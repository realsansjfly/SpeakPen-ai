param([switch]$SkipTunnel, [switch]$Restart, [switch]$RebuildTunnel)
$ErrorActionPreference = 'Stop'
$workspacePath = Split-Path -Parent $PSScriptRoot
$runtimePath = Join-Path $workspacePath '.local'
New-Item -ItemType Directory -Path $runtimePath -Force | Out-Null
Import-Module (Join-Path $PSScriptRoot 'ServiceConnection.psm1') -Force
$workspaceHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($workspacePath))).Substring(0,16)
$mutex = [Threading.Mutex]::new($false, "Local\SpeakPenStartup_$workspaceHash")
$locked = $false
try {
    try { $locked = $mutex.WaitOne(0) } catch [Threading.AbandonedMutexException] { $locked = $true }
    if (-not $locked) { throw '服务已经在另一个启动窗口中检查，请等待该窗口完成。' }
    $nodePath = (Get-Command node.exe).Source
    $envPath = Join-Path $workspacePath 'server\.env'
    if (-not (Test-Path -LiteralPath $envPath)) { throw 'server/.env 尚未配置。' }
    $lines = [Collections.Generic.List[string]]::new()
    Get-Content -LiteralPath $envPath | ForEach-Object { $lines.Add($_) }
    function Get-ServerValue([string]$name) {
        $line = $lines | Where-Object { $_.StartsWith($name + '=') } | Select-Object -First 1
        if ($null -ne $line) { return $line.Substring($name.Length + 1) }; return ''
    }
    function Set-ServerValue([string]$name, [string]$value) {
        for ($i=$lines.Count-1; $i-ge 0; $i--) { if ($lines[$i].StartsWith($name+'=')) { $lines.RemoveAt($i) } }
        $lines.Add($name+'='+$value)
    }
    $accessToken = Get-ServerValue 'SERVICE_ACCESS_TOKEN'
    if (-not $accessToken) {
        $bytes = [byte[]]::new(32); [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
        $accessToken = [Convert]::ToHexString($bytes).ToLowerInvariant()
    }
    Set-ServerValue 'SERVICE_ACCESS_TOKEN' $accessToken
    Set-ServerValue 'HOST' '127.0.0.1'; Set-ServerValue 'PORT' '8787'; Set-ServerValue 'DISABLE_STREAMING' 'true'
    Set-ServerValue 'USAGE_FILE' ((Join-Path $runtimePath 'usage.json').Replace('\','/'))
    if (-not (Get-ServerValue 'MAX_REQUESTS_PER_MINUTE')) { Set-ServerValue 'MAX_REQUESTS_PER_MINUTE' '30' }
    if (-not (Get-ServerValue 'MAX_DAILY_REQUESTS')) { Set-ServerValue 'MAX_DAILY_REQUESTS' '500' }
    [IO.File]::WriteAllLines($envPath,$lines,[Text.UTF8Encoding]::new($false))
    $headers = @{Authorization='Bearer '+$accessToken}
    $version = (Get-Content -LiteralPath (Join-Path $workspacePath 'server\package.json') -Raw | ConvertFrom-Json).version
    $statePath = Join-Path $runtimePath 'processes.json'
    $previous = $null
    if (Test-Path -LiteralPath $statePath) { try { $previous=Get-Content -LiteralPath $statePath -Raw|ConvertFrom-Json } catch { } }
    $serverPid = $null; $tunnelPid = $previous.tunnelPid; $publicUrl = $previous.publicUrl
    function Save-State([bool]$verified=$false) {
        $state=@{serverPid=$serverPid;tunnelPid=$tunnelPid;publicUrl=$publicUrl;verified=$verified;checkedAt=(Get-Date).ToString('o')}
        $temp=$statePath+'.tmp'; $state|ConvertTo-Json|Set-Content -LiteralPath $temp -Encoding utf8
        Move-Item -LiteralPath $temp -Destination $statePath -Force
    }
    function Owned-Process($processId,[string]$name,[string]$marker) {
        if (-not $processId -or "$processId" -notmatch '^\d+$') { return $null }
        $process=Get-CimInstance Win32_Process -Filter "ProcessId=$processId"
        if ($process -and $process.Name -eq $name -and $process.CommandLine -and $process.CommandLine.Contains($marker)) { return $process }
        return $null
    }
    $srcPath=Join-Path $workspacePath 'server\src.mjs'
    $serverProcess=Owned-Process $previous.serverPid 'node.exe' $srcPath
    $localStatus=$null
    try { $localStatus=Invoke-RestMethod -Uri 'http://127.0.0.1:8787/v1/status' -Headers $headers -TimeoutSec 3 -MaximumRedirection 0 } catch { }
    $localHealthy=$localStatus.ready -eq $true -and $localStatus.service -eq 'SpeakPen' -and $localStatus.instanceId -and $localStatus.version -eq $version
    if ($serverProcess -and ($Restart -or -not $localHealthy)) {
        Stop-Process -Id $serverProcess.ProcessId; $serverProcess=$null; $localHealthy=$false
    }
    if ($serverProcess -and $localHealthy) { $serverPid=$serverProcess.ProcessId }
    elseif ($localHealthy) {
        $listener=Get-NetTCPConnection -LocalAddress '127.0.0.1' -LocalPort 8787 -State Listen -ErrorAction SilentlyContinue|Select-Object -First 1
        $owned=Owned-Process $listener.OwningProcess 'node.exe' $srcPath
        if (-not $owned) { throw '8787 端口已由未登记进程占用，未停止该进程。' }
        $serverPid=$owned.ProcessId
    } else {
        $arguments=@('--env-file="'+$envPath+'"','"'+$srcPath+'"')
        $server=Start-Process -FilePath $nodePath -ArgumentList $arguments -WorkingDirectory $workspacePath -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtimePath 'server.log') -RedirectStandardError (Join-Path $runtimePath 'server-error.log')
        $serverPid=$server.Id; Save-State
        $ready=$false
        for ($attempt=0;$attempt-lt 24;$attempt++) {
            try {
                $localStatus=Invoke-RestMethod -Uri 'http://127.0.0.1:8787/v1/status' -Headers $headers -TimeoutSec 2 -MaximumRedirection 0
                if ($localStatus.ready -eq $true -and $localStatus.service -eq 'SpeakPen' -and $localStatus.instanceId -and $localStatus.version -eq $version) { $ready=$true;break }
            } catch { }
            if ($server.HasExited) { break }; Start-Sleep -Milliseconds 250
        }
        if (-not $ready) { throw '本机服务未能启动或鉴权失败，请检查 .local/server-error.log。' }
    }
    Write-Output '本机服务与鉴权正常。正在检查手机使用的公网连接…'
    Save-State
    if ($SkipTunnel) { Write-Output 'LOCAL_SERVICE_READY: http://127.0.0.1:8787'; return }
    $cloudflared=Join-Path $workspacePath '.tooling\network\cloudflared.exe'
    if (-not (Test-Path -LiteralPath $cloudflared)) { throw '缺少 Cloudflare 官方 tunnel 工具。' }
    $signature=Get-AuthenticodeSignature -LiteralPath $cloudflared
    if ($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notlike '*Cloudflare*') { throw 'Tunnel 工具官方签名未通过。' }
    $tunnelProcess=Owned-Process $tunnelPid 'cloudflared.exe' 'http://127.0.0.1:8787'
    $probe={
        param($url)
        $inputJson=@{url=$url;token=$accessToken}|ConvertTo-Json -Compress
        $result=$inputJson|& $nodePath (Join-Path $PSScriptRoot 'probe-public.mjs')
        if($LASTEXITCODE -ne 0){throw '公网服务请求失败。'}
        $result|ConvertFrom-Json
    }
    $decision=Get-TunnelReuseDecision -ProcessOwned ([bool]$tunnelProcess) -PublicUrl $publicUrl -LocalInstanceId $localStatus.instanceId -Probe $probe
    if (-not $decision.Reuse -or $RebuildTunnel) {
        Write-Output "旧公网连接不可复用，重新建立连接（$($decision.Reason)）。"
        if ($tunnelProcess) { Stop-Process -Id $tunnelProcess.ProcessId }
        $tunnelPid=$null;$publicUrl=$null;Save-State
        $stamp=Get-Date -Format 'yyyyMMdd-HHmmss-fff'
        $tunnelLog=Join-Path $runtimePath "tunnel-$stamp.log"
        $tunnelStdout=Join-Path $runtimePath "tunnel-$stamp-stdout.log"
        $arguments=@('tunnel','--url','http://127.0.0.1:8787','--no-autoupdate','--protocol','http2')
        $tunnel=Start-Process -FilePath $cloudflared -ArgumentList $arguments -WorkingDirectory $workspacePath -WindowStyle Hidden -PassThru -RedirectStandardOutput $tunnelStdout -RedirectStandardError $tunnelLog
        $tunnelPid=$tunnel.Id;Save-State
        for ($attempt=0;$attempt-lt 90;$attempt++) {
            if (Test-Path -LiteralPath $tunnelLog) {
                $log=[string](Get-Content -LiteralPath $tunnelLog -Raw)
                $match=[regex]::Match($log,'https://[a-z0-9-]+\.trycloudflare\.com')
                if ($match.Success) { $publicUrl=$match.Value;Save-State;break }
            }
            if ($tunnel.HasExited) { break };Start-Sleep -Milliseconds 500
        }
        if (-not $publicUrl) { throw '公网地址尚未生成，请检查 .local 下最新 tunnel 日志。本机服务仍在运行。' }
        $verified=$false
        for ($attempt=0;$attempt-lt 6;$attempt++) {
            $decision=Get-TunnelReuseDecision -ProcessOwned (-not $tunnel.HasExited) -PublicUrl $publicUrl -LocalInstanceId $localStatus.instanceId -Probe $probe
            if ($decision.Reuse) { $verified=$true;break };Start-Sleep -Milliseconds 500
        }
        if (-not $verified) { throw '公网连接未通过实际访问检查，尚不能供手机使用。请稍后重试启动，不要使用旧二维码。' }
    }
    # Publish pairing information only after the same authenticated Node instance is reachable publicly.
    $connectionPath=Join-Path $runtimePath 'connection.json'
    @{baseUrl=($publicUrl+'/v1');accessToken=$accessToken;webUrl=$publicUrl}|ConvertTo-Json|Set-Content -LiteralPath ($connectionPath+'.tmp') -Encoding utf8
    Move-Item -LiteralPath ($connectionPath+'.tmp') -Destination $connectionPath -Force
    Save-State $true
    $generator=Join-Path $workspacePath 'scripts\make-launcher.py'
    & python $generator
    if ($LASTEXITCODE -ne 0) { throw '公网服务正常，但连接入口生成失败。请检查 Python 二维码依赖。' }
    Write-Output "SERVICE_READY: $publicUrl"
    Write-Output '手机公网访问与鉴权均已验证。请扫描新入口，并在手机上点“连接 App”。'
} catch {
    try { if (Get-Command Save-State -ErrorAction SilentlyContinue) { Save-State $false } } catch { }
    throw
} finally {
    if ($locked) { $mutex.ReleaseMutex() };$mutex.Dispose()
}

