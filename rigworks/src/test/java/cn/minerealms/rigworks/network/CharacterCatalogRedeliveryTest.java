package cn.minerealms.rigworks.network;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CharacterCatalogRedeliveryTest {
    @Test void coalescesTheLatestRevisionAndPreservesOwnerConfirmationUntilDelivered() {
        var queue=new CharacterCatalogRedelivery();Object connection=new Object();
        var key=new CharacterCatalogRedelivery.Key(UUID.randomUUID(),UUID.randomUUID());
        assertTrue(queue.failed(key,connection,2,17,0));assertTrue(queue.due(3,32).isEmpty());
        assertEquals(17,queue.request(key,connection,0));
        assertTrue(queue.failed(key,connection,3,0,4));
        var latest=queue.due(8,32).get(0);assertEquals(3,latest.revision());assertEquals(17,latest.request());
        assertEquals(1,latest.attempts());assertEquals(1,queue.size());
        queue.sent(key);assertEquals(0,queue.size());
    }
    @Test void usesBoundedBackoffAndDoesNotCarryConfirmationIntoAnotherConnection() {
        var queue=new CharacterCatalogRedelivery();var key=new CharacterCatalogRedelivery.Key(UUID.randomUUID(),UUID.randomUUID());
        Object original=new Object(),replacement=new Object();long now=0;
        for(int i=1;i<=CharacterCatalogRedelivery.MAX_ATTEMPTS;i++) {
            assertTrue(queue.failed(key,original,1,8,now));var entry=queue.due(now+200,1).get(0);
            assertTrue(entry.due()-now>=4&&entry.due()-now<=200);now=entry.due();
        }
        assertFalse(queue.failed(key,original,1,8,now));assertEquals(0,queue.size());
        queue.failed(key,original,2,9,now);assertEquals(0,queue.request(key,replacement,0));
        queue.failed(key,replacement,2,0,now);assertEquals(0,queue.due(now+4,1).get(0).request());
    }
    @Test void capacityAndDisconnectCleanupBoundReferencesAndPerTickWork() {
        var queue=new CharacterCatalogRedelivery();Object connection=new Object();UUID owner=UUID.randomUUID();
        for(int i=0;i<CharacterCatalogRedelivery.MAX_ENTRIES+3;i++)queue.failed(new CharacterCatalogRedelivery.Key(UUID.randomUUID(),owner),connection,1,0,0);
        assertEquals(CharacterCatalogRedelivery.MAX_ENTRIES,queue.size());assertEquals(32,queue.due(4,32).size());
        queue.disconnect(owner);assertEquals(0,queue.size());
        queue.failed(new CharacterCatalogRedelivery.Key(owner,owner),connection,1,1,0);queue.clear();assertEquals(0,queue.size());
    }
}
