@echo off
chcp 65001 >nul
"C:\Users\Realsansjfly\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\scripts\start-service.ps1"
if errorlevel 1 (
  echo 服务未通过检查，请查看上方错误。不会打开旧连接入口。
  pause
  exit /b 1
)
start "" "%~dp0打开真实对话.html"