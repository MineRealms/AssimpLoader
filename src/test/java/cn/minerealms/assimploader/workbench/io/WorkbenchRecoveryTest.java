package cn.minerealms.assimploader.workbench.io;

import cn.minerealms.assimploader.workbench.model.ModelTransform;
import cn.minerealms.assimploader.workbench.model.WorkbenchDocument;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class WorkbenchRecoveryTest {
    @TempDir Path temp;

    @Test void aQueuedAutosaveCannotRecreateAnExplicitlyDiscardedDraft() throws Exception {
        var store=new WorkbenchProjectStore(temp.resolve("projects"));
        Path file=store.root().resolve("asset/project.workbench.json");
        var recovery=new WorkbenchRecovery(store,file);
        long queuedGeneration=recovery.generation();
        assertTrue(recovery.save(queuedGeneration,document()));
        recovery.invalidatePendingWrites();recovery.discard();

        assertFalse(recovery.save(queuedGeneration,document().withTransform(ModelTransform.IDENTITY.withPosition(3,0,0))));
        assertTrue(store.readDraft(file).isEmpty());
        assertTrue(recovery.save(recovery.generation(),document()));
        assertEquals(document(),store.readDraft(file).orElseThrow());
    }

    @Test void invalidationDuringDiskWriteRemovesItsResultBeforeCompleting() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var persisted=new AtomicReference<WorkbenchDocument>();
        var recovery=new WorkbenchRecovery(new WorkbenchRecovery.DraftIo() {
            @Override public void save(WorkbenchDocument document) throws IOException {
                entered.countDown();
                try {if(!release.await(5,TimeUnit.SECONDS))throw new IOException("Test writer was not released");}
                catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException(interrupted);}
                persisted.set(document);
            }
            @Override public void discard(){persisted.set(null);}
        });
        var executor=Executors.newSingleThreadExecutor();
        try {
            long queuedGeneration=recovery.generation();
            var writing=executor.submit(()->recovery.save(queuedGeneration,document()));
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            recovery.invalidatePendingWrites();
            release.countDown();
            assertFalse(writing.get(5,TimeUnit.SECONDS));
            assertNull(persisted.get());
        } finally {release.countDown();executor.shutdownNow();}
    }

    @Test void discardFailureIsReportedAndDoesNotReenableOldAutosaves() {
        var recovery=new WorkbenchRecovery(new WorkbenchRecovery.DraftIo() {
            @Override public void save(WorkbenchDocument document){fail("Stale write reached disk");}
            @Override public void discard() throws IOException {throw new IOException("Draft is locked");}
        });
        long queuedGeneration=recovery.generation();
        recovery.invalidatePendingWrites();
        assertEquals("Draft is locked",assertThrows(IOException.class,recovery::discard).getMessage());
        assertDoesNotThrow(()->assertFalse(recovery.save(queuedGeneration,document())));
    }

    private static WorkbenchDocument document() {
        return new WorkbenchDocument(1,"test:asset","assets/model.fbx","",ModelTransform.IDENTITY,
                Map.of(),Map.of(),Map.of(),WorkbenchDocument.AnimationPreview.DEFAULT,new JsonObject());
    }
}
