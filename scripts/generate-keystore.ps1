param(
  [string]$Alias = "mykey",
  [string]$StorePass = "changeit",
  [string]$KeyPass = $null
)

if (-not $KeyPass) { $KeyPass = $StorePass }
$store = Join-Path -Path (Get-Location) -ChildPath "keystore.jks"

Write-Host "Generating keystore at $store (alias=$Alias)"
& keytool -genkeypair -keystore $store -storepass $StorePass -alias $Alias -keypass $KeyPass -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Secure Vault, OU=Dev, O=Organization, L=City, S=State, C=US"

Write-Host "Keystore generated: $store"
Write-Host "To produce base64 for GitHub secret run (PowerShell):"
Write-Host "[Convert]::ToBase64String([IO.File]::ReadAllBytes('keystore.jks')) | Out-File -Encoding ascii keystore.b64"
