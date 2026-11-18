package cn.minerealms.assimploader.workbench.io;

import cn.minerealms.assimploader.workbench.model.WorkbenchDocument;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

/** Invalidates queued recovery writes without blocking the client on disk IO. */
public final class WorkbenchRecovery {
    interface DraftIo {
        void save(WorkbenchDocument document) throws IOException;
        void discard() throws IOException;
    }

    private final DraftIo io;
    private final AtomicLong generation = new AtomicLong();
    private final Object lock = new Object();

    public WorkbenchRecovery(WorkbenchProjectStore store, Path projectFile) {
        this(new DraftIo() {
            @Override public void save(WorkbenchDocument document) throws IOException {store.saveDraft(projectFile,document);}
            @Override public void discard() throws IOException {store.discardDraft(projectFile);}
        });
    }

    WorkbenchRecovery(DraftIo io) {this.io=io;}

    public long generation() {return generation.get();}
    public void invalidatePendingWrites() {generation.incrementAndGet();}

    /** Call on an IO worker, using the generation captured with the immutable document. */
    public boolean save(long expectedGeneration, WorkbenchDocument document) throws IOException {
        synchronized(lock) {
            if(expectedGeneration!=generation.get())return false;
            io.save(document);
            if(expectedGeneration!=generation.get()) {
                io.discard();
                return false;
            }
            return true;
        }
    }

    /** Call after invalidating pending writes, on an IO worker. */
    public void discard() throws IOException {
        synchronized(lock) {io.discard();}
    }
}
