$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'ServiceConnection.psm1') -Force
$cases = @(
    @{Name='alive but unreachable tunnel must be rebuilt'; Owned=$true; Url='https://expired.trycloudflare.com'; Local='current'; Probe={ throw 'ENOTFOUND' }; Expected=$false},
    @{Name='tunnel returning not-ready must not be reused'; Owned=$true; Url='https://notready.trycloudflare.com'; Local='current'; Probe={ [pscustomobject]@{ready=$false; service='SpeakPen'; instanceId='current'} }; Expected=$false},
    @{Name='unrelated endpoint must not be reused'; Owned=$true; Url='https://wrong.trycloudflare.com'; Local='current'; Probe={ [pscustomobject]@{ready=$true; service='Other'; instanceId='current'} }; Expected=$false},
    @{Name='old service instance must not be reused'; Owned=$true; Url='https://old.trycloudflare.com'; Local='current'; Probe={ [pscustomobject]@{ready=$true; service='SpeakPen'; instanceId='old'} }; Expected=$false},
    @{Name='authentication rejection must not be reused'; Owned=$true; Url='https://auth.trycloudflare.com'; Local='current'; Probe={ throw 'HTTP 401' }; Expected=$false},
    @{Name='token must not be sent to malformed stored address'; Owned=$true; Url='https://wrong.example/path'; Local='current'; Probe={ throw 'MUST NOT CALL' }; Expected=$false},
    @{Name='missing local identity cannot establish health'; Owned=$true; Url='https://valid.trycloudflare.com'; Local=''; Probe={ [pscustomobject]@{ready=$true; service='SpeakPen'; instanceId=''} }; Expected=$false},
    @{Name='missing process cannot be reused'; Owned=$false; Url='https://valid.trycloudflare.com'; Local='current'; Probe={ throw 'MUST NOT CALL' }; Expected=$false},
    @{Name='healthy authenticated route to this service is reusable'; Owned=$true; Url='https://valid.trycloudflare.com'; Local='current'; Probe={ [pscustomobject]@{ready=$true; service='SpeakPen'; instanceId='current'} }; Expected=$true}
)
$failed = 0
foreach ($case in $cases) {
    $result = Get-TunnelReuseDecision -ProcessOwned $case.Owned -PublicUrl $case.Url -LocalInstanceId $case.Local -Probe $case.Probe
    if ($result.Reuse -ne $case.Expected) { $failed++; Write-Output "FAIL: $($case.Name)" }
    else { Write-Output "PASS: $($case.Name)" }
}
if ($failed) { throw "$failed connection regression checks failed." }
Write-Output "CONNECTION_REGRESSION_PASS: $($cases.Count) cases"
