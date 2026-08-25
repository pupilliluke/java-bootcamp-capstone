#!/usr/bin/env bash
#
# Deploys the manifests to a cluster, proves the application serves, breaks the
# deployment on purpose, and proves it recovers.
#
# A script rather than steps inlined in ci.yml, for the same reason
# container-structure-test.yaml is a file: it has to run identically on a laptop
# and on a runner, or the CI result and the rehearsal in
# docs/rollback-runbook.md are evidence about two different things.
#
#   ./k8s/smoke.sh                        # against the current kubectl context
#   INGRESS=localhost:8088 ./k8s/smoke.sh
#
# Every assertion fails the script. There is no path through this that reports
# success without having checked something -- which is the failure mode the
# manifests job had before its file-count guard, and worth not repeating.
set -euo pipefail

NS="${NS:-crm}"
# Empty by default, meaning "whatever deployment.yaml declares". Set it only to
# deliberately test a different image. An earlier version of this script defaulted
# to crm-api:dev and ran `kubectl set image` unconditionally, which defeated the
# point: a typo'd tag committed in the manifest was silently overwritten with a
# known-good one and the run passed. The tag in the manifest is under test.
IMAGE="${IMAGE:-}"
INGRESS="${INGRESS:-localhost:8088}"
HOST_HEADER="${HOST_HEADER:-crm-api.localtest.me}"
# Overridable so the mutation tests in docs/rollback-runbook.md can prove a
# broken manifest fails without waiting the full five minutes each time.
ROLLOUT_TIMEOUT="${ROLLOUT_TIMEOUT:-300s}"

# Run from the repository root and address the manifests relatively. An absolute
# path breaks under Git Bash, where kubectl is a Windows binary that cannot read
# an MSYS path like /c/Users/... -- and CI would never have caught that, because
# the runner is Linux.
cd "$(dirname "${BASH_SOURCE[0]}")/.."

BODY="$(mktemp)"
trap 'rm -f "$BODY"' EXIT

pass() { echo "  PASS  $*"; }
fail() { echo "  FAIL  $*" >&2; exit 1; }
step() { echo; echo "== $* =="; }

# Returns the status code on stdout rather than failing, so a caller can assert
# on a 401 as readily as a 200. The assertion belongs at the call site, where the
# expected value sits next to the reason for it.
#
# The body goes to a real temp file, not /dev/null. Under Git Bash curl exits 23
# ("write error") writing to /dev/null having already printed the status, so an
# `|| echo 000` fallback appends a second value and the caller reads "200000".
# That would have passed on the Linux runner and failed only on a laptop.
get_status() {
  local path="$1" code=""
  shift
  code="$(curl -s -o "$BODY" -w '%{http_code}' --max-time 15 \
    -H "Host: ${HOST_HEADER}" "$@" "http://${INGRESS}${path}" 2>/dev/null)" || true
  echo "${code:-000}"
}

# Polls until the ingress answers, because a Service that has just gained an
# endpoint is not the same as Traefik having noticed. Sampling once here would
# make the whole script flaky for a reason that has nothing to do with the code.
wait_for_ingress() {
  local want="$1" path="$2" tries="${3:-40}" code=""
  for _ in $(seq 1 "$tries"); do
    code="$(get_status "$path")"
    if [ "$code" = "$want" ]; then
      echo "$code"
      return 0
    fi
    sleep 3
  done
  echo "$code"
  return 1
}

step "1. Namespace and the test database"
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/test/postgres.yaml
# Generous, because the first run on a cold cluster pulls ~400MB of postgres:17
# before anything can start. Measured: 180s was not enough on a laptop k3d.
# `k3d image import postgres:17 -c <cluster>` makes reruns instant.
kubectl -n "$NS" rollout status deploy/postgres --timeout=420s
pass "PostgreSQL is up"

step "2. Secret, created out of band"
# Never `kubectl apply -f secret.example.yaml`. That file records which keys are
# needed; applying it would deploy its placeholders.
kubectl -n "$NS" create secret generic crm-api-secrets \
  --from-literal=LOCAL_DB_PASSWORD='smoke-test-only' \
  --from-literal=JWT_SECRET='smoke-test-secret-of-at-least-32-characters' \
  --dry-run=client -o yaml | kubectl apply -f -
pass "Secret created without touching a file"

