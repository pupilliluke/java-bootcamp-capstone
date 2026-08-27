# Looking at the Azure database

How to connect and read the data. For admin problems (firewall, connection
exhaustion, grants) see `docs/azure-admin-runbook.md`.

The password is in the gitignored `.env` as `AZURE_DB_PASSWORD`. Never type or
paste it — the commands below read it from the file.

## Git Bash

```
cd ~/java-bootcamp-capstone
PW=$(grep -m1 '^AZURE_DB_PASSWORD=' .env | cut -d= -f2- | tr -d '\r\n')
PGPASSWORD="$PW" psql -w "host=java-bootcamp-capstone.postgres.database.azure.com port=5432 dbname=crm user=JavaBootcampCapstone sslmode=require"
```

## PowerShell

```
cd C:\Users\lukel\java-bootcamp-capstone
$env:PGPASSWORD = ((Get-Content .env | Where-Object { $_ -like 'AZURE_DB_PASSWORD=*' }) -split '=',2)[1]
psql -w "host=java-bootcamp-capstone.postgres.database.azure.com port=5432 dbname=crm user=JavaBootcampCapstone sslmode=require"
Remove-Item Env:PGPASSWORD
```

The two shells are not interchangeable. `VAR=x`, `$(...)` and `./mvnw` are Git
Bash. `$env:VAR = "x"`, `Get-Content` and `mvnw.cmd` are PowerShell. Running one
in the other produces `CommandNotFoundException`.

## Once connected

| Command | Does |
| ------- | ---- |
| `\dt` | list tables |
| `\d customer` | show a table's columns |
| `select * from customer;` | the data, semicolon required |
| `\q` | quit |

Tables: `customer`, `interaction`, `app_user`, `flyway_schema_history`.

Do not select the password column out of `app_user`.

## Things that cost time the first go

**Do not `source .env`.** The password is 128 unquoted characters and the shell
tries to interpret it. Extract the line instead, as above.

**Use `-w`.** Without it psql silently prompts for a password when the variable
did not reach it, and a non-interactive shell hangs until it times out rather
than telling you why.

**A pasted password is invisible.** It goes in, it just does not echo, so a
partial paste looks identical to a correct one and reports `password
authentication failed`.

**The portal query editor does not work.** It reports "Failed to retrieve schema"
and "No tables to display" because the subscription has no `Microsoft.Insights`
resource provider registered. Registering it is a subscription-wide change and is
not needed to read four tables.

## Proving the backend is connected

Portal, server resource, Monitoring, Metrics. Metric **Active Connections**
(`active_connections`), last 4 hours, 5 minute granularity.

Pick the right metric. **`client_connections_active`** is a PgBouncer metric and
is empty because the pooler is not enabled, so charting it gives a flat line at
zero and an empty legend. Azure offers it either way.

Reading the number:

| Source | Connections |
| ------ | ----------- |
| One running backend | **10** — HikariCP holds a pool open whether or not it is busy |
| Azure itself (`azuresu`, `azure_sys`, `azure_maintenance`) | about 8, always there |
| PostgreSQL background workers | about 8 |
| One psql session | 1 |

So the baseline is never zero, and starting one backend steps the line up by ten.
That step is the proof the application is talking to Azure; the absolute number is
not, because the server is shared.

To see exactly who is connected:

```
select coalesce(usename,'[system]') as who,
       coalesce(nullif(application_name,''),'-') as app,
       datname, state, count(*)
from pg_stat_activity group by 1,2,3,4 order by 5 desc;
```

`PostgreSQL JDBC Driver` rows are backends. `azuresu` rows are Azure's own.

Four teammates each running a backend is forty connections before anyone does any
work, which is how the tier's cap gets hit. Stop a backend you are not using, or
cap the pool with `spring.datasource.hikari.maximum-pool-size`.
