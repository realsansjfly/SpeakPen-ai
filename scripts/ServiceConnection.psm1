function Get-TunnelReuseDecision {
    param([bool]$ProcessOwned, [string]$PublicUrl, [string]$LocalInstanceId, [scriptblock]$Probe)
    if (-not $ProcessOwned) { return [pscustomobject]@{Reuse=$false;Reason='ProcessMissingOrUnowned'} }
    if ($PublicUrl -notmatch '^https://[a-z0-9-]+\.trycloudflare\.com$') { return [pscustomobject]@{Reuse=$false;Reason='InvalidAddress'} }
    if (-not $LocalInstanceId) { return [pscustomobject]@{Reuse=$false;Reason='LocalIdentityMissing'} }
    try {
        $status = & $Probe $PublicUrl
        $valid = $status.ready -eq $true -and $status.service -eq 'SpeakPen' -and $status.instanceId -eq $LocalInstanceId
        return [pscustomobject]@{Reuse=$valid;Reason=if ($valid) {'AuthenticatedRouteReady'} else {'WrongOrUnreadyService'}}
    } catch { return [pscustomobject]@{Reuse=$false;Reason='PublicRouteUnavailable'} }
}
Export-ModuleMember -Function Get-TunnelReuseDecision
