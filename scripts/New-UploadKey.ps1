param(
    [string]$KeyStorePath = (Join-Path $PSScriptRoot '../private-keys/chronosync-upload.p12')
)
$ErrorActionPreference = 'Stop'
$absoluteKeyPath = [System.IO.Path]::GetFullPath($KeyStorePath)
if (Test-Path -LiteralPath $absoluteKeyPath) { throw 'The upload key already exists. It will not be overwritten.' }
$keytool = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/keytool.exe' } else { (Get-Command keytool -ErrorAction Stop).Source }
if (-not (Test-Path -LiteralPath $keytool)) { throw 'Set JAVA_HOME to JDK 17 or 21.' }
New-Item -ItemType Directory -Path ([System.IO.Path]::GetDirectoryName($absoluteKeyPath)) -Force | Out-Null
Write-Host 'Enter and keep your password locally. Do not send it in chat. Back up the key securely.'
& $keytool -genkeypair -keystore $absoluteKeyPath -storetype PKCS12 -alias chronosync-upload -keyalg RSA -keysize 3072 -validity 10000
if ($LASTEXITCODE -ne 0) { throw "keytool failed: $LASTEXITCODE" }
Write-Host "Created upload key: $absoluteKeyPath"
Write-Host 'Copy keystore.properties.example to keystore.properties and fill in the local password.'
