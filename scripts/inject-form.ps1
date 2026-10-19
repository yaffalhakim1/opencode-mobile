# Creates a pending `question` form on a session, for the tools flow.
#
# The `question` tool is a form the server holds open, and whether an agent calls
# it depends on that agent's toolset — so a Maestro flow cannot reliably produce
# one by asking. Injecting it directly is deterministic and still exercises the
# part the app owns: rendering the card and submitting the answer.
#
# Usage:
#   pwsh -File scripts/inject-form.ps1 -SessionID ses_xxx
#   pwsh -File scripts/inject-form.ps1 -Latest
param(
    [string]$SessionID,
    [switch]$Latest,
    [string]$BaseUrl = 'http://127.0.0.1:45278'
)

$ErrorActionPreference = 'Stop'

$password = $env:OPENCODE_PASSWORD
if (-not $password) {
    $serviceJson = Join-Path $env:USERPROFILE '.config\opencode\service.json'
    if (Test-Path $serviceJson) {
        $password = (Get-Content $serviceJson -Raw | ConvertFrom-Json).password
    }
}
if (-not $password) {
    throw 'Set OPENCODE_PASSWORD or provide ~/.config/opencode/service.json'
}

$auth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("opencode:$password"))
$headers = @{ Authorization = "Basic $auth" }

if ($Latest -or -not $SessionID) {
    $sessions = Invoke-RestMethod -Uri "$BaseUrl/api/session?limit=1&order=desc" -Headers $headers
    $SessionID = $sessions.data[0].id
    Write-Host "Using latest session: $SessionID"
}

$payload = @{
    title  = 'Which colour do you prefer?'
    fields = @(
        @{
            key      = 'colour'
            type     = 'string'
            title    = 'Colour'
            required = $true
            options  = @(
                @{ value = 'red'; label = 'Red' },
                @{ value = 'blue'; label = 'Blue' }
            )
        }
    )
} | ConvertTo-Json -Depth 8 -Compress

$result = Invoke-RestMethod -Uri "$BaseUrl/api/session/$SessionID/form" `
    -Headers $headers -Method Post -Body $payload -ContentType 'application/json'

Write-Host "Created form: $($result.data.id)"
