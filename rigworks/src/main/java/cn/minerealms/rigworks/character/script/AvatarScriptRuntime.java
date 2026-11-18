package cn.minerealms.rigworks.character.script;

import cn.minerealms.rigworks.character.core.CharacterVisualPackage;
import com.google.gson.JsonObject;

import java.util.List;

/** Optional isolated behavior interpreter. Every owner needs its own session and JavaScript state. */
public final class AvatarScriptRuntime {
    private AvatarScriptRuntime() {}

    public interface Session extends AutoCloseable {
        Result execute(JsonObject input);
        String failure();
        boolean available();
        String bundleHash();
        void reset();
        @Override void close();
    }
    public record Result(boolean handled,List<AvatarCommand> commands,String failure) {
        public Result {commands=List.copyOf(commands);if(commands.size()>128)throw new IllegalArgumentException("Too many avatar commands");}
        public static Result fallback(String failure){return new Result(false,List.of(),failure);}
    }
    public static Session open(CharacterVisualPackage source) {
        try{return open(AvatarScriptBundle.fromPackage(source));}
        catch(RuntimeException invalid){return new Unavailable("Invalid avatar behavior: "+shortError(invalid),source.hash());}
    }
    public static Session open(AvatarScriptBundle source) {
        try {
            Class.forName("dev.latvian.mods.rhino.Context",false,AvatarScriptRuntime.class.getClassLoader());
            return new RhinoAvatarSession(source);
        } catch(ClassNotFoundException|LinkageError missing) {return new Unavailable("Rhino is not installed; native animation remains active",source.hash());}
        catch(RuntimeException invalid){return new Unavailable("Invalid avatar behavior: "+shortError(invalid),source.hash());}
    }
    private static final class Unavailable implements Session {
        private final String failure,hash;
        private Unavailable(String failure,String hash){this.failure=failure;this.hash=hash;}
        public Result execute(JsonObject input){return Result.fallback(failure);}public String failure(){return failure;}
        public boolean available(){return false;}public String bundleHash(){return hash;}public void reset(){}public void close(){}
    }
    static String shortError(Throwable failure){String text=failure.getMessage();return text==null?failure.getClass().getSimpleName():text.substring(0,Math.min(text.length(),512));}
}
