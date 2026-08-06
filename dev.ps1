<#
.SYNOPSIS
    Start, stop, and inspect the Sniplink dev stack.

.DESCRIPTION
    Checks the environment before launching anything, then opens the API and the
    UI in their own windows.

    Run this through dev.cmd (or dev.sh) rather than directly: a default Windows
    PowerShell profile is Restricted and will refuse to run this file, while
    dev.cmd invokes it with -ExecutionPolicy Bypass, which needs no admin rights
    and changes nothing on the machine.

.EXAMPLE
    .\dev.cmd            # start
    .\dev.cmd stop       # shut everything down
    .\dev.cmd status     # report only, change nothing
#>
[CmdletBinding()]
param(
    [ValidateSet('start', 'stop', 'status', 'help')]
    [string]$Action = 'start'
)

$ErrorActionPreference = 'Stop'

# --------------------------------------------------------------------------
# Configuration
# --------------------------------------------------------------------------

$RepoRoot  = $PSScriptRoot
$ApiDir    = Join-Path $RepoRoot 'sniplink-api'
$UiDir     = Join-Path $RepoRoot 'sniplink-ui'
$ApiPort   = 8080   # matches server.port in application.yml
$UiPort    = 5173   # matches server.port in vite.config.ts
$StateFile = Join-Path $env:TEMP 'sniplink-dev.json'

$DbName = 'sniplink'
$DbUser = 'sniplink'
$DbPass = 'sniplink'

# --------------------------------------------------------------------------
# Output helpers
# --------------------------------------------------------------------------

function Write-Check($label, $ok, $detail) {
    $mark  = if ($ok) { 'ok  ' } else { 'FAIL' }
    $color = if ($ok) { 'Green' } else { 'Red' }
    Write-Host '  [' -NoNewline
    Write-Host $mark -NoNewline -ForegroundColor $color
    Write-Host '] ' -NoNewline
    Write-Host $label -NoNewline
    if ($detail) { Write-Host "  $detail" -ForegroundColor DarkGray } else { Write-Host '' }
}

function Write-Fix($lines) {
    Write-Host ''
    foreach ($line in $lines) { Write-Host "  $line" -ForegroundColor Yellow }
    Write-Host ''
}

function Write-Header($text) {
    Write-Host ''
    Write-Host "  $text" -ForegroundColor Cyan
    Write-Host ''
}

# --------------------------------------------------------------------------
# Environment probes
# --------------------------------------------------------------------------

function Test-Port([int]$port) {
    # Loopback services may bind IPv4, IPv6, or both; either counts as up.
    $conns = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue
    return [bool]$conns
}

function Get-PortOwner([int]$port) {
    $conn = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue |
            Select-Object -First 1
    if (-not $conn) { return $null }
    return Get-Process -Id $conn.OwningProcess -ErrorAction SilentlyContinue
}

<#
    Locate a JDK 21. Globbed rather than hardcoded so a JDK upgrade does not
    silently break the launcher. SNIPLINK_JAVA_HOME wins if set.
#>
function Resolve-JavaHome {
    if ($env:SNIPLINK_JAVA_HOME) { return $env:SNIPLINK_JAVA_HOME }
    $candidates = Get-ChildItem (Join-Path $env:USERPROFILE '.jdks') -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue |
                  Sort-Object Name -Descending
    if ($candidates) { return $candidates[0].FullName }
    return $null
}

function Get-JavaVersion($javaHome) {
    $exe = Join-Path $javaHome 'bin\java.exe'
    if (-not (Test-Path $exe)) { return $null }
    # `--version` (not `-version`) writes to stdout. Redirecting a native exe's
    # stderr in Windows PowerShell wraps each line in a NativeCommandError,
    # which $ErrorActionPreference='Stop' would turn into a terminating error.
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $out = (& $exe --version) -join ' '
    }
    catch { return $null }
    finally { $ErrorActionPreference = $prev }

    $m = [regex]::Match($out, '\b(\d+)\.\d+\.\d+')
    if ($m.Success) { return [int]$m.Groups[1].Value }
    return $null
}

