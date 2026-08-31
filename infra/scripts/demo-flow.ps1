<#
  End-to-end demo of the platform through the API gateway.

    pwsh infra/scripts/demo-flow.ps1
    pwsh infra/scripts/demo-flow.ps1 -BaseUrl http://localhost:8080

  Assumes the stack is up (docker compose ... up -d) and healthy.
#>
param(
  [string]$BaseUrl = "http://localhost:8080",
  [int]$ClientId = 1
)

$ErrorActionPreference = "Stop"

function Step($msg) { Write-Host "`n=== $msg ===" -ForegroundColor Cyan }
function Show($obj) { $obj | ConvertTo-Json -Depth 6 }

Step "Wait for the gateway to route (lb:// routes activate after the first Eureka fetch)"
$ready = $false
foreach ($i in 1..45) {
  try {
    Invoke-RestMethod "$BaseUrl/api/assets?clientId=$ClientId" -ErrorAction Stop | Out-Null
    $ready = $true; break
  } catch { Start-Sleep -Seconds 2 }
}
if (-not $ready) { throw "gateway not routing after 90s" }
Write-Host "gateway ready"

Step "Clients (public)"
Show (Invoke-RestMethod "$BaseUrl/api/clients")

Step "Assets in stock for client $ClientId (public)"
$stock = Invoke-RestMethod "$BaseUrl/api/assets?clientId=$ClientId&status=IN_STOCK"
$assetId = $stock[0].id
Write-Host "picked asset $assetId ($($stock[0].assetTag))"

Step "All laptops (public)"
$laptops = Invoke-RestMethod "$BaseUrl/api/assets?clientId=$ClientId&type=LAPTOP"
Write-Host "$($laptops.Count) laptops"

Step "People (public)"
$people = Invoke-RestMethod "$BaseUrl/api/people?clientId=$ClientId"
$personId = $people[0].id
Write-Host "picked person $personId ($($people[0].fullName))"

Step "Desks (public)"
$desks = Invoke-RestMethod "$BaseUrl/api/locations?clientId=$ClientId&kind=DESK"
Write-Host "$($desks.Count) desks"

Step "Failure path: check-out with no token -> 401"
try {
  Invoke-RestMethod -Method Post "$BaseUrl/api/assignments" -ContentType application/json `
    -Body (@{ clientId = $ClientId; assetId = $assetId; holderType = "PERSON"; holderId = $personId } | ConvertTo-Json) | Out-Null
  throw "expected 401"
} catch { Write-Host "got $($_.Exception.Response.StatusCode.value__) as expected" }

Step "Sign in as the seeded tech"
$login = Invoke-RestMethod -Method Post "$BaseUrl/api/auth/login" -ContentType application/json `
  -Body (@{ email = "tech@acme.example"; password = "Passw0rd!" } | ConvertTo-Json)
$auth = @{ Authorization = "Bearer $($login.token)" }
Write-Host "token acquired"

Step "Check asset $assetId out to person $personId"
$assign = Invoke-RestMethod -Method Post "$BaseUrl/api/assignments" -Headers $auth -ContentType application/json `
  -Body (@{ clientId = $ClientId; assetId = $assetId; holderType = "PERSON"; holderId = $personId } | ConvertTo-Json)
Show $assign
if (-not $assign.open) { throw "expected an open assignment" }
if ($assign.checkedOutBy -ne "tech@acme.example") { throw "expected checkedOutBy=tech@acme.example, got $($assign.checkedOutBy)" }

Step "It shows on the person"
Show (Invoke-RestMethod "$BaseUrl/api/assets?clientId=$ClientId&holderType=PERSON&holderId=$personId")

Step "Failure path: check it out again -> 409"
try {
  Invoke-RestMethod -Method Post "$BaseUrl/api/assignments" -Headers $auth -ContentType application/json `
    -Body (@{ clientId = $ClientId; assetId = $assetId; holderType = "PERSON"; holderId = $personId } | ConvertTo-Json) | Out-Null
  throw "expected 409"
} catch { Write-Host "got $($_.Exception.Response.StatusCode.value__) as expected" }

Step "Offboard person $personId"
$result = Invoke-RestMethod -Method Post "$BaseUrl/api/assignments/offboard?clientId=$ClientId&personId=$personId" -Headers $auth
Show $result

Step "Asset $assetId is back in stock"
$after = Invoke-RestMethod "$BaseUrl/api/assets/$assetId"
Write-Host "status = $($after.status)"
if ($after.status -ne "IN_STOCK") { throw "expected IN_STOCK, got $($after.status)" }

Step "Notifications for client $ClientId (public)"
Show (Invoke-RestMethod "$BaseUrl/api/notifications?clientId=$ClientId")

Write-Host "`nAll demo steps passed." -ForegroundColor Green
