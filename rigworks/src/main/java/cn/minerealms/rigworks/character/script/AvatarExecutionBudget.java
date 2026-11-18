package cn.minerealms.rigworks.character.script;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.function.LongSupplier;

/** Counts execution work without charging an avatar for another thread or a JVM pause. */
final class AvatarExecutionBudget {
    private static final long WALL_WATCHDOG=5_000_000_000L;
    private static final long FALLBACK_WALL_ALLOWANCE=250_000_000L;
    private static final long MAX_ALLOCATED_BYTES=32L*1024*1024;
    private static final ThreadMXBean THREADS=ManagementFactory.getThreadMXBean();
    private final LongSupplier wall,cpu,allocated;
    private long instructions,instructionLimit,cpuAllowance,wallStart,cpuStart,allocationStart;

    AvatarExecutionBudget(){this(System::nanoTime,AvatarExecutionBudget::cpuTime,AvatarExecutionBudget::allocatedBytes);}
    AvatarExecutionBudget(LongSupplier wall,LongSupplier cpu,LongSupplier allocated){this.wall=wall;this.cpu=cpu;this.allocated=allocated;}
    void arm(long instructionLimit,long cpuAllowance) {
        instructions=0;this.instructionLimit=instructionLimit;this.cpuAllowance=cpuAllowance;
        wallStart=wall.getAsLong();cpuStart=cpu.getAsLong();allocationStart=allocated.getAsLong();
    }
    void observe(int count){instructions+=count;check();}
    void check() {
        if(instructions>instructionLimit)throw new Exceeded("Avatar instruction budget exceeded");
        if(Thread.currentThread().isInterrupted())throw new Exceeded("Avatar execution interrupted");
        long cpuNow=cpu.getAsLong();boolean measuredCpu=cpuStart>=0&&cpuNow>=cpuStart;
        if(measuredCpu&&cpuNow-cpuStart>cpuAllowance)throw new Exceeded("Avatar CPU time budget exceeded");
        long wallAllowance=measuredCpu?Math.max(WALL_WATCHDOG,cpuAllowance*10):Math.max(FALLBACK_WALL_ALLOWANCE,cpuAllowance*4);
        if(wall.getAsLong()-wallStart>wallAllowance)throw new Exceeded("Avatar wall time watchdog budget exceeded");
        long allocatedNow=allocated.getAsLong();
        if(allocationStart>=0&&allocatedNow>=allocationStart&&allocatedNow-allocationStart>MAX_ALLOCATED_BYTES)
            throw new Exceeded("Avatar allocation budget exceeded");
    }
    private static long cpuTime() {
        try{return THREADS.isCurrentThreadCpuTimeSupported()&&THREADS.isThreadCpuTimeEnabled()?THREADS.getCurrentThreadCpuTime():-1;}
        catch(UnsupportedOperationException|SecurityException unavailable){return -1;}
    }
    private static long allocatedBytes() {
        try{return THREADS instanceof com.sun.management.ThreadMXBean allocated&&allocated.isThreadAllocatedMemorySupported()&&allocated.isThreadAllocatedMemoryEnabled()
                ?allocated.getThreadAllocatedBytes(Thread.currentThread().getId()):-1;}
        catch(UnsupportedOperationException|SecurityException unavailable){return -1;}
    }
    static final class Exceeded extends Error {
        Exceeded(String message){super(message,null,false,false);}
    }
}
