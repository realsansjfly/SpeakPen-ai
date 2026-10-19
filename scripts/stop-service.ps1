$ErrorActionPreference = 'Stop'
$workspacePath = Split-Path -Parent $PSScriptRoot
$statePath = Join-Path $workspacePath '.local\processes.json'
if (-not (Test-Path -LiteralPath $statePath)) { Write-Output '没有登记的服务进程。'; return }
$state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
foreach ($entry in @(@{Pid=$state.tunnelPid;Expected='cloudflared.exe';Marker='127.0.0.1:8787'},@{Pid=$state.serverPid;Expected='node.exe';Marker=(Join-Path $workspacePath 'server\src.mjs')})) {
    if (-not $entry.Pid) { continue }
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($entry.Pid)"
    if ($process -and $process.Name -eq $entry.Expected -and $process.CommandLine.Contains($entry.Marker)) {
        Stop-Process -Id $entry.Pid
        Write-Output "已停止 $($entry.Expected)。"
    }
}
Remove-Item -LiteralPath $statePath
