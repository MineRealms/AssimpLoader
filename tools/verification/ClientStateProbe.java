import com.sun.tools.attach.VirtualMachine;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;

/** Read-only attach probe for an inaccessible dev-client disconnect screen. */
public final class ClientStateProbe {
    public static void main(String[] args) throws Exception {
        var vm=VirtualMachine.attach(args[0]);
        try {vm.loadAgent(args[1],args[2]);} finally {vm.detach();}
    }
    public static void agentmain(String destination, Instrumentation instrumentation) throws Exception {
        StringBuilder result=new StringBuilder();
        for(Class<?> type:instrumentation.getAllLoadedClasses()) {
            if(!type.getName().equals("net.minecraft.client.Minecraft"))continue;
            Object client=type.getMethod("getInstance").invoke(null);
            Object server=type.getMethod("getSingleplayerServer").invoke(client);
            if(server!=null)result.append("server.usesAuthentication=").append(server.getClass().getMethod("usesAuthentication").invoke(server)).append('\n');
            Object currentServer=type.getMethod("getCurrentServer").invoke(client);
            if(currentServer!=null)result.append("connection.isLan=").append(currentServer.getClass().getMethod("isLan").invoke(currentServer)).append('\n');
            Object screen=type.getField("screen").get(client);
            result.append("screen=").append(screen==null?"null":screen.getClass().getName()).append('\n');
            if(screen!=null)for(var field:screen.getClass().getDeclaredFields()) {
                if(!field.getType().getName().equals("net.minecraft.network.chat.Component"))continue;
                field.setAccessible(true);Object component=field.get(screen);
                if(component!=null) {
                    Class<?> componentType=field.getType();
                    result.append(field.getName()).append('=').append(componentType.getMethod("getString").invoke(component)).append('\n');
                }
            }
            break;
        }
        Files.writeString(Path.of(destination),result);
    }
}
