@echo off
REM Entry point for the Sniplink dev launcher.
REM
REM Batch files are not subject to PowerShell's execution policy, and
REM -ExecutionPolicy Bypass is process-scoped and needs no admin rights.
REM That is why this shim exists: a default PowerShell profile is Restricted
REM and would refuse to run dev.ps1 directly.
REM
REM   dev.cmd          start
REM   dev.cmd stop     shut down
REM   dev.cmd status   report only

set "SNIPLINK_ACTION=%~1"
if "%SNIPLINK_ACTION%"=="" set "SNIPLINK_ACTION=start"

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0dev.ps1" -Action %SNIPLINK_ACTION%
exit /b %ERRORLEVEL%
