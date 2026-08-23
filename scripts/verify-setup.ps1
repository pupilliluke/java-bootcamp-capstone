# Verifies a developer's local setup end to end and prints a pass/fail table.
#
#   cd backend; .\mvnw spring-boot:run      # in one terminal
#   .\scripts\verify-setup.ps1              # in another
#
# Reports which database the running application is actually connected to, so a
# teammate can confirm they are on the local container or on Azure rather than
# assuming from what .env says.

$ErrorActionPreference = 'Continue'
$api = 'http://localhost:8080'
$results = @()

function Check($name, $detail, $ok) {
    $script:results += [pscustomobject]@{
        Check  = $name
        Result = $(if ($ok) { 'PASS' } else { 'FAIL' })
        Detail = $detail
    }
}

# --- 1. .env exists and carries a usable secret ---------------------------
$envPath = Join-Path $PSScriptRoot '..\.env'
if (Test-Path $envPath) {
    $secret = (Select-String -Path $envPath -Pattern '^JWT_SECRET=(.*)$').Matches.Groups[1].Value
    Check '.env present' $envPath $true
    Check 'JWT_SECRET length >= 32' "$($secret.Length) characters" ($secret.Length -ge 32)

    $dbUrl = (Select-String -Path $envPath -Pattern '^DB_URL=(.*)$').Matches.Groups[1].Value
    $mode  = if ($dbUrl) { 'Azure (DB_URL set in .env)' } else { 'local container (DB_URL not set)' }
    Check 'database mode configured' $mode $true
} else {
    Check '.env present' 'missing - copy .env.example to .env' $false
}

# --- 2. Local Postgres container -----------------------------------------
$pg = docker compose ps --format '{{.Service}} {{.Status}}' postgres 2>$null
Check 'local postgres container' $(if ($pg) { $pg } else { 'not running - docker compose up -d' }) ([bool]$pg)

# --- 2b. Azure reachability, independent of the application ---------------
# Tested directly rather than through the app on purpose. A missing firewall
# rule stops the application from starting at all, which would otherwise show up
# further down as "application responding: FAIL" and read like a broken build
# instead of a networking problem.
function Test-Tcp($hostname, $port, $timeoutMs = 5000) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $connect = $client.BeginConnect($hostname, $port, $null, $null)
        if (-not $connect.AsyncWaitHandle.WaitOne($timeoutMs)) { return $false }
        $client.EndConnect($connect)
        return $true
    } catch { return $false } finally { $client.Close() }
}

if ($dbUrl -and $dbUrl -match '//([^:/]+)') {
    $azureHost = $Matches[1]
    $reachable = Test-Tcp $azureHost 5432
    Check 'azure host reachable' $(if ($reachable) {
        "$azureHost accepts connections on 5432"
    } else {
        "$azureHost did not answer - your public IP is probably not in the Azure firewall rules"
    }) $reachable

    Check 'azure URL requires TLS' $(if ($dbUrl -match 'sslmode=require') {
        'sslmode=require present'
    } else {
        'sslmode=require missing - Azure will refuse the connection'
    }) ($dbUrl -match 'sslmode=require')
} else {
    Check 'azure host reachable' 'skipped - DB_URL not set, using the local container' $true
}

# --- 3. Application is up -------------------------------------------------
try {
    $health = Invoke-RestMethod "$api/actuator/health" -TimeoutSec 5
    Check 'application responding' "status = $($health.status)" ($health.status -eq 'UP')
} catch {
    Check 'application responding' 'no response on :8080 - start the backend first' $false
}

# --- 4. Login works, which proves the database is readable ----------------
$token = $null
try {
    $login = Invoke-RestMethod "$api/api/auth/login" -Method Post -ContentType 'application/json' `
        -Body '{"username":"agent1","password":"agent1"}' -TimeoutSec 10
    $token = $login.accessToken
    Check 'login as agent1' "role = $($login.role)" ($login.role -eq 'AGENT')
} catch {
    Check 'login as agent1' 'failed - schema or seed data missing' $false
}

# --- 5. Which database is it really talking to? ---------------------------
if ($token) {
    $auth = @{ Authorization = "Bearer $token" }
    try {
        $detail = Invoke-RestMethod "$api/actuator/health" -Headers $auth -TimeoutSec 5
        $db = $detail.components.db
        Check 'datasource reachable' "$($db.status), $($db.details.database)" ($db.status -eq 'UP')
    } catch {
        Check 'datasource reachable' 'health details unavailable' $false
    }

    try {
        Invoke-RestMethod "$api/api/customers" -Headers $auth -TimeoutSec 5 | Out-Null
        Check 'authenticated read' '/api/customers returned 200' $true
    } catch {
        Check 'authenticated read' "failed: $($_.Exception.Message)" $false
    }
}

# --- 6. The negative case: anonymous access must be refused --------------
try {
    Invoke-RestMethod "$api/api/customers" -TimeoutSec 5 | Out-Null
    Check 'anonymous request refused' 'returned 200 - security is NOT working' $false
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    # No response object at all means the app never answered, which is a
    # different problem from security failing. Say so rather than printing a
    # blank status code.
    if (-not $code) {
        Check 'anonymous request refused' 'inconclusive - the application is not running' $false
    } else {
        Check 'anonymous request refused' "HTTP $code" ($code -eq 401)
    }
}

# --- Report ---------------------------------------------------------------
''
$results | Format-Table -AutoSize
$failed = @($results | Where-Object Result -eq 'FAIL').Count
if ($failed -eq 0) {
    Write-Host 'Setup verified.' -ForegroundColor Green
} else {
    Write-Host "$failed check(s) failed - see the README troubleshooting table." -ForegroundColor Red
    exit 1
}
