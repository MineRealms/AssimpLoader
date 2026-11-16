package cn.minerealms.assimploader.animation;

import org.joml.Vector3f;

/** Bounded tick emitter. Its sink translates model coordinates to the owner's world anchor. */
public final class AttachmentParticleEmitter {
    @FunctionalInterface public interface Sink { void emit(String particle,Vector3f position,Vector3f velocity); }
    private final String attachment,particle;
    private final float rate;
    private final Vector3f velocity;
    private double accumulator;

    public AttachmentParticleEmitter(String attachment,String particle,float rate,Vector3f velocity) {
        if(attachment==null || particle==null || particle.isBlank() || !Float.isFinite(rate) || rate<0 || rate>1000 || !velocity.isFinite())
            throw new IllegalArgumentException("Invalid attachment emitter");
        this.attachment=attachment; this.particle=particle; this.rate=rate; this.velocity=new Vector3f(velocity);
    }
    public String attachment() { return attachment; }
    public void update(double deltaSeconds,Vector3f position,Sink sink) {
        update(deltaSeconds,position,sink,32);
    }
    public int update(double deltaSeconds,Vector3f position,Sink sink,int maximumEmissions) {
        if(!Double.isFinite(deltaSeconds) || deltaSeconds<0 || !position.isFinite()) throw new IllegalArgumentException("Invalid emitter input");
        if(maximumEmissions<0 || maximumEmissions>32) throw new IllegalArgumentException("Invalid particle budget");
        if(sink==null || maximumEmissions==0) { accumulator=0; return 0; }
        accumulator=Math.min(maximumEmissions,accumulator+deltaSeconds*rate);
        int count=(int)Math.floor(accumulator+1e-8); accumulator-=count;
        for(int i=0;i<count;i++) sink.emit(particle,new Vector3f(position),new Vector3f(velocity));
        return count;
    }
    public void reset() { accumulator=0; }
}
