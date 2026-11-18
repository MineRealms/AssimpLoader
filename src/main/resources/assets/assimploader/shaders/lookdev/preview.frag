#version 150

in vec3 viewNormal;
in vec2 texCoord;
in vec4 vertexColor;
in vec3 viewPosition;
uniform sampler2D Albedo;
uniform sampler2D NormalMap;
uniform sampler2D SpecularMap;
uniform vec4 MaterialColor;
uniform vec4 Surface;
uniform ivec2 MapFlags;
uniform int Selected;
uniform float AlphaCutoff;
out vec4 fragColor;

void main() {
    vec4 base = texture(Albedo, texCoord) * vertexColor * MaterialColor;
    if (AlphaCutoff>=0.0&&base.a < AlphaCutoff) discard;
    if (AlphaCutoff<0.0)base.a=1.0;
    float normalLength = length(viewNormal);
    vec3 normal = normalLength > 0.00001 ? viewNormal / normalLength : vec3(0.0, 1.0, 0.0);
    if (!gl_FrontFacing) normal = -normal;
    if(MapFlags.x!=0) {
        vec2 encodedNormal=texture(NormalMap,texCoord).rg*2.0-1.0;
        encodedNormal.y=-encodedNormal.y;
        vec3 mapNormal=vec3(encodedNormal,sqrt(max(0.0,1.0-dot(encodedNormal,encodedNormal))));
        // Derivative tangent frame also supports imported meshes without explicit tangents.
        vec3 dp1=dFdx(viewPosition),dp2=dFdy(viewPosition);
        vec2 duv1=dFdx(texCoord),duv2=dFdy(texCoord);
        vec3 t=dp1*duv2.y-dp2*duv1.y;
        vec3 b=-dp1*duv2.x+dp2*duv1.x;
        float lengthSquared=max(dot(t,t),dot(b,b));
        if(lengthSquared>0.00000001)normal=normalize(mat3(t*inversesqrt(lengthSquared),b*inversesqrt(lengthSquared),normal)*mapNormal);
    }
    vec4 spec=texture(SpecularMap,texCoord);
    float metal=Surface.x,rough=Surface.y;
    if(MapFlags.y!=0&&Surface.w<0.5){rough=pow(1.0-spec.r,2.0);metal=step(229.5/255.0,spec.g);}
    rough=max(.045,rough);
    vec3 albedo=pow(max(base.rgb,vec3(0.0)),vec3(2.2));
    vec3 l=normalize(vec3(-.4,.7,.8)),v=vec3(0.0,0.0,1.0),h=normalize(l+v);
    float nl=max(dot(normal,l),0.0),nv=max(dot(normal,v),.001),nh=max(dot(normal,h),0.0),vh=max(dot(v,h),0.0);
    float a2=pow(rough,4.0),denom=nh*nh*(a2-1.0)+1.0;
    float d=a2/max(3.14159265*denom*denom,.00001);
    float k=pow(rough+1.0,2.0)/8.0;
    float g=nv/(nv*(1.0-k)+k)*nl/(nl*(1.0-k)+k);
    vec3 f0=mix(vec3(.04),albedo,metal),f=f0+(1.0-f0)*pow(1.0-vh,5.0);
    vec3 lit=((1.0-f)*(1.0-metal)*albedo/3.14159265+d*g*f/max(4.0*nv*nl,.001))*nl*2.8;
    lit+=albedo*.24+mix(vec3(.08),albedo,.5)*metal*(1.0-rough)*.45;
    float emission=Surface.z+(MapFlags.y!=0&&spec.a<.999?spec.a*3.0:0.0);
    lit+=albedo*emission;
    if(Selected!=0)lit+=vec3(.05,.18,.15);
    lit=lit/(lit+vec3(1.0));
    fragColor = vec4(pow(lit,vec3(1.0/2.2)),base.a);
}
