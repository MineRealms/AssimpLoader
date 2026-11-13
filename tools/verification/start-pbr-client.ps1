param(
    [string]$World = 'New World',
    [string]$RosettaRepository,
    [string]$RosettaVersion = '1.0.0',
    [int]$Port = 48801,
    [string]$ForgeVersion,
    [string]$LogName = 'pbr-client'
)
$ErrorActionPreference = 'Stop'
$projectDirectory = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
Set-Location -LiteralPath $projectDirectory
New-Item -ItemType Directory -Path (Join-Path $projectDirectory 'logs') -Force | Out-Null
$arguments = @('runClient', '-PwithOculus', "-PclientPbrSmoke=$World", '-PclientPbrKeepOpen',
    '--console=plain', '--no-daemon', '--no-parallel')
if ($RosettaRepository) {
    $arguments += "-ProsettaRepo=$RosettaRepository", "-ProsettaVersion=$RosettaVersion", "-ProsettaPort=$Port"
}
if ($ForgeVersion) { $arguments += "-Pforge_version=$ForgeVersion" }
$ErrorActionPreference = 'Continue'
& .\gradlew.bat @arguments *> "logs/$LogName.log"
exit $LASTEXITCODE
