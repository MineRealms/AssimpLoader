import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/** Run with Java 17 source-file mode; no mod classes or additional dependencies. */
class ProfileJfr {
    public static void main(String[] args) throws Exception {
        if(args.length!=3) throw new IllegalArgumentException("Usage: java ProfileJfr.java recording.jfr from-UTC to-UTC");
        Instant from=Instant.parse(args[1]),to=Instant.parse(args[2]);
        Map<String,Long> execution=new HashMap<>(),allocation=new HashMap<>();
        long samples=0,modelSamples=0,allocationBytes=0,modelBytes=0;
        try(var recording=new RecordingFile(Path.of(args[0]))) {
            while(recording.hasMoreEvents()) {
                RecordedEvent event=recording.readEvent();
                if(event.getStartTime().isBefore(from)||!event.getStartTime().isBefore(to)) continue;
                String type=event.getEventType().getName();
                if(!type.equals("jdk.ExecutionSample")&&!type.equals("jdk.ObjectAllocationSample")) continue;
                var thread=event.getThread(type.equals("jdk.ExecutionSample")?"sampledThread":"eventThread");
                if(thread==null||!thread.getJavaName().equals("Render thread") && thread.getJavaThreadId()!=1) continue;
                var stack=event.getStackTrace();String method=null;
                if(stack!=null) for(var frame:stack.getFrames()) {
                    var value=frame.getMethod();
                    String className=value.getType().getName().replace('/','.');
                    if(className.startsWith("cn.minerealms.assimploader.")) {
                        method=className+"."+value.getName();break;
                    }
                }
                if(type.equals("jdk.ExecutionSample")) {
                    samples++;
                    if(method!=null) {modelSamples++;execution.merge(method,1L,Long::sum);}
                } else {
                    long weight=event.getLong("weight");allocationBytes+=weight;
                    if(method!=null) {modelBytes+=weight;allocation.merge(method,weight,Long::sum);}
                }
            }
        }
        System.out.println("Range UTC: ["+from+", "+to+") Render/main thread (Java thread 1)");
        System.out.println("Java execution samples: total="+samples+" attributed-to-Assimp="+modelSamples);
        System.out.println("Sampled allocation weight bytes: total="+allocationBytes+" attributed-to-Assimp="+modelBytes);
        print("Assimp execution attribution (nearest mod frame, samples)",execution);
        print("Assimp allocation attribution (nearest mod frame, weighted bytes)",allocation);
    }
    private static void print(String title,Map<String,Long> values) {
        System.out.println(title);
        values.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())).limit(20)
                .forEach(entry->System.out.println(entry.getValue()+"\t"+entry.getKey()));
    }
}
