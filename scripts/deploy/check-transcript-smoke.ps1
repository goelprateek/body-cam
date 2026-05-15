param(
    [Parameter(Mandatory = $true)]
    [string]$BaseUrl,

    [Parameter(Mandatory = $true)]
    [string]$BearerToken
)

$normalizedBaseUrl = $BaseUrl.TrimEnd('/')
$uri = "$normalizedBaseUrl/api/transcripts/smoke-check"

$headers = @{
    Authorization = "Bearer $BearerToken"
}

Write-Host "Checking transcript smoke status at $uri"

try {
    $response = Invoke-RestMethod -Method Get -Uri $uri -Headers $headers
} catch {
    Write-Error "Transcript smoke-check request failed: $($_.Exception.Message)"
    exit 1
}

if (-not $response) {
    Write-Error "Transcript smoke-check returned no payload."
    exit 1
}

Write-Host "Transcript ready: $($response.ready)"
Write-Host "Transcript enabled: $($response.enabled)"
Write-Host "Transcript engine: $($response.engine)"
Write-Host "Transcript endpoint: $($response.endpoint)"
Write-Host "Transcript poll delay: $($response.pollDelayMs)"

if ($response.warnings) {
    Write-Host "Warnings:"
    foreach ($warning in $response.warnings) {
        Write-Host " - $warning"
    }
}

if (-not $response.ready) {
    Write-Error "Transcript smoke-check failed readiness validation."
    exit 1
}

Write-Host "Transcript smoke-check passed."