function Find-Psql {
    Get-ChildItem 'C:\Program Files\PostgreSQL\*\bin\psql.exe' -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}

<#
    Returns 'ok', 'missing', or 'unknown' (when psql is unavailable to ask).
    Read-only by design: this script never creates roles or databases.
#>
function Test-Database {
    $psql = Find-Psql
    if (-not $psql) { return 'unknown' }
    $prev = $env:PGPASSWORD
    $env:PGPASSWORD = $DbPass
    try {
        & $psql -U $DbUser -d $DbName -h localhost -p 5432 -tAc 'select 1' *> $null
        if ($LASTEXITCODE -eq 0) { return 'ok' } else { return 'missing' }
    }
    catch { return 'missing' }
    finally { $env:PGPASSWORD = $prev }
}

function Show-DatabaseFix {
    $psql = Find-Psql
    if (-not $psql) { $psql = 'psql' }
    Write-Fix @(
        'The sniplink database or role does not exist yet. Create it with:',
        '',
        "  & `"$psql`" -U postgres -h localhost -c `"CREATE ROLE $DbUser LOGIN PASSWORD '$DbPass';`"",
        "  & `"$psql`" -U postgres -h localhost -c `"CREATE DATABASE $DbName OWNER $DbUser;`"",
        '',
        'You will be prompted for your Postgres superuser password.',
        'Owning the database is what lets Hibernate create its tables on first boot.'
    )
}

# --------------------------------------------------------------------------
# State file (PIDs of the windows we opened)
# --------------------------------------------------------------------------

function Save-State($apiPid, $uiPid) {
    [pscustomobject]@{
        apiPid    = $apiPid
        uiPid     = $uiPid
        startedAt = (Get-Date).ToString('s')
    } | ConvertTo-Json | Set-Content -Path $StateFile -Encoding utf8
}

function Get-State {
    if (-not (Test-Path $StateFile)) { return $null }
    try { Get-Content $StateFile -Raw | ConvertFrom-Json } catch { $null }
}

function Clear-State {
    Remove-Item $StateFile -Force -ErrorAction SilentlyContinue
}

<#
    /T is essential: mvnw.cmd spawns java.exe as a CHILD of the shell, so
    killing the shell alone would leave the API holding port 8080.
#>
function Stop-Tree([int]$processId) {
    if (-not (Get-Process -Id $processId -ErrorAction SilentlyContinue)) { return $false }
    & taskkill /PID $processId /T /F *> $null
    return $true
}

# --------------------------------------------------------------------------
# Actions
# --------------------------------------------------------------------------

function Invoke-Status {
    Write-Header 'Sniplink status'

    $javaHome = Resolve-JavaHome
    if ($javaHome) {
        $ver = Get-JavaVersion $javaHome
        Write-Check 'JDK 21' ($ver -eq 21) $javaHome
    }
    else {
        Write-Check 'JDK 21' $false 'not found'
    }

    Write-Check 'PostgreSQL :5432' (Test-Port 5432)
    Write-Check 'Redis :6379'      (Test-Port 6379)

    switch (Test-Database) {
        'ok'      { Write-Check 'sniplink database' $true }
        'missing' { Write-Check 'sniplink database' $false 'role or database missing' }
        'unknown' { Write-Check 'sniplink database' $true 'psql not found, not checked' }
    }

    Write-Host ''
    $apiUp = Test-Port $ApiPort
    $uiUp  = Test-Port $UiPort
    Write-Check "API  :$ApiPort" $apiUp $(if ($apiUp) { 'running' } else { 'not running' })
    Write-Check "UI   :$UiPort"  $uiUp  $(if ($uiUp)  { 'running' } else { 'not running' })
    Write-Host ''
}

