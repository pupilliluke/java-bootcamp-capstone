#!/usr/bin/env bash
#
# Deploys crm-api into YOUR namespace on the course cluster, taking every
# per-student value from .env.cluster at the repository root (template:
# k8s/cluster.env.example). The manifests stay team-generic; this script is
# the injection point, so four students deploy four namespaces from one
# repository with no tracked file carrying anybody's identity.
#
# Safe to re-run: the Secret is created only if absent (so a rotation is a
# deliberate delete-and-rerun, never an accidental overwrite of a working
# credential), and everything else is apply/patch, which no-ops on no change.
#
# Run it as `bash k8s/cluster-deploy.sh` — authored on Windows, where git
# does not reliably carry the executable bit.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

if [ ! -f .env.cluster ]; then
  echo "No .env.cluster at the repository root." >&2
  echo "Copy k8s/cluster.env.example there and fill your row of the credentials sheet." >&2
  exit 1
fi

set -a
. ./.env.cluster
set +a

: "${STUDENT_NS:?set STUDENT_NS in .env.cluster}"
: "${CLUSTER_HOST:?set CLUSTER_HOST in .env.cluster}"
: "${KUBECONFIG_PATH:?set KUBECONFIG_PATH in .env.cluster}"
: "${LOCAL_DB_PASSWORD:?set LOCAL_DB_PASSWORD in .env.cluster}"
: "${JWT_SECRET:?set JWT_SECRET in .env.cluster}"
: "${INGRESS_HOST:?set INGRESS_HOST in .env.cluster}"
: "${IMAGE:?set IMAGE in .env.cluster}"

# A path that does not resolve makes kubectl fall back to localhost:8080,
# which then fails looking like a network problem. Catch it here instead --
# and use forward slashes in .env.cluster: backslashes do not survive
# sourcing (C:\Users sources as C:Users).
if [ ! -f "$KUBECONFIG_PATH" ]; then
  echo "KUBECONFIG_PATH does not exist: $KUBECONFIG_PATH" >&2
  echo "Check .env.cluster -- use forward slashes (C:/Users/...)." >&2
  exit 1
fi
export KUBECONFIG="$KUBECONFIG_PATH"

echo "== deploying $IMAGE"
echo "== into $STUDENT_NS at https://$CLUSTER_HOST:6443, ingress $INGRESS_HOST"

# Never applied here: namespace.yaml (provided, and not ours to create) and
# test/postgres.yaml (PostgreSQL is provided; that file is a k3d fixture).

if kubectl -n "$STUDENT_NS" get secret crm-api-secrets >/dev/null 2>&1; then
  echo "   secret crm-api-secrets exists -- left untouched (delete it to rotate)"
else
  kubectl -n "$STUDENT_NS" create secret generic crm-api-secrets \
    --from-literal=LOCAL_DB_PASSWORD="$LOCAL_DB_PASSWORD" \
    --from-literal=JWT_SECRET="$JWT_SECRET"
fi

kubectl -n "$STUDENT_NS" apply \
  -f k8s/configmap.yaml \
  -f k8s/service.yaml \
  -f k8s/deployment.yaml \
  -f k8s/ingress.yaml

# The per-student values the manifest comments promise get patched in.
# DB user, schema, topic and group all share the namespace's name -- the
# credentials sheet uses one identifier for all of them.
kubectl -n "$STUDENT_NS" patch configmap crm-api-config --type merge \
  -p "{\"data\":{\"LOCAL_DB_USER\":\"$STUDENT_NS\",\"LOCAL_DB_URL_OPTIONS\":\"?currentSchema=$STUDENT_NS\",\"CRM_INTERACTION_TOPIC\":\"$STUDENT_NS.crm.interaction.v1\",\"CRM_CONSUMER_GROUP\":\"$STUDENT_NS.crm-interaction-service-v1\"}}"

kubectl -n "$STUDENT_NS" patch ingress crm-api --type merge \
  -p "{\"spec\":{\"rules\":[{\"host\":\"$INGRESS_HOST\",\"http\":{\"paths\":[{\"path\":\"/\",\"pathType\":\"Prefix\",\"backend\":{\"service\":{\"name\":\"crm-api\",\"port\":{\"number\":80}}}}]}}]}}"

# The manifest's crm-api:dev is a local tag the cluster cannot pull; the
# deployable identity is the pushed digest from .env.cluster.
kubectl -n "$STUDENT_NS" set image deploy/crm-api "crm-api=$IMAGE"

kubectl -n "$STUDENT_NS" rollout status deploy/crm-api --timeout=300s

# Proven from outside or not proven at all: through the ingress, not the
# API. Polled, not sampled once -- the apply/patch pair above flips the
# ingress host twice in two seconds and Traefik rebuilds its router
# asynchronously, so the first request after a deploy can 404 against a
# route that is seconds from existing. Same lesson smoke.sh already encodes.
code=""
for _ in $(seq 1 20); do
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 \
    "http://$INGRESS_HOST/actuator/health/readiness")" || true
  [ "$code" = "200" ] && break
  sleep 3
done
if [ "$code" = "200" ]; then
  echo "== readiness 200 through http://$INGRESS_HOST -- deployed and serving"
else
  echo "== deployment converged but the ingress kept answering '$code' -- check" >&2
  echo "   kubectl -n $STUDENT_NS get ingress,endpoints" >&2
  exit 1
fi
