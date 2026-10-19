# Prepares a session with a pending question, then runs the tools flow.
#
# The `question` tool is a form the server holds open, and whether an agent calls
# it depends on that agent's toolset — so a flow cannot reliably produce one by
# asking. This creates a session, injects a form into it, and hands the session
# id to the flow, which opens it directly.
#
# Usage:
#   pwsh -File scripts/run-tools-flow.ps1
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

# A fresh session in the workspace, so the flow starts from a known state.
$session = Invoke-RestMethod -Uri "$BaseUrl/api/session" -Headers $headers `
    -Method Post -Body '{}' -ContentType 'application/json'
$sessionID = $session.data.id
Write-Host "Session: $sessionID"

# Ask for the shell tool first; the flow asserts on that card.
$prompt = @{ text = 'Run the shell command: echo hello-from-tool' } | ConvertTo-Json -Compress
Invoke-RestMethod -Uri "$BaseUrl/api/session/$sessionID/prompt" -Headers $headers `
    -Method Post -Body $prompt -ContentType 'application/json' | Out-Null
Write-Host 'Prompted shell tool'

# Wait for the turn to settle so the form is the only pending thing.
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 3
    $active = Invoke-RestMethod -Uri "$BaseUrl/api/session/active" -Headers $headers
    if (-not $active.data.PSObject.Properties.Name.Contains($sessionID)) { break }
}
Write-Host 'Turn settled'

# The server renames the session after the first turn, so the flow needs the
# generated title rather than the prompt it was given.
$detail = Invoke-RestMethod -Uri "$BaseUrl/api/session/$sessionID" -Headers $headers
$title = $detail.data.title
if (-not $title) { throw 'Session has no title to open by' }
Write-Host "Title: $title"

# Inject the question form.
$form = @{
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
$created = Invoke-RestMethod -Uri "$BaseUrl/api/session/$sessionID/form" `
    -Headers $headers -Method Post -Body $form -ContentType 'application/json'
Write-Host "Form: $($created.data.id)"

& $Maestro test maestro/tools.yaml -e SESSION_ID=$sessionID -e SESSION_TITLE=$title
exit $LASTEXITCODE