function Invoke-Stop {
    Write-Header 'Stopping Sniplink'

    $stopped = 0
    $state = Get-State
    if ($state) {
        foreach ($entry in @(
            @{ Name = 'API window'; Id = $state.apiPid },
            @{ Name = 'UI window';  Id = $state.uiPid }
        )) {
            if ($entry.Id -and (Stop-Tree ([int]$entry.Id))) {
                Write-Host "  stopped $($entry.Name) (pid $($entry.Id))" -ForegroundColor DarkGray
                $stopped++
            }
        }
    }

    # Sweep anything still on our ports, so hand-started servers are cleaned up too.
    Start-Sleep -Milliseconds 400
    foreach ($port in @($ApiPort, $UiPort)) {
        $proc = Get-PortOwner $port
        if ($proc) {
            if (Stop-Tree $proc.Id) {
                Write-Host "  stopped $($proc.ProcessName) holding port $port (pid $($proc.Id))" -ForegroundColor DarkGray
                $stopped++
            }
        }
    }

    Clear-State

    Write-Host ''
    if ($stopped -eq 0) {
        Write-Host '  Nothing was running.' -ForegroundColor Yellow
    }
    else {
        Write-Host "  Stopped $stopped process(es)." -ForegroundColor Green
    }
    Write-Host ''
}

