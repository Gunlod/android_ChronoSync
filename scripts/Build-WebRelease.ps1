$ErrorActionPreference = 'Stop'
$projectDirectory = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Push-Location -LiteralPath $projectDirectory
try {
    & './gradlew.bat' ':app:assembleRelease' ':app:testDebugUnitTest' ':app:lintRelease' '-Pchronosync.requireReleaseSigning=true' '--console=plain'
    if ($LASTEXITCODE -ne 0) { throw "Release build failed: $LASTEXITCODE" }
    $outputDirectory = Join-Path $projectDirectory 'app/build/outputs/apk/release'
    $metadata = Get-Content -LiteralPath (Join-Path $outputDirectory 'output-metadata.json') -Raw | ConvertFrom-Json
    if ($metadata.elements.Count -ne 1) { throw 'Expected one universal release APK.' }
    $element = $metadata.elements[0]
    $fileName = "ChronoSync-$($element.versionName).apk"
    $distributionDirectory = Join-Path $projectDirectory 'dist'
    New-Item -ItemType Directory -Path $distributionDirectory -Force | Out-Null
    $destination = Join-Path $distributionDirectory $fileName
    Copy-Item -LiteralPath (Join-Path $outputDirectory $element.outputFile) -Destination $destination
    $checksum = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
    [System.IO.File]::WriteAllText("$destination.sha256", "$checksum  $fileName`n", (New-Object System.Text.UTF8Encoding $false))
    Write-Host "Web distribution APK: $destination"
    Write-Host "SHA-256: $checksum"
} finally { Pop-Location }
