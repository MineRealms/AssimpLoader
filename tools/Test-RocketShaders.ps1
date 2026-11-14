param([string]$Pack, [string]$Validator = "$env:ANDROID_HOME\emulator\lib64\vulkan\glslangValidator.exe")
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (!$Pack) { $Pack = Join-Path $repoRoot 'run\shaderpacks\RocketFlow_Local' }
$shaderRoot = Join-Path $Pack 'shaders'
$output = Join-Path $repoRoot 'build\rocket-shader-validation'
$null = New-Item -ItemType Directory -Path $output -Force
$utf8 = [Text.UTF8Encoding]::new($false)
if (!(Test-Path -LiteralPath $Validator)) { throw "Validator not found: $Validator" }
function Expand-Includes([string]$ShaderPath, [int]$Depth = 0) {
    if ($Depth -gt 32) { throw "Shader include depth: $ShaderPath" }
    $source = [IO.File]::ReadAllText($ShaderPath)
    $directory = [IO.Path]::GetDirectoryName($ShaderPath)
    $replace = [Text.RegularExpressions.MatchEvaluator] {
        param($match)
        $include = $match.Groups[1].Value
        $path = if ($include.StartsWith('/')) { Join-Path $shaderRoot $include.TrimStart('/') } else { Join-Path $directory $include }
        return Expand-Includes $path ($Depth + 1)
    }
    return [regex]::Replace($source, '(?m)^\s*#include\s+"([^"]+)"\s*$', $replace)
}
foreach ($dimension in @('world0','world-1','world1')) {
    foreach ($stage in @('fsh','vsh')) {
        $path = Join-Path $shaderRoot "$dimension\composite1.$stage"
        $source = Expand-Includes $path
        $source = $source.Replace('#version 130', "#version 130`n#extension GL_ARB_shading_language_420pack : enable`n#define IS_IRIS`n#define MC_VERSION 12001")
        $expanded = Join-Path $output "$dimension-volume.$stage"
        [IO.File]::WriteAllText($expanded, $source, $utf8)
        $kind = if ($stage -eq 'fsh') { 'frag' } else { 'vert' }
        & $Validator -S $kind $expanded
        if ($LASTEXITCODE -ne 0) { throw "GLSL validation failed: $expanded" }
    }
}
foreach ($program in @('composite3','gbuffers_terrain','gbuffers_block','gbuffers_entities','gbuffers_hand')) {
    $source = Expand-Includes (Join-Path $shaderRoot "world0\$program.fsh")
    $source = $source.Replace('#version 130', "#version 130`n#extension GL_ARB_shading_language_420pack : enable`n#define IS_IRIS`n#define MC_VERSION 12001")
    $expanded = Join-Path $output "$program.fsh"
    [IO.File]::WriteAllText($expanded, $source, $utf8)
    & $Validator -S frag $expanded
    if ($LASTEXITCODE -ne 0) { throw "GLSL validation failed: $expanded" }
}
$coreRoot = Join-Path $repoRoot 'src\main\resources\assets\assimploader\shaders'
$core = [IO.File]::ReadAllText((Join-Path $coreRoot 'core\exhaust_volume.fsh'))
$core = $core.Replace('// ASSIMP_EXHAUST_INCLUDE', [IO.File]::ReadAllText((Join-Path $coreRoot 'include\exhaust_volume.glsl')))
$expanded = Join-Path $output 'vanilla-volume.fsh'
[IO.File]::WriteAllText($expanded, $core, $utf8)
& $Validator -S frag $expanded
if ($LASTEXITCODE -ne 0) { throw 'Vanilla GLSL validation failed.' }
Write-Output 'Validated 12 expanded rocket shader variants.'
