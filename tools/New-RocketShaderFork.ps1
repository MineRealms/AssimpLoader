[CmdletBinding()]
param(
    [string]$SourceZip,
    [string]$Destination
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if ([string]::IsNullOrWhiteSpace($SourceZip)) {
    $SourceZip = Join-Path $repoRoot 'run\shaderpacks\ComplementaryUnbound_r5.3.zip'
}
if ([string]::IsNullOrWhiteSpace($Destination)) {
    $Destination = Join-Path $repoRoot 'run\shaderpacks\ComplementaryRocket_Local'
}
$SourceZip = [IO.Path]::GetFullPath($SourceZip)
$Destination = [IO.Path]::GetFullPath($Destination)
$expectedHash = '66061B3C5B4843E31BC9A7562A7AC697A51BB77C73DEFC7071F996B783EFACCE'
if (!(Test-Path -LiteralPath $SourceZip -PathType Leaf)) {
    throw "Source ZIP does not exist: $SourceZip"
}
if (Test-Path -LiteralPath $Destination) {
    throw "Destination already exists. Choose a new local directory: $Destination"
}
if ((Get-FileHash -LiteralPath $SourceZip -Algorithm SHA256).Hash -ne $expectedHash) {
    throw 'Unsupported source ZIP. This patch is pinned to the inspected Complementary Unbound r5.3 archive.'
}

$templateRoot = Join-Path $PSScriptRoot 'rocket-shader'
$sharedSource = Join-Path $repoRoot 'src\main\resources\assets\assimploader\shaders\include\exhaust_volume.glsl'
$ownedFiles = @(
    $sharedSource,
    (Join-Path $templateRoot 'composite2.glsl'),
    (Join-Path $templateRoot 'temporal.glsl'),
    (Join-Path $templateRoot 'exhaust_environment.glsl'),
    (Join-Path $templateRoot 'exhaust_composite.glsl')
)
foreach ($ownedFile in $ownedFiles) {
    if (!(Test-Path -LiteralPath $ownedFile -PathType Leaf)) {
        throw "Required AssimpLoader shader source is missing: $ownedFile"
    }
}
$composite2Template = [IO.File]::ReadAllText((Join-Path $templateRoot 'composite2.glsl'))
$materialPatcher = Join-Path $PSScriptRoot 'Patch-RocketMaterials.ps1'
if (!(Test-Path -LiteralPath $materialPatcher -PathType Leaf)) {
    throw "Required local PBR G-buffer patch is missing: $materialPatcher"
}
foreach ($target in @('8', '9', '10')) {
    $clearPattern = 'const\s+vec4\s+colortex' + $target + 'ClearColor\s*=\s*vec4\(([^)]+)\)\s*;'
    $clearMatch = [regex]::Match($composite2Template, $clearPattern)
    if (!$clearMatch.Success -or $clearMatch.Groups[1].Value.Split(',').Count -ne 4) {
        throw "Oculus requires four literal floats in colortex${target}ClearColor."
    }
    foreach ($component in $clearMatch.Groups[1].Value.Split(',')) {
        $null = [single]::Parse($component.Trim(), [Globalization.CultureInfo]::InvariantCulture)
    }
    if (!$composite2Template.Contains("const int colortex${target}Format = RGBA16F;") -or
        !$composite2Template.Contains("const bool colortex${target}Clear = false;")) {
        throw "Expected explicit HDR format and persistent history directives for target $target."
    }
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($SourceZip)
$utf8 = [Text.UTF8Encoding]::new($false)
function Read-ZipText([string]$EntryName) {
    $entry = $archive.GetEntry($EntryName)
    if ($null -eq $entry) { throw "Required source ZIP entry is missing: $EntryName" }
    $reader = [IO.StreamReader]::new($entry.Open(), [Text.Encoding]::UTF8)
    try { return $reader.ReadToEnd().Replace("`r`n", "`n").Replace("`r", '') }
    finally { $reader.Dispose() }
}
function Replace-ExactlyOnce([string]$Text, [string]$Needle, [string]$Replacement, [string]$Label) {
    $position = $Text.IndexOf($Needle, [StringComparison]::Ordinal)
    if ($position -lt 0 -or $Text.IndexOf($Needle, $position + $Needle.Length, [StringComparison]::Ordinal) -ge 0) {
        throw "Expected exactly one patch anchor in $Label"
    }
    return $Text.Substring(0, $position) + $Replacement + $Text.Substring($position + $Needle.Length)
}

try {
    $license = Read-ZipText 'License.txt'
    if (!$license.StartsWith('Complementary License Agreement 1.5')) {
        throw 'The expected r5.3 license was not found.'
    }
    $versions = Read-ZipText 'shaderFile_Versions.txt'
    if (!$versions.Contains('ComplementaryUnbound_r5.3.zip')) {
        throw 'The expected Unbound r5.3 version marker was not found.'
    }
    $composite3 = Read-ZipText 'shaders/program/composite3.glsl'
    $composite6 = Read-ZipText 'shaders/program/composite6.glsl'
    $taa = Read-ZipText 'shaders/lib/antialiasing/taa.glsl'
    $properties = Read-ZipText 'shaders/shaders.properties'
    $null = Read-ZipText 'shaders/program/composite2.glsl'
    $null = Read-ZipText 'shaders/program/composite4.glsl'
    $null = Read-ZipText 'shaders/program/composite5.glsl'
    foreach ($dimension in @('world0', 'world-1', 'world1')) {
        foreach ($extension in @('vsh', 'fsh')) {
            if ($null -ne $archive.GetEntry("shaders/$dimension/composite1.$extension")) {
                throw "Expected unused composite1 slot: $dimension/composite1.$extension"
            }
            $wrapper = Read-ZipText "shaders/$dimension/composite2.$extension"
            if (!$wrapper.Contains('#include "/program/composite2.glsl"')) {
                throw "Unexpected composite2 wrapper: $dimension/composite2.$extension"
            }
        }
    }

    foreach ($dimension in @('world0', 'world-1', 'world1')) {
        $original = "program.$dimension/composite2.enabled=MOTION_BLURRING"
        if (!$properties.Contains($original)) {
            throw "Expected original motion-blur gate: $original"
        }
    }
    $properties += @'

# AssimpLoader local rocket demo: actual target sizes, not viewport-only scale.
size.buffer.colortex8=0.5 0.5
size.buffer.colortex9=0.5 0.5
size.buffer.colortex10=0.5 0.5

'@
    $composite3 = Replace-ExactlyOnce $composite3 '#ifdef FRAGMENT_SHADER' @'
#ifdef FRAGMENT_SHADER
#include "/lib/assimploader/exhaust_composite.glsl"
'@ 'composite3.glsl include'
    $composite3 = Replace-ExactlyOnce $composite3 '    /* DRAWBUFFERS:0 */' @'
    assimpCompositeExhaust(color);

    /* DRAWBUFFERS:0 */
'@ 'composite3.glsl output'
    $composite6 = Replace-ExactlyOnce $composite6 '#ifdef FRAGMENT_SHADER' @'
#ifdef FRAGMENT_SHADER
#define ASSIMP_EXHAUST_SCENE_REACTIVE
#include "/lib/assimploader/temporal.glsl"
'@ 'composite6.glsl reactive include'
    $taa = Replace-ExactlyOnce $taa 'void DoTAA(inout vec3 color, inout vec3 temp, float z1) {' @'
void DoTAA(inout vec3 color, inout vec3 temp, float z1) {
    if (assimpExhaustSceneReactive(texCoord)) {
        temp = color;
        return;
    }
'@ 'taa.glsl reactive mask'

    # Check every resolved extraction path before creating any directory.
    $destinationPrefix = $Destination.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
    foreach ($entry in $archive.Entries) {
        $relative = $entry.FullName.Replace('/', [IO.Path]::DirectorySeparatorChar)
        $resolved = [IO.Path]::GetFullPath((Join-Path $Destination $relative))
        if (!$resolved.StartsWith($destinationPrefix, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Source ZIP entry escapes the destination: $($entry.FullName)"
        }
    }
    $null = New-Item -ItemType Directory -Path $Destination
    foreach ($entry in $archive.Entries) {
        $relative = $entry.FullName.Replace('/', [IO.Path]::DirectorySeparatorChar)
        $resolved = [IO.Path]::GetFullPath((Join-Path $Destination $relative))
        if ($entry.FullName.EndsWith('/')) {
            $null = New-Item -ItemType Directory -Path $resolved -Force
        } else {
            $null = New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($resolved)) -Force
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $resolved, $false)
        }
    }

    $includeRoot = Join-Path $Destination 'shaders\lib\assimploader'
    $null = New-Item -ItemType Directory -Path $includeRoot -Force
    Copy-Item -LiteralPath $sharedSource -Destination (Join-Path $includeRoot 'exhaust_volume.glsl')
    Copy-Item -LiteralPath (Join-Path $templateRoot 'exhaust_environment.glsl') -Destination (Join-Path $includeRoot 'exhaust_environment.glsl')
    Copy-Item -LiteralPath (Join-Path $templateRoot 'temporal.glsl') -Destination (Join-Path $includeRoot 'temporal.glsl')
    Copy-Item -LiteralPath (Join-Path $templateRoot 'exhaust_composite.glsl') -Destination (Join-Path $includeRoot 'exhaust_composite.glsl')
    Copy-Item -LiteralPath (Join-Path $templateRoot 'composite2.glsl') -Destination (Join-Path $Destination 'shaders\program\assimploader_exhaust.glsl')
    foreach ($dimension in @('world0', 'world-1', 'world1')) {
        $dimensionDefine = switch ($dimension) {
            'world0' { 'OVERWORLD' }
            'world-1' { 'NETHER' }
            'world1' { 'END' }
        }
        foreach ($extension in @('vsh', 'fsh')) {
            $stageDefine = if ($extension -eq 'vsh') { 'VERTEX_SHADER' } else { 'FRAGMENT_SHADER' }
            $exhaustWrapper = @"
#version 130

#define $stageDefine
#define $dimensionDefine
#define COMPOSITE1

#include "/program/assimploader_exhaust.glsl"
"@
            [IO.File]::WriteAllText((Join-Path $Destination "shaders\$dimension\composite1.$extension"), $exhaustWrapper, $utf8)
        }
    }
    [IO.File]::WriteAllText((Join-Path $Destination 'shaders\program\composite3.glsl'), $composite3, $utf8)
    [IO.File]::WriteAllText((Join-Path $Destination 'shaders\program\composite6.glsl'), $composite6, $utf8)
    [IO.File]::WriteAllText((Join-Path $Destination 'shaders\lib\antialiasing\taa.glsl'), $taa, $utf8)
    [IO.File]::WriteAllText((Join-Path $Destination 'shaders\shaders.properties'), $properties, $utf8)
    $notice = @'
AssimpLoader Local Rocket Shader Experiment
==========================================
Based on Complementary Unbound r5.3 by EminGT / Complementary Development.
Original project: https://www.complementary.dev/
Original License.txt and credits are preserved without modification.

This local directory adds a half-resolution HDR volume pass in the previously unused
composite1 slot. Dedicated radiance/transmittance, depth and source history are
reprojected before composite3, bloom and tonemapping. The original composite2 scene
motion blur and its options are preserved. Scene TAA treats exhaust pixels as reactive.
The original ZIP is unchanged. Select the ZIP again to return to the original pack.

Temporal history rejects source topology/profile jumps, camera cuts, resize,
disocclusion, large motion and large radiance changes. Overlapping media use current
samples because one depth moment cannot track several moving density fields.
Heat distortion is screen-space refraction, and the exhaust field is a reduced
physical model rather than CFD. The original motion blur does not have velocity
vectors for exhaust; the static stand uses its dedicated volume history.

This generated directory is not a redistribution package. The bundled license
restricts modifications/redistribution; a rocket-only change does not establish
the required visual difference in common daytime gameplay. Obtain author permission
before distributing this experiment or any original pack sources.
'@
    [IO.File]::WriteAllText((Join-Path $Destination 'ASSIMPLOADER_LOCAL_EXPERIMENT.txt'), $notice, $utf8)
}
finally {
    $archive.Dispose()
}

& $materialPatcher -Destination $Destination

if ((Get-FileHash -LiteralPath $SourceZip -Algorithm SHA256).Hash -ne $expectedHash) {
    throw 'Unexpected source ZIP change detected.'
}
Write-Output "Created local shader directory: $Destination"
Write-Output 'Original ZIP is unchanged. Shader selection has not been changed.'
