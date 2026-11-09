param([Parameter(Mandatory = $true)][string]$BaseUrl)
$ErrorActionPreference = 'Stop'
$uri = $null
$BaseUrl = $BaseUrl.Trim().TrimEnd('/')
if (-not [Uri]::TryCreate($BaseUrl, [UriKind]::Absolute, [ref]$uri) -or $uri.Scheme -ne 'https' -or
    $uri.AbsolutePath -ne '/v1' -or $uri.UserInfo -or $uri.Query -or $uri.Fragment) {
    throw '请填写 https://你的域名/v1。'
}
$secret = Read-Host '粘贴服务器生成的 SERVICE_ACCESS_TOKEN（输入不显示）' -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
try { $token = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
if ($token -cnotmatch '^[a-f0-9]{64}$') { throw '令牌应为 64 位小写十六进制字符；不要填写 DashScope API Key。' }
$localDir = Join-Path (Split-Path -Parent $PSScriptRoot) '.local'
New-Item -ItemType Directory -Path $localDir -Force | Out-Null
@{ baseUrl = $BaseUrl; accessToken = $token } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $localDir 'cloud-connection.json') -Encoding utf8
Write-Output '云端连接已保存到忽略目录。现在运行 scripts/build.ps1 -Cloud。'
