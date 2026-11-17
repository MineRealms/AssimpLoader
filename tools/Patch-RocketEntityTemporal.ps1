[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$Destination)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$shaderRoot = Join-Path ([IO.Path]::GetFullPath($Destination)) 'shaders'
$utf8 = [Text.UTF8Encoding]::new($false)
$files = [ordered]@{}
foreach ($relative in @('lib\pipelineSettings.glsl', 'lib\antialiasing\taa.glsl',
        'program\gbuffers_entities.glsl', 'program\gbuffers_hand.glsl',
        'program\deferred1.glsl', 'shaders.properties', 'program\composite6.glsl')) {
    $files[$relative] = [IO.File]::ReadAllText((Join-Path $shaderRoot $relative)).Replace("`r`n", "`n")
}
$marker = 'ASSIMP_ENTITY_TEMPORAL'
$versionMarker = 'ASSIMP_ENTITY_TEMPORAL_V2'
$template = Join-Path $PSScriptRoot 'rocket-shader\entity_temporal.glsl'
if (!(Test-Path -LiteralPath $template -PathType Leaf)) { throw "Missing template: $template" }

function Replace-Once([string]$Text, [string]$Needle, [string]$Replacement, [string]$Label) {
    $position = $Text.IndexOf($Needle, [StringComparison]::Ordinal)
    if ($position -lt 0 -or $Text.IndexOf($Needle, $position + $Needle.Length, [StringComparison]::Ordinal) -ge 0) {
        throw "Expected exactly one entity temporal patch anchor: $Label"
    }
    return $Text.Substring(0, $position) + $Replacement + $Text.Substring($position + $Needle.Length)
}

if (!$files['lib\antialiasing\taa.glsl'].Contains($marker)) {
    $relative = 'lib\pipelineSettings.glsl'
    if ($files[$relative].Contains('colortex6ClearColor')) { throw 'colortex6 already has an explicit clear color.' }
    $files[$relative] = Replace-Once $files[$relative] 'const bool colortex6Clear = true;' @'
const bool colortex6Clear = true;
// ASSIMP_ENTITY_TEMPORAL: alpha is 0 for entities/hand, 1 for the static scene.
const vec4 colortex6ClearColor = vec4(0.0, 0.0, 0.0, 1.0);
'@ $relative

    foreach ($relative in @('program\gbuffers_entities.glsl', 'program\gbuffers_hand.glsl')) {
        $files[$relative] = Replace-Once $files[$relative] 'gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0);' @'
// ASSIMP_ENTITY_TEMPORAL: keep LabPBR material RGB independent of temporal eligibility.
    gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 0.0);
'@ $relative
    }

    $relative = 'program\deferred1.glsl'
    $files[$relative] = Replace-Once $files[$relative] @'
        vec3 texture6 = texelFetch(colortex6, texelCoord, 0).rgb;
        bool entityOrHand = z0 < 0.56;
'@ @'
        // ASSIMP_ENTITY_TEMPORAL: custom rigs are entities at any camera distance.
        vec4 materialData = texelFetch(colortex6, texelCoord, 0);
        vec3 texture6 = materialData.rgb;
        bool entityOrHand = z0 < 0.56 || materialData.a < 0.5;
'@ $relative

    $relative = 'lib\antialiasing\taa.glsl'
    $files[$relative] = Replace-Once $files[$relative] 'void DoTAA(inout vec3 color, inout vec3 temp, float z1) {' @'
// ASSIMP_ENTITY_TEMPORAL
#include "/lib/assimploader/entity_temporal.glsl"

void DoTAA(inout vec3 color, inout vec3 temp, float z1) {
    if (assimpEntityReactive(texelCoord)) {
        // Zero is the pack's invalid-history sentinel, including on the next frame.
        temp = vec3(0.0);
        return;
    }
'@ $relative

    $files['shaders.properties'] += @'

# ASSIMP_ENTITY_TEMPORAL: metadata must overwrite, even for alpha-blended eyes.
# Color blending on colortex0 and PBR material values remain unchanged.
blend.gbuffers_entities.colortex6=off
blend.gbuffers_entities_translucent.colortex6=off
blend.gbuffers_entities_glowing.colortex6=off
blend.gbuffers_hand.colortex6=off

'@
} else {
    foreach ($relative in @('lib\pipelineSettings.glsl', 'program\gbuffers_entities.glsl',
            'program\gbuffers_hand.glsl', 'program\deferred1.glsl', 'shaders.properties')) {
        if (!$files[$relative].Contains($marker)) { throw "Incomplete entity temporal patch: $relative" }
    }
}

