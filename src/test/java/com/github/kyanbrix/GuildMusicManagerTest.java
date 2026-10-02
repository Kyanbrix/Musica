package com.github.kyanbrix;

import dev.arbjerg.lavalink.client.player.Track;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GuildMusicManagerTest {

    @Test
    void enqueueReturnsPositionAndPollsInOrder() {
        GuildMusicManager manager = new GuildMusicManager(1L);
        Track a = TestTracks.track("a");
        Track b = TestTracks.track("b");

        assertEquals(1, manager.enQueue(a));
        assertEquals(2, manager.enQueue(b));
        assertSame(a, manager.pollNext());
        assertSame(b, manager.pollNext());
        assertNull(manager.pollNext());
    }

    @Test
    void removeTrackUsesOneBasedPositions() {
        GuildMusicManager manager = new GuildMusicManager(1L);
        Track a = TestTracks.track("a");
        Track b = TestTracks.track("b");
        manager.enQueueAll(List.of(a, b));

        assertNull(manager.removeTrack(0));
        assertNull(manager.removeTrack(3));
        assertSame(b, manager.removeTrack(2));
        assertEquals(List.of(a), manager.getTrackQueue());
    }

    @Test
    void shuffleKeepsEveryTrack() {
        GuildMusicManager manager = new GuildMusicManager(1L);
        for (int i = 0; i < 50; i++) manager.enQueue(TestTracks.track("t" + i));
        List<Track> before = manager.getTrackQueue();

        manager.shuffleQueue();

        assertEquals(50, manager.queueSize());
        assertEquals(new HashSet<>(before), new HashSet<>(manager.getTrackQueue()));
    }

    @Test
    void snapshotIsNotLiveView() {
        GuildMusicManager manager = new GuildMusicManager(1L);
        manager.enQueue(TestTracks.track("a"));
        List<Track> snapshot = manager.getTrackQueue();

        manager.clearQueue();

        assertEquals(1, snapshot.size());
        assertTrue(manager.isQueueEmpty());
    }
}
