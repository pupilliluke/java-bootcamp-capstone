# Security and deploy demo

Commands to reproduce the release path, and what each one printed on
2026-08-25. Full run output is in `docs/k8s-ci-evidence.txt`.

## Identity of the build

| Field | Value |
| ----- | ----- |
| Tag | `crm-api:dev` |
| Digest | `sha256:ebfe1486a16d77a6dedc0c5f8ed92eec56618f55fd1cdf53d789667d016e69b2` |
| Commit | `5f1bb6d8013f204db7fd5965d6604352ba5fd889` |
| Cluster | `k3d-crm-local`, k3s v1.35.5+k3s1 |
| kubectl | v1.35.5 |

Recorded before the deploy, not after something breaks.

```
docker image inspect crm-api:dev --format '{{.Id}}'
docker image inspect crm-api:dev --format '{{index .Config.Labels "org.opencontainers.image.revision"}}'
```

`.Id` rather than `.RepoDigests` because nothing is pushed to a registry yet.

## Build

```
docker build --build-arg GIT_SHA=$(git rev-parse HEAD) -t crm-api:dev backend
```

## Cluster

```
k3d cluster create crm-local --agents 0 \
  -p "8088:80@loadbalancer" \
  --api-port 127.0.0.1:6445 \
  --image rancher/k3s:v1.35.5-k3s1

k3d image import crm-api:dev -c crm-local
k3d image import postgres:17 -c crm-local
docker exec k3d-crm-local-server-0 crictl images | grep crm-api
```

Import one image at a time. `k3d image import a b -c cluster` reports success
and imports neither. The `crictl` line is the check that the import happened.

## Deploy and smoke

```
bash k8s/smoke.sh
```

164 seconds, twelve assertions, exit 0:

```
  PASS  PostgreSQL is up
  PASS  Secret created without touching a file
  PASS  Deployment rolled out
  PASS  readiness 200
  PASS  login issued a token
  PASS  authenticated read 200
  PASS  anonymous read refused with 401
  PASS  the bad rollout did not converge
  PASS  the ingress kept serving 200 throughout
  PASS  image restored to crm-api:dev
  PASS  readiness 200 after rollback
  PASS  authorisation still enforced after rollback
```

Where the time goes: 8s for PostgreSQL with images preloaded, 90s for the
rollout, 3s for the four HTTP assertions, 60s waiting out the deliberate break,
about 1s for the rollback.

## The deliberate fault

```
kubectl -n crm set image deploy/crm-api crm-api=crm-api:does-not-exist
```

Both pods, during the bad rollout:

```
crm-api-846b58685d-8b6m8    1/1   Running        ← kept serving
crm-api-857f6dd996-hh8wx    0/1   ErrImagePull   ← never served
```

The ingress returned 200 throughout. A rolling update does not take a healthy
pod down for an unhealthy replacement, so no request reached the broken version.

## Recovery

```
kubectl -n crm rollout undo deploy/crm-api
kubectl -n crm rollout status deploy/crm-api
```

Image back to `crm-api:dev`, readiness 200, anonymous read still 401.

## Manual smoke, when the API is reachable

```
curl -s -o /dev/null -w "%{http_code}\n" -H "Host: crm-api.localtest.me" \
  http://localhost:8088/actuator/health/readiness

curl -s -X POST http://localhost:8088/api/v1/auth/login \
  -H "Host: crm-api.localtest.me" -H "Content-Type: application/json" \
  -d '{"username":"admin1","password":"admin1"}'

curl -s -o /dev/null -w "%{http_code}\n" -H "Host: crm-api.localtest.me" \
  -H "Authorization: Bearer <token>" http://localhost:8088/api/v1/customers
```

Expect 200, 200 with a token, 200. Without the token, 401. Use `admin1`;
`agent1`'s password was changed during manual testing.

Redact tokens in screenshots.

## What this does not prove

A version-to-version rollback. The two revisions are one working image and one
that does not exist, so nothing was ever served by the bad version. Proving that
needs an image pushed per commit.
