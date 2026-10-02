package com.github.kyanbrix;

import com.github.kyanbrix.utils.SpotifyTrack;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.arbjerg.lavalink.client.player.Track;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpotifyApiClient {

    private static final String TOKEN_URL    = "https://accounts.spotify.com/api/token";
    private static final String API_BASE     = "https://api.spotify.com/v1";
    private static final Pattern PLAYLIST_ID = Pattern.compile(
            "spotify\\.com/playlist/([A-Za-z0-9]+)|^spotify:playlist:([A-Za-z0-9]+)$"
    );
    private static final Logger log = LoggerFactory.getLogger(SpotifyApiClient.class);

    private final OkHttpClient http = new OkHttpClient();

    private String access_token;
    private Instant tokenExpiresAt = Instant.EPOCH;
    private final String clientId;
    private final String clientSecret;

    public SpotifyApiClient(String clientId, String clientSecret) {

        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }


    public Optional<List<SpotifyTrack>> getPlaylist(String spotifyPlaylistUrl) {

        int offset = 0;
        int limit = 100;

        String id = extractPlaylistId(spotifyPlaylistUrl);
        if (id == null) {
            log.warn("Could not extract playlist ID from: {}", spotifyPlaylistUrl);
            return Optional.empty();
        }


        List<SpotifyTrack> tracks = new ArrayList<>();

        try {
            ensureValidToken();


            while (true) {
                String url = String.format(
                        "%s/playlists/%s/items?limit=%d&offset=%d&fields=items(track(name,artists(name))),next",
                        API_BASE, id, limit, offset
                );

                Request request = new Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer " + access_token)
                        .build();

                try (Response response = http.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        log.warn("Spotify playlist items API returned {}", response.code());
                        return Optional.empty();
                    }

                    JsonObject json  = JsonParser.parseString(response.body().string()).getAsJsonObject();
                    JsonArray  items = json.getAsJsonArray("items");

                    for (var item : items) {
                        var trackElement = item.getAsJsonObject().get("track");
                        if (trackElement == null || trackElement.isJsonNull()) continue;

                        JsonObject trackObj = trackElement.getAsJsonObject();

                        if (!trackObj.has("name") || trackObj.get("name").isJsonNull()) continue;
                        if (trackObj.has("is_local") && trackObj.get("is_local").getAsBoolean()) continue;

                        String title  = trackObj.get("name").getAsString();
                        String artist = trackObj.getAsJsonArray("artists")
                                .get(0).getAsJsonObject()
                                .get("name").getAsString();
                        tracks.add(new SpotifyTrack(title, artist));
                    }

                    if (json.get("next").isJsonNull()) break;
                    offset += limit;
                }


            }

            return Optional.of(tracks);


        }catch (IOException e) {
            log.error(e.getMessage());
            return Optional.empty();
        }

    }

    private void ensureValidToken() throws IOException {
        if (access_token != null && Instant.now().isBefore(tokenExpiresAt)) return;

        String credentials = Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes());

        RequestBody body = new FormBody.Builder()
                .add("grant_type", "client_credentials")
                .build();

        Request request = new Request.Builder()
                .url(TOKEN_URL)
                .header("Authorization", "Basic " + credentials)
                .post(body)
                .build();

        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Token request failed: " + response.code());
            }

            JsonObject json = JsonParser.parseString(response.body().string())
                    .getAsJsonObject();

            access_token = json.get("access_token").getAsString();
            int expiresIn = json.get("expires_in").getAsInt(); // usually 3600
            tokenExpiresAt = Instant.now().plusSeconds(expiresIn - 60); // refresh 60s early

            log.debug("Spotify access token refreshed, expires in {}s", expiresIn);
        }
    }

    private String extractPlaylistId(String input) {
        Matcher m = PLAYLIST_ID.matcher(input);
        if (!m.find()) return null;
        return m.group(1) != null ? m.group(1) : m.group(2);
    }




}
