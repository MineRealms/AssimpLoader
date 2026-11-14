#ifndef ASSIMP_EXHAUST_VOLUME_GLSL
#define ASSIMP_EXHAUST_VOLUME_GLSL

// Reduced-order gas transport in camera-relative world coordinates, SI gas inputs.
// Radiance/exposure and unresolved mixing remain calibrated visual parameters, not CFD.
uniform int assimpExhaustCount;
uniform float assimpExhaustTime;
uniform vec4 assimpExhaustOriginRadius[16];
uniform vec4 assimpExhaustAxisLength[16];
uniform vec4 assimpExhaustShape[16];
uniform vec4 assimpExhaustOptics[16];
uniform vec4 assimpExhaustColorShock[16];
uniform vec4 assimpExhaustSmoke[16];
uniform vec4 assimpExhaustFlow[16];
uniform vec4 assimpExhaustThermal[16];
uniform vec4 assimpExhaustTransport[16];
uniform vec4 assimpExhaustGas[16];
uniform int assimpExhaustPuffCount;
uniform vec4 assimpExhaustPuffOriginRadius[24];
uniform vec4 assimpExhaustPuffOptics[24];
uniform vec4 assimpExhaustPuffMotion[24];
uniform sampler3D assimpExhaustOcclusion;
uniform vec4 assimpExhaustOcclusionOrigin;
uniform int assimpExhaustOcclusionValid;

const int ASSIMP_EXHAUST_MAX = 16;
const int ASSIMP_EXHAUST_STEPS = 64;
const int ASSIMP_EXHAUST_INTERVALS = 40;
const float ASSIMP_EXHAUST_PI = 3.14159265359;

vec3 assimpExhaustHeatVector;
float assimpExhaustMeanDistance;
int assimpExhaustDominantEmitter = -1;
#ifndef ASSIMP_EXHAUST_STEP_BUDGET
    #define ASSIMP_EXHAUST_STEP_BUDGET 48
#endif

