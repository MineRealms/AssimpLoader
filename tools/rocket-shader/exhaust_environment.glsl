#ifndef ASSIMP_EXHAUST_ENVIRONMENT
#define ASSIMP_EXHAUST_ENVIRONMENT
// Current pack shadow maps and celestial angle supply the external source term.
vec3 assimpExhaustIncident(vec3 position, vec3 ray) {
    #ifdef OVERWORLD
        vec3 sunDirection=normalize(mat3(gbufferModelViewInverse)*GetSunVector());
        float daylight=smoothstep(-.08,.12,sunDirection.y);
        vec3 lightDirection=sunDirection*((timeAngle<.5325 || timeAngle>.9675)?1.0:-1.0);
        vec3 shadowPosition=position+gbufferModelViewInverse[3].xyz;
        shadowPosition=mat3(shadowModelView)*shadowPosition+shadowModelView[3].xyz;
        shadowPosition=(shadowProjection*vec4(shadowPosition,1.0)).xyz;
        float distort=length(shadowPosition.xy)*shadowMapBias+(1.0-shadowMapBias);
        shadowPosition.xy/=distort;
        shadowPosition.z*=.2;
        shadowPosition=shadowPosition*.5+.5;
        float visibility=1.0;
        if(all(greaterThan(shadowPosition,vec3(.001))) && all(lessThan(shadowPosition,vec3(.999))))
            visibility=shadow2D(shadowtex0,shadowPosition-vec3(0,0,.0005)).r;
        float cosine=dot(-ray,lightDirection);
        float denominator=max(1.0+.35*.35-2.0*.35*cosine,.001);
        float phase=(1.0-.35*.35)/(12.5663706*denominator*sqrt(denominator));
        vec3 sky=mix(vec3(.014,.024,.05),vec3(.19,.26,.4),daylight);
        vec3 direct=mix(vec3(.12,.17,.28),vec3(4.0,3.65,3.1),daylight);
        return sky+direct*phase*visibility*(1.0-rainStrength*.7);
    #else
        return vec3(.04,.035,.05);
    #endif
}
#endif
