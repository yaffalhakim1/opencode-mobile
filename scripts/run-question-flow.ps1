# Prepares a session with a pending question, then runs the question flow.
#
# The agent decides for itself whether to call the `question` tool, so a flow that
# simply asks cannot rely on one appearing — it passed once and failed the next
# run because the model answered directly. Injecting the form makes the UI half
# deterministic while still exercising exactly what the app owns: rendering the
# card, answering it, and clearing it.
#
# Usage:
#   pwsh -File scripts/run-question-flow.ps1
param(
    [string]$BaseUrl = 'http://127.0.0.1:45278',
    [string]$Maestro = 'C:\Users\yafit\maestro\maestro\bin\maestro.bat'
)

$ErrorActionPreference = 'Stop'

$password = $env:OPENCODE_PASSWORD
if (-not $password) {
    $serviceJson = Join-Path $env:USERPROFILE '.config\opencode\service.json'
    $password = (Get-Content $serviceJson -Raw | ConvertFrom-Json).password
}
$auth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("opencode:$password"))
$headers = @{ Authorization = "Basic $auth" }

$session = Invoke-RestMethod -Uri "$BaseUrl/api/session" -Headers $headers `
    -Method Post -Body '{"title":"Question card harness"}' -ContentType 'application/json'
$sessionID = $session.data.id
Write-Host "Session: $sessionID"

# A user turn first, so the card renders in a realistic position.
$prompt = @{ text = 'Ask me which task to work on next.' } | ConvertTo-Json -Compress
Invoke-RestMethod -Uri "$BaseUrl/api/session/$sessionID/prompt" -Headers $headers `
    -Method Post -Body $prompt -ContentType 'application/json' | Out-Null

for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 3
    $active = Invoke-RestMethod -Uri "$BaseUrl/api/session/active" -Headers $headers
    if (-not $active.data.PSObject.Properties.Name.Contains($sessionID)) { break }
}

$form = @{
    title  = 'Questions'
    fields = @(
        @{
            key      = 'q0'
            type     = 'string'
            title    = 'Next task'
            required = $true
            options  = @(
                @{ value = 'Build something'; label = 'Build something' },
                @{ value = 'Fix a bug'; label = 'Fix a bug' },
                @{ value = 'Review code'; label = 'Review code' }
            )
        }
    )
} | ConvertTo-Json -Depth 8 -Compress
$created = Invoke-RestMethod -Uri "$BaseUrl/api/session/$sessionID/form" `
    -Headers $headers -Method Post -Body $form -ContentType 'application/json'
Write-Host "Form: $($created.data.id)"

& $Maestro test maestro/question.yaml -e SESSION_TITLE='Question card harness'
exit $LASTEXITCODE
