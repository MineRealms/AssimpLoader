param([Parameter(Mandatory = $true)][string]$Destination)
$ErrorActionPreference = 'Stop'
$utf8 = [Text.UTF8Encoding]::new($false)
$shaderRoot = Join-Path $Destination 'shaders'
$settingsPath = Join-Path $shaderRoot 'lib\pipelineSettings.glsl'
$settings = [IO.File]::ReadAllText($settingsPath)
if ($settings.Contains('colortex12Format')) { throw 'Rocket material targets are already installed.' }
$settings += @'

// AssimpLoader local exhaust: actual base material and mapped normals.
/* Oculus render-target format directives.
const int colortex12Format = RGBA16F;
const int colortex13Format = RGBA16F;
*/
const bool colortex12Clear = true;
const bool colortex13Clear = true;
const vec4 colortex12ClearColor = vec4(0.0, 0.0, 0.0, 1.0);
const vec4 colortex13ClearColor = vec4(0.5, 0.5, 1.0, 0.0);
'@
[IO.File]::WriteAllText($settingsPath, $settings, $utf8)
$outputPattern = '(?s)/\* DRAWBUFFERS:06 \*/\s*gl_FragData\[0\] = color;\s*gl_FragData\[1\] = vec4\(smoothnessD, materialMask, skyLightFactor, 1.0\);\s*#if BLOCK_REFLECT_QUALITY[^\r\n]+\s*/\* DRAWBUFFERS:065 \*/\s*gl_FragData\[2\] = vec4\(mat3\(gbufferModelViewInverse\) \* normalM, 1.0\);\s*#endif'
$materialOutput = @'
/* RENDERTARGETS: 0,6,5,12,13 */
    gl_FragData[0] = color;
    gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0);
    vec3 rocketNormal = normalize(mat3(gbufferModelViewInverse) * normalM);
    gl_FragData[2] = vec4(rocketNormal, 1.0);
    float rocketMetal = 0.0;
    #ifdef CUSTOM_PBR
        rocketMetal = step(229.5 / 255.0, texture2D(specular, texCoord).g);
    #endif
    vec3 rocketAlbedo = pow(clamp(texture2D(tex, texCoord).rgb * glColor.rgb, vec3(0.0), vec3(1.0)), vec3(2.2));
    gl_FragData[3] = vec4(rocketAlbedo, clamp(1.0 - sqrt(max(smoothnessD, 0.0)), 0.045, 1.0));
    gl_FragData[4] = vec4(rocketNormal * 0.5 + 0.5, rocketMetal);
'@
foreach ($program in @('gbuffers_terrain','gbuffers_block','gbuffers_entities','gbuffers_hand')) {
    $path = Join-Path $shaderRoot "program\$program.glsl"
    $source = [IO.File]::ReadAllText($path)
    if ([regex]::Matches($source, $outputPattern).Count -ne 1) { throw "Unexpected $program material-output layout." }
    $source = [regex]::Replace($source, $outputPattern, $materialOutput)
    [IO.File]::WriteAllText($path, $source, $utf8)
}
Write-Output 'Installed local PBR material targets 12/13 for rocket lighting.'
