$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

$AppVersion = if ($env:APP_VERSION) { $env:APP_VERSION } else { "1.0.0" }
$MavenCommand = if ($env:MAVEN) { $env:MAVEN } else { "mvn" }

& $MavenCommand clean package dependency:copy-dependencies `
    "-DincludeScope=runtime" `
    "-DoutputDirectory=target/package-dependencies"
if ($LASTEXITCODE -ne 0) {
    throw "Maven package failed with exit code $LASTEXITCODE"
}

Get-ChildItem "target/package-dependencies/javafx-*.jar" |
    Where-Object { $_.Name -notlike "*-win.jar" } |
    Remove-Item -Force

$PackageOutput = Join-Path $Root "target/package-output"
$AppImage = Join-Path $PackageOutput "GameTracker"
$Dist = Join-Path $Root "dist"
New-Item -ItemType Directory -Force -Path $PackageOutput, $Dist | Out-Null

& jpackage `
    --type app-image `
    --name GameTracker `
    --app-version $AppVersion `
    --vendor "Game Tracker" `
    --module-path "target/classes;target/package-dependencies" `
    --module "dev.gametracker/dev.gametracker.Main" `
    --dest $PackageOutput
if ($LASTEXITCODE -ne 0) {
    throw "jpackage failed with exit code $LASTEXITCODE"
}

$ZipPath = Join-Path $Dist "GameTracker-$AppVersion-Windows-x64.zip"
if (Test-Path $ZipPath) {
    Remove-Item -LiteralPath $ZipPath -Force
}
Compress-Archive -Path (Join-Path $AppImage "*") -DestinationPath $ZipPath
Write-Output "Created $ZipPath"
