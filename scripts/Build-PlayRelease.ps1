$ErrorActionPreference = 'Stop'
$projectDirectory = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Push-Location -LiteralPath $projectDirectory
try {
    & './gradlew.bat' ':app:bundleRelease' ':app:testDebugUnitTest' ':app:lintRelease' '-Pchronosync.requireReleaseSigning=true' '--console=plain'
    if ($LASTEXITCODE -ne 0) { throw "Release build failed: $LASTEXITCODE" }
    Write-Host 'Signed upload bundle: app/build/outputs/bundle/release/app-release.aab'
} finally { Pop-Location }
