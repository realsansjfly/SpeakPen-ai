@echo off
chcp 65001 >nul
"C:\Users\Realsansjfly\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\scripts\stop-service.ps1"
pause