step "3. The real manifests"
kubectl apply \
  -f k8s/configmap.yaml \
  -f k8s/service.yaml \
  -f k8s/deployment.yaml \
  -f k8s/ingress.yaml

# The one deviation from a real deploy, and it is deliberate. The ConfigMap
# points at host.k3d.internal because that is where PostgreSQL lives on a
# developer laptop. In CI there is no such host, so the value is repointed at the
# in-cluster Service. Everything else -- probes, security context, resources, the
# Secret wiring -- is exactly what ships.
kubectl -n "$NS" patch configmap crm-api-config --type merge \
  -p '{"data":{"LOCAL_DB_HOST":"postgres","LOCAL_DB_PORT":"5432"}}'
if [ -n "$IMAGE" ]; then
  kubectl -n "$NS" set image deploy/crm-api "crm-api=${IMAGE}"
fi
kubectl -n "$NS" rollout restart deploy/crm-api
kubectl -n "$NS" rollout status deploy/crm-api --timeout="$ROLLOUT_TIMEOUT"
pass "Deployment rolled out"

step "4. The application serves through the ingress"
code="$(wait_for_ingress 200 /actuator/health/readiness)" ||
  fail "readiness never returned 200, last was $code"
pass "readiness 200"

# Polled for the same reason readiness is. Observed once: a single-shot login
# failed immediately after the rollout, and the identical command succeeded a few
# seconds later with nothing changed. Readiness going UP means this pod is
# serving, but the ingress can still route a request to the outgoing one while
# Traefik rebuilds its router.
#
# Worth fixing rather than re-running, because a check that fails one run in five
# trains everyone to press the button again, and then it is not a check.
token=""
for _ in $(seq 1 20); do
  token="$(curl -s --max-time 15 -H "Host: ${HOST_HEADER}" \
    -H 'Content-Type: application/json' \
    -d '{"username":"admin1","password":"admin1"}' \
    "http://${INGRESS}/api/auth/login" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')" || true
  [ -n "$token" ] && break
  sleep 3
done
[ -n "$token" ] || fail "login never returned a token after 20 attempts"
pass "login issued a token"

code="$(get_status /api/customers -H "Authorization: Bearer ${token}")"
[ "$code" = "200" ] || fail "authenticated read returned $code, expected 200"
pass "authenticated read 200"

code="$(get_status /api/customers)"
[ "$code" = "401" ] || fail "anonymous read returned $code, expected 401"
pass "anonymous read refused with 401"

step "5. Break it on purpose"
before="$(kubectl -n "$NS" get deploy crm-api -o jsonpath='{.spec.template.spec.containers[0].image}')"
kubectl -n "$NS" set image deploy/crm-api crm-api=crm-api:does-not-exist

# The rollout must NOT converge. If it does, the cluster found an image that
# should not exist and this test is not testing what it claims to.
if kubectl -n "$NS" rollout status deploy/crm-api --timeout=60s >/dev/null 2>&1; then
  fail "a deployment of crm-api:does-not-exist converged, which should be impossible"
fi
pass "the bad rollout did not converge"

kubectl -n "$NS" get pods --no-headers | sed 's/^/     /'

# The point of the whole exercise: a rolling update must not take a healthy pod
# down for an unhealthy replacement, so users never see the bad version.
code="$(get_status /actuator/health/readiness)"
[ "$code" = "200" ] || fail "the ingress returned $code during the bad rollout; the outage reached users"
pass "the ingress kept serving 200 throughout"

step "6. Recover through automation"
kubectl -n "$NS" rollout undo deploy/crm-api
kubectl -n "$NS" rollout status deploy/crm-api --timeout="$ROLLOUT_TIMEOUT"

after="$(kubectl -n "$NS" get deploy crm-api -o jsonpath='{.spec.template.spec.containers[0].image}')"
[ "$after" = "$before" ] || fail "after rollback the image is $after, expected $before"
pass "image restored to $after"

code="$(wait_for_ingress 200 /actuator/health/readiness)" ||
  fail "readiness did not recover after the rollback, last was $code"
pass "readiness 200 after rollback"

code="$(get_status /api/customers)"
[ "$code" = "401" ] || fail "anonymous read returned $code after rollback, expected 401"
pass "authorisation still enforced after rollback"

echo
echo "== all checks passed =="