float assimpExhaustHash(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float assimpExhaustNoise(vec3 p) {
    vec3 cell = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = mix(assimpExhaustHash(cell), assimpExhaustHash(cell + vec3(1,0,0)), f.x);
    float b = mix(assimpExhaustHash(cell + vec3(0,1,0)), assimpExhaustHash(cell + vec3(1,1,0)), f.x);
    float c = mix(assimpExhaustHash(cell + vec3(0,0,1)), assimpExhaustHash(cell + vec3(1,0,1)), f.x);
    float d = mix(assimpExhaustHash(cell + vec3(0,1,1)), assimpExhaustHash(cell + vec3(1,1,1)), f.x);
    return mix(mix(a,b,f.y), mix(c,d,f.y), f.z);
}

float assimpExhaustFbm(vec3 p) {
    return 0.57 * assimpExhaustNoise(p) + 0.29 * assimpExhaustNoise(p*2.03+17.1)
         + 0.14 * assimpExhaustNoise(p*4.11-8.2);
}

// Smooth cellular ridges make coherent eddies in the mixed outer gas.
float assimpExhaustCellular(vec3 p) {
    vec3 cell = floor(p), f = fract(p);
    float closest = 4.0;
    for (int z=0; z<2; z++) for (int y=0; y<2; y++) for (int x=0; x<2; x++) {
        vec3 corner = vec3(x,y,z);
        float h = assimpExhaustHash(cell+corner);
        vec3 point = corner + (vec3(h, fract(h*19.7), fract(h*7.3))-0.5)*0.75;
        closest = min(closest, dot(f-point,f-point));
    }
    return clamp(1.0-sqrt(closest),0.0,1.0);
}

// Three spectral samples of Planck's law; normalized at 2400K, then display-calibrated.
// Blue gas chemiluminescence is added separately; it is not a black-body temperature claim.
vec3 assimpExhaustBlackbody(float temperature) {
    float t = clamp(temperature,300.0,8000.0);
    vec3 lambda = vec3(0.65,0.55,0.46);
    vec3 exponent = clamp(vec3(14387.77)/(lambda*t),vec3(0.0),vec3(70.0));
    return 1100.0 / (pow(lambda,vec3(5.0)) * (exp(exponent)-vec3(1.0)));
}

float assimpExhaustPhase(float cosine, float g) {
    float d = max(1.0+g*g-2.0*g*cosine,0.001);
    return (1.0-g*g)/(4.0*ASSIMP_EXHAUST_PI*d*sqrt(d));
}

float assimpExhaustSolid(vec3 p) {
    if (assimpExhaustOcclusionValid == 0) return 0.0;
    vec3 uvw = (p-assimpExhaustOcclusionOrigin.xyz)/(64.0*assimpExhaustOcclusionOrigin.w);
    if (any(lessThan(uvw,vec3(0.0))) || any(greaterThanEqual(uvw,vec3(1.0)))) return 0.0;
    return texture(assimpExhaustOcclusion,uvw).r;
}

float assimpExhaustVisibility(vec3 from, vec3 to) {
    vec3 delta = to-from;
    float distanceToLight = length(delta);
    int steps = int(clamp(ceil(distanceToLight/0.42),1.0,48.0));
    vec3 stepVector = delta/float(steps);
    for (int i=1;i<49;i++) {
        if (i>=steps) break;
        if (assimpExhaustSolid(from+stepVector*float(i))>0.5) return 0.0;
    }
    return 1.0;
}

#ifndef ASSIMP_EXHAUST_ENVIRONMENT
vec3 assimpExhaustIncident(vec3 position, vec3 ray) { return vec3(0.05,0.065,0.09); }
#endif

vec3 assimpExhaustLocalLight(vec3 position, vec3 ray) {
    vec3 lighting = vec3(0.0);
    for (int i=0;i<ASSIMP_EXHAUST_MAX;i++) {
        if (i>=assimpExhaustCount) break;
        vec3 center = assimpExhaustOriginRadius[i].xyz + assimpExhaustAxisLength[i].xyz * min(2.2,assimpExhaustAxisLength[i].w*0.25);
        vec3 delta = center-position;
        float d2 = dot(delta,delta);
        float radius = assimpExhaustSmoke[i].z;
        if (d2>radius*radius || assimpExhaustSmoke[i].w<=0.0) continue;
        float phase = assimpExhaustPhase(dot(normalize(-ray),normalize(delta+vec3(0.00001))),0.3);
        float falloff = pow(max(1.0-sqrt(d2)/max(radius,0.01),0.0),2.0)/(1.0+d2*0.12);
        float visibility=assimpExhaustVisibility(position,center);
        lighting += assimpExhaustColorShock[i].rgb * assimpExhaustSmoke[i].w * falloff * phase * visibility * 8.0;
    }
    return lighting;
}

bool assimpExhaustInterval(int emitter, vec3 ray, float sceneDistance, out vec2 interval) {
    vec4 origin = assimpExhaustOriginRadius[emitter];
    vec4 axisLength = assimpExhaustAxisLength[emitter];
    float lengthJet = max(axisLength.w,0.001);
    vec3 axis = axisLength.xyz;
    float radius = max(origin.w,origin.w+assimpExhaustShape[emitter].x*lengthJet)*2.5;
    vec3 relative = -origin.xyz;
    float oz = dot(relative,axis), dz = dot(ray,axis);
    vec3 ro = relative-axis*oz, rd = ray-axis*dz;
    float a = dot(rd,rd), b = dot(ro,rd), c = dot(ro,ro)-radius*radius;
    float entry=0.0, exitDistance=sceneDistance;
    if (a<0.000001) { if (c>0.0) return false; }
    else {
        float d=b*b-a*c;
        if (d<0.0) return false;
        float root=sqrt(d);
        entry=max(entry,(-b-root)/a); exitDistance=min(exitDistance,(-b+root)/a);
    }
    if (abs(dz)<0.000001) { if (oz<0.0 || oz>lengthJet) return false; }
    else {
        float a0=-oz/dz, a1=(lengthJet-oz)/dz;
        entry=max(entry,min(a0,a1)); exitDistance=min(exitDistance,max(a0,a1));
    }
    interval=vec2(entry,exitDistance);
    return exitDistance>entry;
}

bool assimpExhaustPuffInterval(int puff, vec3 ray, float sceneDistance, out vec2 interval) {
    vec4 sphere=assimpExhaustPuffOriginRadius[puff];
    float projection=dot(sphere.xyz,ray);
    float d=projection*projection-dot(sphere.xyz,sphere.xyz)+sphere.w*sphere.w*4.84;
    if(d<0.0) return false;
    float root=sqrt(d);
    interval=vec2(max(0.0,projection-root),min(sceneDistance,projection+root));
    return interval.y>interval.x;
}

void assimpExhaustSample(int emitter, vec3 position, vec3 ray, float time,
                        inout float sigmaA, inout float sigmaS, inout vec3 source, inout vec3 heat) {
    vec4 origin=assimpExhaustOriginRadius[emitter], axisLength=assimpExhaustAxisLength[emitter];
    vec4 shape=assimpExhaustShape[emitter], optics=assimpExhaustOptics[emitter];
    vec4 flow=assimpExhaustFlow[emitter], thermal=assimpExhaustThermal[emitter];
    vec4 transport=assimpExhaustTransport[emitter], tintShock=assimpExhaustColorShock[emitter];
    vec3 axis=axisLength.xyz, local=position-origin.xyz;
    float z=dot(local,axis), lengthJet=max(axisLength.w,0.01);
    if(z<=0.0 || z>=lengthJet || flow.x<0.0001) return;
    vec3 radial=local-axis*z;
    float base=max(origin.w,0.03), spread=base+shape.x*z;
    float r=length(radial)/spread;
    if(r>2.5) return;
    vec3 tangent=normalize(cross(axis,abs(axis.y)<0.95?vec3(0,1,0):vec3(1,0,0)));
    vec3 bitangent=cross(axis,tangent);
    float q=z/lengthJet;
    // Large eddies move at the slower entrained gas speed; exit gas remains supersonic.
    float convection=clamp(flow.y*0.006/(1.0+transport.y*z/base),2.0,22.0)/max(transport.x,0.001);
    vec3 p=vec3(dot(radial,tangent),dot(radial,bitangent),z-time*convection)/base;
    vec3 warp=vec3(assimpExhaustNoise(p*.7+3.2),assimpExhaustNoise(p*.7+19.1),assimpExhaustNoise(p*.7-8.5))-.5;
    float noise=assimpExhaustFbm(p*1.7+warp*1.8);
    float cells=assimpExhaustCellular(p*.8+warp);
    float turbulent=shape.w*smoothstep(.03,.6,q);
    float disturbedR=length(radial/spread+ (tangent*warp.x+bitangent*warp.y)*turbulent*.38);
    float spacing=max(tintShock.w,base*.6);
    float phase=z/spacing;
    float triangle=abs(fract(phase)-.5)*2.0;
    // Intersecting compression cones: radial shocks pinch into bright axial knots.
    float shockRadius=base*(.12+.73*triangle)*(1.0+.15*q);
    float shell=exp(-pow((length(radial)-shockRadius)/max(base*.095,.015),2.0));
    float node=exp(-pow((fract(phase)-.5)/.09,2.0))*exp(-disturbedR*disturbedR*8.0);
    float shock=(shell*1.4+node*2.6)*optics.w*exp(-q*1.3);
    float pinch=1.0-.23*optics.w*(1.0-triangle)*exp(-q*2.0);
    float potential=exp(-pow(disturbedR/max(pinch,.4),4.0)*3.2);
    // The axial potential core stays hot longer than the entraining outer shear layer.
    float mixGas=1.0/(1.0+transport.y*max(z-base*4.0,0.0)/base*1.3+disturbedR*disturbedR*.75);
    float temperature=thermal.y+(flow.z-thermal.y)*mixGas;
    temperature*=1.0+.075*shock;
    float axial=smoothstep(0.0,base*.05,z)*(1.0-smoothstep(.65,1.0,q));
    float density=max(flow.w,0.0)*(base*base)/(spread*spread)*axial*shape.z;
    density*=mix(1.0,.35+noise*1.35,turbulent);
    float hot=density*potential*exp(-shape.y*z*.28);
    float shear=exp(-pow(disturbedR-.88,2.0)*3.5)*smoothstep(.08,.5,q);
    float mixed=density*shear*(.25+cells*1.4)*assimpExhaustSmoke[emitter].x;
    float condensation=transport.w*(1.0-smoothstep(330.0,650.0,temperature));
    float soot=transport.z;
    float localA=(hot*.12+mixed*(.008+soot*4.0))*optics.x*transport.x;
    float localS=(hot*.012+mixed*(.018+condensation*.4))*optics.y*transport.x;
    sigmaA+=localA; sigmaS+=localS;
    float burn=pow(clamp((temperature-thermal.y)/max(flow.z-thermal.y,1.0),0.0,1.5),2.8);
    vec3 chemical=mix(tintShock.rgb,vec3(.62,.82,1.0),exp(-z/base*1.8));
    vec3 continuum=assimpExhaustBlackbody(temperature)*(.05+soot*40.0);
    vec3 emission=(chemical*.65+continuum*.4)*hot*burn*optics.z*(.22+shock*6.0);
    source+=emission;
    // Gladstone-Dale density gradient, scaled into the configured sub-pixel refraction range.
    vec3 gradient=radial/max(length(radial),.01)*(-2.0*density*exp(-r*r)/(spread*spread));
    gradient+=(tangent*warp.x+bitangent*warp.y)*density*.8;
    heat+=gradient*assimpExhaustSmoke[emitter].y*(assimpExhaustGas[emitter].y/.00023)
        *clamp((temperature-thermal.y)/1800.0,0.0,2.0);
}

void assimpExhaustPuffSample(int puff, vec3 position, vec3 ray, float time,
                            inout float sigmaA, inout float sigmaS, inout vec3 source) {
    vec4 sphere=assimpExhaustPuffOriginRadius[puff];
    vec3 local=(position-sphere.xyz)/max(sphere.w,.01);
    float r2=dot(local,local);
    if(r2>4.84) return;
    vec4 optics=assimpExhaustPuffOptics[puff], motion=assimpExhaustPuffMotion[puff];
    vec3 noiseP=local*2.6+vec3(motion.y*.37,motion.x*.4,0.0);
    float noise=assimpExhaustFbm(noiseP+assimpExhaustNoise(noiseP*.7)*1.6);
    float density=optics.x*exp(-r2*1.25)*smoothstep(.18,.65,noise)*2.0;
    density*=1.0-smoothstep(4.2,6.0,motion.x);
    float condense=optics.w*(1.0-smoothstep(315.0,440.0,optics.y));
    float a=density*(.0005+optics.z*.8)*motion.w, s=density*(.002+condense*.12)*motion.w;
    sigmaA+=a; sigmaS+=s;
    source+=assimpExhaustBlackbody(optics.y)*density*optics.z*30.0;
}

vec4 assimpIntegrateExhaust(vec3 rayDirection,float sceneDistance,float time,float jitter) {
    assimpExhaustHeatVector=vec3(0.0); assimpExhaustMeanDistance=0.0; assimpExhaustDominantEmitter=-1;
    if((assimpExhaustCount<=0 && assimpExhaustPuffCount<=0) || sceneDistance<=0.0) return vec4(0,0,0,1);
    vec3 ray=normalize(rayDirection);
    vec2 intervals[40];
    int count=0;
    float nearest=1e20;
    for(int i=0;i<ASSIMP_EXHAUST_MAX;i++) {
        if(i>=assimpExhaustCount) break;
        vec2 range;
        if(assimpExhaustInterval(i,ray,sceneDistance,range)) {
            intervals[count++]=range;
            if(range.x<nearest) { nearest=range.x; assimpExhaustDominantEmitter=i; }
        }
    }
    for(int i=0;i<24;i++) {
        if(i>=assimpExhaustPuffCount) break;
        vec2 range;
        if(assimpExhaustPuffInterval(i,ray,sceneDistance,range)) intervals[count++]=range;
    }
    if(count==0) return vec4(0,0,0,1);
    for(int i=1;i<ASSIMP_EXHAUST_INTERVALS;i++) {
        if(i>=count) break;
        vec2 candidate=intervals[i]; int insertion=i;
        for(int j=0;j<ASSIMP_EXHAUST_INTERVALS;j++) {
            if(insertion<=0) break;
            if(intervals[insertion-1].x<=candidate.x) break;
            intervals[insertion]=intervals[insertion-1]; insertion--;
        }
        intervals[insertion]=candidate;
    }
    int regions=1;
    for(int i=1;i<ASSIMP_EXHAUST_INTERVALS;i++) {
        if(i>=count) break;
        if(intervals[i].x<=intervals[regions-1].y) intervals[regions-1].y=max(intervals[regions-1].y,intervals[i].y);
        else intervals[regions++]=intervals[i];
    }
    float occupied=0.0;
    for(int i=0;i<ASSIMP_EXHAUST_INTERVALS;i++) { if(i>=regions) break; occupied+=intervals[i].y-intervals[i].x; }
    int budget=int(clamp(float(ASSIMP_EXHAUST_STEP_BUDGET),16.0,float(ASSIMP_EXHAUST_STEPS)));
    int extra=max(0,budget-regions);
    float cumulative=0.0, weightSum=0.0, transmittance=1.0;
    vec3 radiance=vec3(0.0);
    for(int region=0;region<ASSIMP_EXHAUST_INTERVALS;region++) {
        if(region>=regions || transmittance<.008) break;
        float lengthRegion=intervals[region].y-intervals[region].x;
        int previousBudget=int(floor(cumulative/occupied*float(extra)+.0001));
        cumulative+=lengthRegion;
        int steps=1+int(floor(cumulative/occupied*float(extra)+.0001))-previousBudget;
        float ds=lengthRegion/float(steps);
        for(int step=0;step<ASSIMP_EXHAUST_STEPS;step++) {
            if(step>=steps || transmittance<.008) break;
            float distanceRay=intervals[region].x+(float(step)+clamp(jitter,.02,.98))*ds;
            vec3 position=ray*distanceRay;
            float sigmaA=0.0,sigmaS=0.0; vec3 source=vec3(0.0),heat=vec3(0.0);
            for(int i=0;i<ASSIMP_EXHAUST_MAX;i++) {
                if(i>=assimpExhaustCount) break;
                assimpExhaustSample(i,position,ray,time,sigmaA,sigmaS,source,heat);
            }
            for(int i=0;i<24;i++) {
                if(i>=assimpExhaustPuffCount) break;
                assimpExhaustPuffSample(i,position,ray,time,sigmaA,sigmaS,source);
            }
            if(sigmaS>.00001) source+=sigmaS*(assimpExhaustIncident(position,ray)+assimpExhaustLocalLight(position,ray));
            float sigmaT=sigmaA+sigmaS, segmentT=exp(-sigmaT*ds);
            float integral=sigmaT>.00001?(1.0-segmentT)/sigmaT:ds;
            vec3 contribution=transmittance*source*integral;
            radiance+=contribution;
            assimpExhaustHeatVector+=transmittance*heat*integral;
            float weight=dot(contribution,vec3(.2126,.7152,.0722))+transmittance*(1.0-segmentT);
            assimpExhaustMeanDistance+=distanceRay*weight; weightSum+=weight;
            transmittance*=segmentT;
        }
    }
    assimpExhaustMeanDistance/=max(weightSum,.00001);
    return vec4(radiance,clamp(transmittance,0.0,1.0));
}
#endif