function Invoke-Start {
    Write-Header 'Checking environment'

    # --- JDK ---------------------------------------------------------------
    $javaHome = Resolve-JavaHome
    $javaVer  = if ($javaHome) { Get-JavaVersion $javaHome } else { $null }
    if ($javaVer -ne 21) {
        $detail =
            if (-not $javaHome)  { 'not found' }
            elseif (-not $javaVer) { "no usable java.exe at $javaHome" }
            else                 { "found Java $javaVer at $javaHome" }
        Write-Check 'JDK 21' $false $detail

        $expected = Join-Path $env:USERPROFILE '.jdks\jdk-21*'
        Write-Fix @(
            'JDK 21 is required. The system java on PATH is Java 8 and cannot build this project.',
            '',
            "Expected a portable JDK at: $expected",
            'Download the Windows x64 zip and extract it there:',
            '  https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse',
            '',
            'Or point at an existing install:  $env:SNIPLINK_JAVA_HOME = "C:\path\to\jdk-21"'
        )
        exit 1
    }
    Write-Check 'JDK 21' $true $javaHome

    # --- Backing services --------------------------------------------------
    if (-not (Test-Port 5432)) {
        Write-Check 'PostgreSQL :5432' $false 'not reachable'
        Write-Fix @(
            'PostgreSQL is not listening on 5432. Start the service:',
            '  Start-Service postgresql-x64-18',
            '(or via services.msc if it needs elevation)'
        )
        exit 1
    }
    Write-Check 'PostgreSQL :5432' $true

    if (-not (Test-Port 6379)) {
        Write-Check 'Redis :6379' $false 'not reachable'
        Write-Fix @(
            'Redis is not listening on 6379. Start Memurai:',
            '  Start-Service Memurai'
        )
        exit 1
    }
    Write-Check 'Redis :6379' $true

    # --- Database ----------------------------------------------------------
    switch (Test-Database) {
        'ok' {
            Write-Check 'sniplink database' $true
        }
        'unknown' {
            # psql is a convenience, not a gate — carry on and let the API report.
            Write-Check 'sniplink database' $true 'psql not found, skipped'
        }
        'missing' {
            Write-Check 'sniplink database' $false 'role or database missing'
            Show-DatabaseFix
            Write-Host '  Nothing was started.' -ForegroundColor Yellow
            Write-Host ''
            exit 1
        }
    }

    # --- Ports free --------------------------------------------------------
    $busy = @()
    foreach ($port in @($ApiPort, $UiPort)) {
        $proc = Get-PortOwner $port
        if ($proc) { $busy += "port $port held by $($proc.ProcessName) (pid $($proc.Id))" }
    }
    if ($busy.Count -gt 0) {
        Write-Check 'Ports free' $false ($busy -join '; ')
        Write-Fix @(
            'Sniplink may already be running. Shut it down first:',
            '  .\dev.cmd stop'
        )
        exit 1
    }
    Write-Check 'Ports free' $true "$ApiPort, $UiPort"

    # --- UI dependencies ---------------------------------------------------
    if (-not (Test-Path (Join-Path $UiDir 'node_modules'))) {
        Write-Check 'UI dependencies' $false 'node_modules missing'
        Write-Host ''
        Write-Host '  Installing UI dependencies (one time)...' -ForegroundColor Yellow
        Push-Location $UiDir
        try { & npm install }
        finally { Pop-Location }
        if ($LASTEXITCODE -ne 0) {
            Write-Fix @('npm install failed. Run it manually in sniplink-ui and try again.')
            exit 1
        }
    }
    else {
        Write-Check 'UI dependencies' $true
    }

    # --- Launch ------------------------------------------------------------
    Write-Header 'Starting services'

    # Commands are passed as -Command strings, not script files, so execution
    # policy does not apply to these child windows either.
    $apiCmd = "`$Host.UI.RawUI.WindowTitle = 'Sniplink API'; " +
              "`$env:JAVA_HOME = '$javaHome'; " +
              "Set-Location '$ApiDir'; " +
              ".\mvnw.cmd spring-boot:run"

    $uiCmd  = "`$Host.UI.RawUI.WindowTitle = 'Sniplink UI'; " +
              "Set-Location '$UiDir'; " +
              "npm run dev"

    # -NoExit keeps a crashed service's error on screen instead of the window
    # vanishing before it can be read.
    $api = Start-Process powershell -ArgumentList '-NoExit', '-Command', $apiCmd -PassThru
    Write-Host "  API window started (pid $($api.Id))" -ForegroundColor DarkGray

    $ui = Start-Process powershell -ArgumentList '-NoExit', '-Command', $uiCmd -PassThru
    Write-Host "  UI window started  (pid $($ui.Id))" -ForegroundColor DarkGray

    Save-State $api.Id $ui.Id

    # --- Readiness ---------------------------------------------------------
    Write-Host ''
    # The API budget is generous: a first run downloads Maven before compiling.
    $apiReady = Wait-ForUrl "http://localhost:$ApiPort/v3/api-docs" 180 'API'
    $uiReady  = Wait-ForUrl "http://localhost:$UiPort/" 60 'UI'

    Write-Host ''
    if ($apiReady -and $uiReady) {
        Write-Host '  Sniplink is up.' -ForegroundColor Green
        Write-Host ''
        Write-Host "  UI    http://localhost:$UiPort"
        Write-Host "  API   http://localhost:$ApiPort"
        Write-Host "  Docs  http://localhost:$ApiPort/swagger-ui.html"
        Write-Host ''
        Write-Host '  Stop with:  .\dev.cmd stop' -ForegroundColor DarkGray
    }
    else {
        Write-Host '  Not everything came up.' -ForegroundColor Yellow
        if (-not $apiReady) { Write-Host '    API did not respond - check the "Sniplink API" window.' -ForegroundColor Yellow }
        if (-not $uiReady)  { Write-Host '    UI did not respond - check the "Sniplink UI" window.'  -ForegroundColor Yellow }
    }
    Write-Host ''
}

function Wait-ForUrl($url, $timeoutSeconds, $label) {
    $deadline = (Get-Date).AddSeconds($timeoutSeconds)
    Write-Host "  waiting for $label" -NoNewline
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 3
            if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 500) {
                Write-Host ' ready' -ForegroundColor Green
                return $true
            }
        }
        catch {
            # Not up yet; keep polling.
        }
        Write-Host '.' -NoNewline
        Start-Sleep -Seconds 2
    }
    Write-Host ' timed out' -ForegroundColor Yellow
    return $false
}

function Invoke-Help {
    Write-Header 'Sniplink dev launcher'
    Write-Host '  .\dev.cmd           start the API and UI in their own windows'
    Write-Host '  .\dev.cmd stop      shut down whatever was started'
    Write-Host '  .\dev.cmd status    report what is running, change nothing'
    Write-Host ''
    Write-Host '  From git-bash, use ./dev.sh instead.' -ForegroundColor DarkGray
    Write-Host ''
}

switch ($Action) {
    'start'  { Invoke-Start }
    'stop'   { Invoke-Stop }
    'status' { Invoke-Status }
    'help'   { Invoke-Help }
}
