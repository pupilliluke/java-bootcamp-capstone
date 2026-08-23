# Azure database admin runbook

For whoever owns the Azure server when a teammate reports "it will not connect".
Every symptom below is distinguishable from the others, so start by asking which
one they actually see rather than guessing.

Server: `java-bootcamp-capstone.postgres.database.azure.com`, Azure Database for
PostgreSQL Flexible Server, PostgreSQL 18, PostgreSQL authentication.

## Ask for the exact symptom first

Have them run `.\scripts\verify-setup.ps1` and send the table. The first four
rows resolve most cases without any Azure access at all.

## Symptom table

| What they see | Cause | Admin fix |
| ------------- | ----- | --------- |
| Connection hangs, then times out. `azure host reachable` fails. | Their public IP is not in the firewall rules. Azure drops the packet rather than refusing it, which is why it hangs instead of erroring. | Add a firewall rule for their IP. |
| `no pg_hba.conf entry for host` | Public network access is disabled on the server, or the server is private-endpoint only. | Networking → enable public access. |
| `password authentication failed for user` | Wrong password, or characters came along with the paste. | Have them re-check `.env` first. Reset the admin password only if that is ruled out. |
| `FATAL: database "crm" does not exist` | They are pointed at a server where the database was never created. | `CREATE DATABASE crm;` |
| `permission denied for database crm` | Their login is not the admin and has no grant. | Grant connect, or have them use the admin login. |
| `SSL connection is required` | `sslmode=require` missing from their `DB_URL`. | Client-side fix, not an Azure one. |
| `sorry, too many clients already` | The tier's connection cap is exhausted. Each running backend holds a Hikari pool of up to 10. | See "Connection exhaustion" below. |
| `FATAL: password authentication failed` for everyone at once | Server may have been switched to Microsoft Entra authentication only. | Security → Authentication → include PostgreSQL authentication. |

## Current firewall state

One rule, `AllowAll_2026-8-23_18-25-10`, spanning `0.0.0.0` to `255.255.255.255`.

**While that rule exists no teammate needs anything added** — anyone can connect
from any address, so the "connection hangs" row above cannot occur. The rest of
this section applies only if the rule is narrowed.

The trade, stated plainly so it can be decided rather than drifted into:

| | Allow-all | Per-IP rules |
| ---- | --------- | ------------ |
| Onboarding a teammate | nothing to do | one rule each, redone when home IPs rotate |
| Reachable from | the entire internet | four addresses |
| Standing between the internet and the data | the admin password and TLS | network plus the password |

The password is 128 characters, so brute force is not a realistic threat. The
residual risks are a leaked credential, connection floods, and whatever the next
gateway CVE turns out to be — all reachable by anyone, not just the four of you.

The rubric permits either choice. What it does not permit is leaving it
undecided: §8.4 rule 6 makes a security finding a hard gate "unless residual risk
is explicitly accepted with owner and date". Keeping allow-all is defensible for
a training environment holding synthetic data — write it in the risk register
with a name and a date and it is an accepted risk rather than a finding.

## Getting someone's IP

Only needed if the allow-all rule is removed. They run this and send the result:

```powershell
(Invoke-RestMethod https://api.ipify.org?format=json).ip
```

Home connections rotate addresses, so expect to redo this occasionally. That
friction is the reason people leave an allow-all rule in place; add the rule
instead.

## Firewall rules

List what exists:

```powershell
az postgres flexible-server firewall-rule list --resource-group <rg> --name java-bootcamp-capstone --output table
```

Add one person:

```powershell
az postgres flexible-server firewall-rule create --resource-group <rg> --name java-bootcamp-capstone --rule-name himank --start-ip-address <ip> --end-ip-address <ip>
```

Delete the allow-all rule once real rules exist:

```powershell
az postgres flexible-server firewall-rule delete --resource-group <rg> --name java-bootcamp-capstone --rule-name AllowAll_2026-8-23_18-25-10
```

A rule spanning `0.0.0.0` to `255.255.255.255` exposes the server to the whole
internet. It is a reasonable five-minute debugging step and a bad standing
configuration: the capstone rubric treats an open security finding as a hard
gate unless the residual risk is written down with an owner and a date.

## Connection exhaustion

Each running backend opens a Hikari pool of up to 10 connections, and every
teammate pointing at Azure runs their own. Burstable tiers cap total connections
low enough that four developers can exhaust them.

Check who is connected:

```sql
SELECT usename, client_addr, state, count(*)
FROM pg_stat_activity
GROUP BY usename, client_addr, state
ORDER BY count DESC;
```

Check the ceiling and current usage:

```sql
SHOW max_connections;
SELECT count(*) FROM pg_stat_activity;
```

Two fixes: raise the tier, or cap the pool per developer by adding
`spring.datasource.hikari.maximum-pool-size=3` to their `.env`. Prefer the pool
cap — it costs nothing.

## Verifying a fix without the application

Fastest end-to-end check, run as the admin:

```powershell
psql "host=java-bootcamp-capstone.postgres.database.azure.com port=5432 dbname=crm user=JavaBootcampCapstone sslmode=require" -c "SELECT current_database(), current_user, version();"
```

If that works and their application still does not, the problem is on their side
— almost always `.env`, not Azure.

## What Azure never explains well

- **A missing firewall rule looks like a hang, not a denial.** Azure drops the
  packet silently. Anyone waiting for an error message waits forever.
- **Entra and PostgreSQL authentication are separate modes.** With Entra only,
  the "password" is a token from `az account get-access-token` that expires in
  about an hour, so a static `DB_PASSWORD` appears to work and then stops.
- **The admin username has no `@servername` suffix** on Flexible Server. That
  suffix belonged to the retired Single Server product and is a common
  copy-paste error from older documentation.
