package com.github.kyanbrix.utils;

import com.github.kyanbrix.TestTracks;
import dev.arbjerg.lavalink.client.player.Track;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MusicUtilTest {

    @Test
    void formatDuration() {
        assertEquals("LIVE", MusicUtil.formatDuration(0));
        assertEquals("0:05", MusicUtil.formatDuration(5_000));
        assertEquals("3:20", MusicUtil.formatDuration(200_000));
        assertEquals("1:02:03", MusicUtil.formatDuration(3_723_000));
    }

    @Test
    void parseDuration() {
        assertEquals(90_000, MusicUtil.parseDuration("90"));
        assertEquals(90_000, MusicUtil.parseDuration("1:30"));
        assertEquals(3_723_000, MusicUtil.parseDuration("1:02:03"));
        assertEquals(-1, MusicUtil.parseDuration("abc"));
        assertEquals(-1, MusicUtil.parseDuration("1:2:3:4"));
        assertEquals(-1, MusicUtil.parseDuration("-5"));
        assertEquals(-1, MusicUtil.parseDuration(""));
    }

    @Test
    void progressBarAlwaysHasOneMarker() {
        for (long position : new long[]{0, 50_000, 199_999, 200_000, 500_000}) {
            String bar = MusicUtil.progressBar(position, 200_000);
            assertEquals(1, bar.split("🔘", -1).length - 1, bar);
            assertEquals(14, bar.replace("🔘", "").length(), bar);
        }
        assertTrue(MusicUtil.progressBar(1_000, 0).startsWith("🔘"));
    }

    @Test
    void trackLinkEscapesTitle() {
        Track track = TestTracks.track("[Official] *Video*", "https://youtu.be/x");
        assertEquals("[**\\[Official\\] \\*Video\\***](https://youtu.be/x)", MusicUtil.trackLink(track.getInfo()));
    }

    @Test
    void requesterRoundTrip() {
        Track track = TestTracks.track("song");
        assertEquals(0L, MusicUtil.getRequester(track));

        MusicUtil.setRequester(track, 123456789012345678L);
        assertEquals(123456789012345678L, MusicUtil.getRequester(track));
        // Loop mode replays clones, which must keep the requester
        assertEquals(123456789012345678L, MusicUtil.getRequester(track.makeClone()));
    }

    @Test
    void unknownSourceHasNoNullEmoji() {
        assertEquals("🎵", MusicUtil.sourceEmoji("http"));
    }
}
