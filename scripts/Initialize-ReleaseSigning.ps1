$ErrorActionPreference = 'Stop'
$projectDirectory = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$propertiesPath = Join-Path $projectDirectory 'keystore.properties'
$keyDirectory = Join-Path $projectDirectory 'private-keys'
$keyPath = Join-Path $keyDirectory 'chronosync-release.p12'
if (Test-Path -LiteralPath $propertiesPath) {
    Write-Host 'Existing keystore.properties will be used. No key was changed.'
    exit 0
}
if (Test-Path -LiteralPath $keyPath) {
    throw 'An existing release key was found. Restore its keystore.properties instead of replacing the key.'
}
$keytool = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/keytool.exe' } else { (Get-Command keytool -ErrorAction Stop).Source }
if (-not (Test-Path -LiteralPath $keytool)) { throw 'Set JAVA_HOME to JDK 17 or 21.' }
New-Item -ItemType Directory -Path $keyDirectory -Force | Out-Null
$random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$bytes = New-Object byte[] 32
try { $random.GetBytes($bytes) } finally { $random.Dispose() }
$password = [Convert]::ToBase64String($bytes)
$previousPassword = $env:CHRONOSYNC_KEYGEN_PASSWORD
try {
    $env:CHRONOSYNC_KEYGEN_PASSWORD = $password
    & $keytool -genkeypair -keystore $keyPath -storetype PKCS12 -alias chronosync-release -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Gunlod, OU=ChronoSync' -storepass:env CHRONOSYNC_KEYGEN_PASSWORD -keypass:env CHRONOSYNC_KEYGEN_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw "keytool failed: $LASTEXITCODE" }
    $lines = @(
        'storeFile=private-keys/chronosync-release.p12'
        "storePassword=$password"
        'keyAlias=chronosync-release'
        "keyPassword=$password"
    )
    [System.IO.File]::WriteAllLines($propertiesPath, $lines, (New-Object System.Text.UTF8Encoding $false))
} finally {
    $env:CHRONOSYNC_KEYGEN_PASSWORD = $previousPassword
    $password = $null
    [Array]::Clear($bytes, 0, $bytes.Length)
}
Write-Host 'Created a release signing key and local keystore.properties. Passwords were not printed.'
Write-Host 'Back up private-keys and keystore.properties securely. Distribute only the APK and its checksum.'
