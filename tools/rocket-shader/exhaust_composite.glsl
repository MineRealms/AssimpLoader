#ifndef ASSIMP_EXHAUST_COMPOSITE_GLSL
#define ASSIMP_EXHAUST_COMPOSITE_GLSL
#include "/lib/assimploader/exhaust_volume.glsl"
uniform sampler2D colortex12;
uniform sampler2D colortex13;

vec3 assimpExhaustReconstruct(vec2 uv,float depth) {
    vec4 p=gbufferProjectionInverse*vec4(uv*2.0-1.0,depth*2.0-1.0,1.0);
    return p.xyz/p.w;
}

// Cook-Torrance GGX, Schlick Fresnel and energy-conserving diffuse on real G-buffer material.
vec3 assimpExhaustBRDF(vec3 n,vec3 v,vec3 l,vec3 albedo,float roughness,float metal) {
    vec3 h=normalize(v+l);
    float nv=max(dot(n,v),.001), nl=max(dot(n,l),0.0), nh=max(dot(n,h),0.0), vh=max(dot(v,h),0.0);
    float a=roughness*roughness, a2=a*a;
    float d=a2/(ASSIMP_EXHAUST_PI*pow(nh*nh*(a2-1.0)+1.0,2.0)+.00001);
    float k=pow(roughness+1.0,2.0)/8.0;
    float g=nv/(nv*(1.0-k)+k)*nl/(nl*(1.0-k)+k);
    vec3 f0=mix(vec3(.04),albedo,metal);
    vec3 fresnel=f0+(1.0-f0)*pow(1.0-vh,5.0);
    vec3 specularBRDF=d*g*fresnel/max(4.0*nv*nl,.001);
    vec3 diffuse=(1.0-fresnel)*(1.0-metal)*albedo/ASSIMP_EXHAUST_PI;
    return (diffuse+specularBRDF)*nl;
}

vec3 assimpExhaustSurfaceLight(vec2 uv,vec3 viewPosition) {
    vec3 worldPosition=mat3(gbufferModelViewInverse)*viewPosition;
    vec4 material=texture2D(colortex12,uv), normalMetal=texture2D(colortex13,uv);
    vec3 normal=normalMetal.xyz*2.0-1.0;
    if(dot(normal,normal)<.1) return vec3(0.0);
    normal=normalize(normal);
    vec3 v=normalize(-worldPosition), result=vec3(0.0);
    for(int i=0;i<ASSIMP_EXHAUST_MAX;i++) {
        if(i>=assimpExhaustCount) break;
        vec4 nozzle=assimpExhaustOriginRadius[i], plume=assimpExhaustAxisLength[i];
        vec3 local=worldPosition-nozzle.xyz;
        float axial=dot(local,plume.xyz), radial=length(local-plume.xyz*axial);
        // Visible nozzle metal only: the bell is above the exit, where the gas has heated the wall.
        float wallMask=(1.0-smoothstep(nozzle.w*.12,nozzle.w*.3,abs(radial-nozzle.w)));
        wallMask*=smoothstep(-2.2*nozzle.w,-1.6*nozzle.w,axial)*(1.0-smoothstep(-.1,.08,axial));
        float wallT=assimpExhaustThermal[i].x*(1.0-.12*clamp(-axial/nozzle.w,0.0,2.0));
        result+=assimpExhaustBlackbody(wallT)*wallMask*45.0;
        float radius=assimpExhaustSmoke[i].z, intensity=assimpExhaustSmoke[i].w;
        if(radius<=0.0 || intensity<=0.0) continue;
        // Three quadrature samples approximate an extended luminous jet instead of a point halo.
        for(int s=0;s<3;s++) {
            float along=(float(s)+.5)/3.0*min(plume.w*.65,7.0);
            vec3 center=nozzle.xyz+plume.xyz*along;
            vec3 toLight=center-worldPosition;
            float d2=dot(toLight,toLight), distanceLight=sqrt(d2);
            if(distanceLight>=radius) continue;
            vec3 l=toLight/max(distanceLight,.001);
            if(dot(normal,l)<=0.0) continue;
            float visibility=assimpExhaustVisibility(worldPosition+normal*.55,center);
            float falloff=pow(max(1.0-pow(distanceLight/radius,4.0),0.0),2.0)/(d2+nozzle.w*nozzle.w);
            vec3 brdf=assimpExhaustBRDF(normal,v,l,material.rgb,material.a,normalMetal.a);
            result+=brdf*assimpExhaustColorShock[i].rgb*intensity*falloff*visibility*1.5;
        }
    }
    return result;
}

void assimpCompositeExhaust(inout vec3 color) {
    if(assimpExhaustCount<=0 && assimpExhaustPuffCount<=0) return;
    vec2 uv=gl_FragCoord.xy/vec2(viewWidth,viewHeight);
    float depth=texture2D(depthtex0,uv).r;
    vec3 viewPosition=assimpExhaustReconstruct(uv,depth);
    float sceneDistance=length(viewPosition);
    ivec2 halfSize=textureSize(colortex8,0);
    vec2 hp=uv*vec2(halfSize)-.5, f=fract(hp);
    ivec2 base=ivec2(floor(hp));
    vec4 volume=vec4(0.0), metadata=vec4(0.0);
    float totalWeight=0.0;
    for(int y=0;y<2;y++) for(int x=0;x<2;x++) {
        ivec2 p=clamp(base+ivec2(x,y),ivec2(0),halfSize-1);
        vec4 meta=texelFetch(colortex9,p,0);
        float spatial=(x==0?1.0-f.x:f.x)*(y==0?1.0-f.y:f.y);
        float scale=max(.08,sceneDistance*.015);
        float weight=spatial*exp(-abs(meta.z-sceneDistance)/scale);
        if(meta.w>sceneDistance+scale) weight*=.02;
        volume+=texelFetch(colortex8,p,0)*weight; metadata+=meta*weight; totalWeight+=weight;
    }
    if(totalWeight>.0001) { volume/=totalWeight; metadata/=totalWeight; }
    else { volume=vec4(0,0,0,1); metadata=vec4(0.0); }
    // Depth-limited refraction cannot pull foreground silhouettes into hot background air.
    if(dot(metadata.xy,metadata.xy)>.0000000001) {
        vec2 displaced=clamp(uv+metadata.xy,vec2(.001),vec2(.999));
        float displacedDepth=texture2D(depthtex0,displaced).r;
        vec3 displacedPosition=assimpExhaustReconstruct(displaced,displacedDepth);
        if(abs(length(displacedPosition)-sceneDistance)<max(.15,sceneDistance*.025))
            color+=texture2D(colortex0,displaced).rgb-texture2D(colortex0,uv).rgb;
    }
    if(depth<.999999 && depth>.56) color+=assimpExhaustSurfaceLight(uv,viewPosition);
    color=color*clamp(volume.a,0.0,1.0)+max(volume.rgb,vec3(0.0));
}
#endif
