# Azure authentication

Two different things are called "Azure auth" on this project and they are
unrelated. Knowing which one is failing saves most of the debugging time.

| | What it authenticates | Credential | Expires |
| ---- | --------------------- | ---------- | ------- |
| **Database login** | the application connecting to PostgreSQL | `DB_USER` and `DB_PASSWORD` in `.env` | never, until rotated |
| **Azure CLI login** | *you*, running `az` commands against the subscription | a refresh token from `az login` | after inactivity |

The application never uses the Azure CLI. Someone can be completely logged out
of `az` and the backend still connects to the database perfectly well.

## What this project uses

**PostgreSQL authentication**, deliberately chosen over Microsoft Entra.

- Server: `java-bootcamp-capstone.postgres.database.azure.com`
- Admin login: `JavaBootcampCapstone` — no `@servername` suffix; that belonged to
  the retired Single Server product and is a common copy-paste error
- TLS 1.2 enforced, so `sslmode=require` is mandatory in the JDBC URL
- PostgreSQL 18

The application reads all three values from the gitignored `.env`. Nothing in the
committed configuration names the server, and the same build runs against the
local container by simply leaving those keys unset.

### Why not Entra

Azure offered Entra authentication, where `PGUSER` is your Entra identity and
`PGPASSWORD` is a token from:

```powershell
az account get-access-token --resource https://ossrdbms-aad.database.windows.net --query accessToken --output tsv
```

That token expires in roughly an hour. A static `DB_PASSWORD` holding one would
appear to work and then start failing mid-session as Hikari opened new
connections. Making it work properly needs a custom `DataSource` that fetches a
fresh token per connection through `DefaultAzureCredential`, plus every teammate
running `az login` and being granted access individually.

It is the better security posture — no database password would exist anywhere —
and it is worth revisiting if the project is ever deployed for real, because a
managed identity removes the credential entirely. It was not worth the detour
against gaps that are actually scored. `azure-identity` 1.13.2 is already in the
local Maven repository if that changes.

## Azure CLI login

Only needed for administering the server: firewall rules, stop/start, checking
the tier. Not needed to run the application.

```powershell
az login
```

The refresh token expires after a period of inactivity, and the failure is
explicit:

```text
AADSTS700082: The refresh token has expired due to inactivity.
Please explicitly log in with: az login --scope https://management.core.windows.net//.default
```

That message means your CLI session lapsed. It says nothing about the database,
and the application is unaffected.

## Rotating the database password

Portal → the server → Overview → **Reset password**. Then every teammate updates
`DB_PASSWORD` in their own `.env`; there is no shared store to update.

Rotate if the value is ever pasted into a chat, a commit, a screen share, or a
support ticket. Distribute the new one through a password manager, never a chat
channel.

## Checking which mode the server is in

Portal → the server → Security → **Authentication**. Three settings exist:
PostgreSQL only, Microsoft Entra only, or both. If it ever reads "Microsoft Entra
authentication only", password logins stop working for everyone at once — that is
the cause to check when four people fail simultaneously with
`password authentication failed`.

## Things that look like auth failures but are not

| Symptom | Actually |
| ------- | -------- |
| Connection hangs, then times out | Firewall. Azure drops the packet instead of refusing, so there is no error to read. |
| `SSL connection is required` | `sslmode=require` missing from `DB_URL`. |
| `FATAL: database "crm" does not exist` | Connected fine, authenticated fine, wrong database name. |
| `sorry, too many clients already` | Authenticated fine; the tier's connection cap is exhausted. |

See [azure-admin-runbook.md](azure-admin-runbook.md) for the fixes.
