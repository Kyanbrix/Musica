package com.github.kyanbrix;

import dev.arbjerg.lavalink.client.player.Track;
import dev.arbjerg.lavalink.protocol.v4.TrackInfo;
import kotlinx.serialization.json.JsonObject;

import java.util.Map;

public final class TestTracks {

    private TestTracks() {
    }

    public static Track track(String title) {
        return track(title, "https://example.com/" + title);
    }

    public static Track track(String title, String uri) {
        TrackInfo info = new TrackInfo(title, true, "Author", 200_000, false, 0, title, uri, "youtube", null, null);
        return new Track(new dev.arbjerg.lavalink.protocol.v4.Track("encoded-" + title, info,
                new JsonObject(Map.of()), new JsonObject(Map.of())));
    }
}
