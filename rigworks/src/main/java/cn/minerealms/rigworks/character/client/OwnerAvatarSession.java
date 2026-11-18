package cn.minerealms.rigworks.character.client;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.character.solver.CharacterRigDefinition;
import cn.minerealms.rigworks.character.core.CharacterProfile;
import cn.minerealms.rigworks.character.core.PlayerVisualInput;
import cn.minerealms.rigworks.character.script.*;
import net.minecraft.client.player.AbstractClientPlayer;

/** One interpreter, input history and command applier for exactly one animation owner. */
final class OwnerAvatarSession implements AutoCloseable {
    private final java.util.UUID owner;
    private final String profileId;
    private final AbstractClientPlayer player;
    private final CharacterProfile profile;
    private final AvatarFrameInput observations=new AvatarFrameInput();
    private final AvatarCommandApplier commands;
    private AvatarScriptRuntime.Session session;
    private String hash="";
    private boolean running;
    OwnerAvatarSession(AbstractClientPlayer player,String profileId,CharacterProfile profile,AnimationState animation,
                       CharacterRigDefinition.Installed rig,CharacterHandContacts contacts,Runnable nativeAnimation,Runnable nativeMotion) {
        this.player=player;owner=player.getUUID();this.profileId=profileId;this.profile=profile;
        commands=new AvatarCommandApplier(animation,rig,contacts,nativeAnimation,nativeMotion);
    }
    boolean present(){return CharacterAvatarBehavior.source(owner,profileId)!=null;}
    boolean run(PlayerVisualInput input) {
        var source=CharacterAvatarBehavior.source(owner,profileId);
        if(source==null){reset();return false;}
        if(!source.hash().equals(hash)) {
            closeSession();hash=source.hash();session=CharacterAvatarBehavior.session(owner,source);observations.reset();
        }
        try {
            boolean applied=commands.apply(session.execute(observations.capture(player,profile,input)));
            running=applied;
            CharacterAvatarBehavior.observed(owner,source,session.available(),applied);
            CharacterAvatarBehavior.failure(owner,applied?"":session.failure());return applied;
        }catch(RuntimeException error) {
            commands.reset();session.close();running=false;
            CharacterAvatarBehavior.observed(owner,source,false,false);
            CharacterAvatarBehavior.failure(owner,"Avatar command failure: "+error.getMessage());return false;
        }
    }
    void reset(){if(running){commands.reset();running=false;CharacterAvatarBehavior.stopped(owner);} }
    private void closeSession(){commands.reset();if(session!=null)session.close();session=null;running=false;CharacterAvatarBehavior.stopped(owner);}
    @Override public void close(){closeSession();commands.close();observations.reset();}
}
