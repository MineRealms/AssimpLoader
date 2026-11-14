param(
    [Parameter(Mandatory = $true)][string]$BlenderPath,
    [Parameter(Mandatory = $true)][string]$SourceBlend,
    [string]$GameDirectory = (Join-Path $PSScriptRoot '../run'),
    [ValidateRange(0.01, 100.0)][double]$Scale = 0.25
)

$ErrorActionPreference = 'Stop'
$blender = (Resolve-Path -LiteralPath $BlenderPath).Path
$source = (Resolve-Path -LiteralPath $SourceBlend).Path
$game = [System.IO.Path]::GetFullPath($GameDirectory)
$pack = Join-Path $game 'resourcepacks/RocketEngine_Local_PBR'
$model = Join-Path $pack 'assets/assimploader/models/showcase/rocket_engine.fbx'
$preparation = Join-Path $PSScriptRoot 'prepare_rocket_showcase.py'
New-Item -ItemType Directory -Force -Path ([System.IO.Path]::GetDirectoryName($model)) | Out-Null
$scaleText = $Scale.ToString([System.Globalization.CultureInfo]::InvariantCulture)
& $blender --background --factory-startup --python-exit-code 1 --python $preparation -- --input-blend $source --output $model --scale $scaleText
if ($LASTEXITCODE -ne 0 -or !(Test-Path -LiteralPath $model)) {
    throw "Blender export failed with exit code $LASTEXITCODE"
}
$metadata = @{ pack = @{ pack_format = 15; description = 'Local rocket engine PBR asset for AssimpLoader' } }
$metadata | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $pack 'pack.mcmeta') -Encoding UTF8
Write-Output "Staged $model"
Write-Output 'Enable RocketEngine_Local_PBR in Minecraft resource packs, then reload resources (F3+T).'
