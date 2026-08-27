# Recovery

Two distinct mechanisms. Knowing which applies to which failure is the point.

| Failure | Mechanism | Trigger | Verified |
| ------- | --------- | ------- | -------- |
| Pod crashes, hangs, or is killed | Self-healing | Automatic | 2026-08-27, `student04` |
| Bad image deployed | Rollback | `kubectl rollout undo` | CI on every push; see [rollback-runbook.md](rollback-runbook.md) |

No application code implements either. Both come from the Deployment's probes and
the replica controller, which is why they keep working as the app changes.

## Self-healing

`k8s/deployment.yaml` declares three probes:

| Probe | Endpoint | Effect |
| ----- | -------- | ------ |
| `startupProbe` | `/actuator/health/readiness` | Holds liveness off until the app has booted |
| `readinessProbe` | `/actuator/health/readiness` | Unready pods are removed from the Service, so traffic never reaches them |
| `livenessProbe` | `/actuator/health/liveness` | A hung or dead container is killed and restarted |

The controller keeps the declared replica count. Delete a pod and a replacement is
scheduled immediately, with no operator action.

State survives because it is not in the pod: customers and interactions live in
PostgreSQL, so a restarted pod reads the same rows back.

### Rehearsal, 2026-08-27, namespace `student04`

    kubectl -n student04 delete pod -l app=crm-api
    kubectl -n student04 get pods -w

| Step | Observed |
| ---- | -------- |
| Pod deleted | 14:49:21 |
| Replacement scheduled | +1s, unprompted |
| Rollout complete, readiness 200 | 14:50:42 |
| **Downtime** | **81s** |
| `lab-request-001` after recovery | present, same `INT-1c5f6b04...` |
| Customers after recovery | all 3 returned |

An unplanned instance of the same behaviour was recorded earlier the same day: the
first pod of the deploy booted against an unpatched ConfigMap, failed Flyway with
`password authentication failed for user "studentNN"`, exited 1, and was restarted
into a healthy state once the patch landed. Recovery was automatic in both cases.

## Rollback

Self-healing does not fix a bad image: the new pod never passes readiness, so the
rollout stalls and the previous pod keeps serving. That is contained, not resolved.
Resolution is deliberate:

    kubectl -n <ns> rollout undo deploy/crm-api

Proven on every push by the CI `cluster` job, which deploys a deliberately broken
image, confirms the rollout does not converge, and restores the pinned digest.
Procedure, triggers, and known-good digest: [rollback-runbook.md](rollback-runbook.md).

## Residual risks

| Risk | Impact | Owner | Due |
| ---- | ------ | ----- | --- |
| `replicas: 1` means self-healing costs ~81s of downtime | No HA; a restart is a brief outage | Delivery | Post-capstone |
| No auto-rollback on a failed deploy | A bad image needs a human to run `rollout undo` | Delivery | Accepted for capstone scope |
| Recovery rehearsed in one namespace only | Behaviour assumed uniform across the cohort cluster | Delivery | Accepted |

Raising `replicas` and relying on the existing rolling-update strategy removes the
downtime; it is deferred, not overlooked.
