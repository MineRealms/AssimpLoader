param(
    [string]$World = 'Scaffold_PBR_Test',
    [string]$ForgeVersion = '47.4.10'
)
$ErrorActionPreference = 'Stop'
$project = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
Set-Location -LiteralPath $project
if (-not (Test-Path -LiteralPath (Join-Path $project "run\saves\$World") -PathType Container)) {
    throw "Test world does not exist: $World"
}
& .\gradlew.bat runClient '-PwithOculus' "-Pforge_version=$ForgeVersion" "-PclientVehicleSmoke=$World" --console=plain --no-daemon --no-parallel *> logs/armored-car-client.log
exit $LASTEXITCODE