if (!$files['lib\antialiasing\taa.glsl'].Contains($versionMarker)) {
    $relative = 'lib\pipelineSettings.glsl'
    if ($files[$relative].Contains('colortex2ClearColor')) { throw 'colortex2 already has an explicit clear color.' }
    $files[$relative] = Replace-Once $files[$relative] 'const int colortex2Format = RGB16F;' 'const int colortex2Format = RGBA16F;' "$relative history format"
    $files[$relative] = Replace-Once $files[$relative] 'const bool colortex2Clear = false;' @'
const bool colortex2Clear = false;
// ASSIMP_ENTITY_TEMPORAL_V2: cleared only on allocation/reload, retained between frames.
const vec4 colortex2ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
'@ "$relative history clear"

    $relative = 'lib\antialiasing\taa.glsl'
    $files[$relative] = Replace-Once $files[$relative] @'
// ASSIMP_ENTITY_TEMPORAL
#include "/lib/assimploader/entity_temporal.glsl"

void DoTAA(inout vec3 color, inout vec3 temp, float z1) {
    if (assimpEntityReactive(texelCoord)) {
        // Zero is the pack's invalid-history sentinel, including on the next frame.
        temp = vec3(0.0);
        return;
    }
'@ @'
// ASSIMP_ENTITY_TEMPORAL_V2: history RGB always remains the current scene color.
#include "/lib/assimploader/entity_temporal.glsl"

void DoTAA(inout vec3 color, inout vec3 temp, float z1, inout float historyValidity) {
    temp = color;
    historyValidity = 0.0;
    if (assimpEntityReactive(texelCoord)) return;
'@ "$relative history output"
    $files[$relative] = Replace-Once $files[$relative] @'
    vec3 tempColor = texture2D(colortex2, prvCoord).rgb;
    if (tempColor == vec3(0.0)) { // Fixes the first frame || Possibly fixes nans spreading around
        temp = color;
        return;
    }
'@ @'
    historyValidity = 1.0;
    if (!assimpEntityHistoryValid(prvCoord)) return;
    vec3 tempColor = texture2D(colortex2, prvCoord).rgb;
    if (any(isnan(tempColor)) || any(isinf(tempColor))) return;
'@ "$relative history sample"

    $relative = 'program\composite6.glsl'
    $files[$relative] = Replace-Once $files[$relative] '    vec3 temp = vec3(0.0);' @'
    vec3 temp = color;
    // ASSIMP_ENTITY_TEMPORAL_V2: alpha marks reusable static-scene history.
    float historyValidity = 0.0;
'@ "$relative history initialization"
    $files[$relative] = Replace-Once $files[$relative] 'DoTAA(color, temp, z1);' 'DoTAA(color, temp, z1, historyValidity);' "$relative history call"
    $files[$relative] = Replace-Once $files[$relative] 'gl_FragData[1] = vec4(temp, 1.0);' 'gl_FragData[1] = vec4(temp, historyValidity);' "$relative history attachment"
}

$relative = 'lib\pipelineSettings.glsl'
if ($files[$relative].Contains('const vec4 colortex2ClearColor = vec4(0.0);')) {
    $files[$relative] = Replace-Once $files[$relative] 'const vec4 colortex2ClearColor = vec4(0.0);' 'const vec4 colortex2ClearColor = vec4(0.0, 0.0, 0.0, 0.0);' "$relative literal clear color"
}

foreach ($relative in @('lib\pipelineSettings.glsl', 'lib\antialiasing\taa.glsl', 'program\composite6.glsl')) {
    if (!$files[$relative].Contains($versionMarker)) { throw "Incomplete entity temporal V2 patch: $relative" }
}
foreach ($requirement in @(
        @('lib\pipelineSettings.glsl', 'const int colortex2Format = RGBA16F;'),
        @('lib\pipelineSettings.glsl', 'const bool colortex2Clear = false;'),
        @('lib\pipelineSettings.glsl', 'const vec4 colortex2ClearColor = vec4(0.0, 0.0, 0.0, 0.0);'),
        @('lib\antialiasing\taa.glsl', 'if (!assimpEntityHistoryValid(prvCoord)) return;'),
        @('program\composite6.glsl', 'DoTAA(color, temp, z1, historyValidity);'),
        @('program\composite6.glsl', 'gl_FragData[1] = vec4(temp, historyValidity);'))) {
    if (!$files[$requirement[0]].Contains($requirement[1])) { throw "Incomplete entity temporal V2 contract: $($requirement[0])" }
}

$include = Join-Path $shaderRoot 'lib\assimploader\entity_temporal.glsl'
$null = New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($include)) -Force
Copy-Item -LiteralPath $template -Destination $include
foreach ($relative in $files.Keys) {
    [IO.File]::WriteAllText((Join-Path $shaderRoot $relative), $files[$relative], $utf8)
}
Write-Output 'Installed entity temporal V2 with explicit RGBA history validity and preserved LabPBR channels.'
