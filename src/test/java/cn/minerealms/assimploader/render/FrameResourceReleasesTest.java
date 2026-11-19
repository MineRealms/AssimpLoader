package cn.minerealms.assimploader.render;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrameResourceReleasesTest {
    @Test
    void keepsRetiredTexturesAliveUntilTheQueuedFrameHasFinished() {
        var releases = new ClientRenderResources.FrameReleases();
        List<String> closed = new ArrayList<>();
        releases.begin();
        releases.retire(() -> closed.add("old-albedo"));
        releases.retire(() -> closed.add("old-debug"));
        assertEquals(List.of(), closed);
        releases.end();
        assertEquals(List.of("old-albedo", "old-debug"), closed);
        releases.begin();
        releases.end();
        assertEquals(2, closed.size());
    }

    @Test
    void releasesImmediatelyOutsideAFrameAndDoesNotCarryOldGenerationsForward() {
        var releases = new ClientRenderResources.FrameReleases();
        List<Integer> closed = new ArrayList<>();
        releases.retire(() -> closed.add(-1));
        assertEquals(List.of(-1), closed);
        for (int generation = 0; generation < 10; generation++) {
            int retired = generation;
            releases.begin();
            releases.retire(() -> closed.add(retired));
            assertEquals(generation + 1, closed.size());
            releases.end();
            assertEquals(generation + 2, closed.size());
        }
    }

    @Test
    void aFailedReleaseDoesNotLeakTheRemainingTextures() {
        var releases = new ClientRenderResources.FrameReleases();
        List<String> closed = new ArrayList<>();
        var failure = new IllegalStateException("failed close");
        releases.begin();
        releases.retire(() -> { throw failure; });
        releases.retire(() -> closed.add("second-texture"));
        assertSame(failure, assertThrows(IllegalStateException.class, releases::end));
        assertEquals(List.of("second-texture"), closed);
        releases.end();
        assertEquals(1, closed.size());
    }
}
