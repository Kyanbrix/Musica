package com.github.kyanbrix;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

public class LyricsClient {


    private static final Logger log = LoggerFactory.getLogger(LyricsClient.class);
    private final OkHttpClient okHttpClient = new OkHttpClient();


    public Optional<Lyrics> fetchLyrics(String sessionId, long guildId) {

        String url = String.format("http://127.0.0.1:2333/v4/sessions/%s/players/%d/track/lyrics?skipTrackSource=true",sessionId,guildId);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .header("Authorization", "youshallnotpass")
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {

            if (response.code() == 404) {

                return Optional.empty();
            }

            if (!response.isSuccessful()) {
                log.warn("LavaLyrics returned HTTP {}", response.code());
                return Optional.empty();
            }

            JsonObject json = JsonParser.parseString(response.body().string()).getAsJsonObject();

            String text   = json.has("text") && !json.get("text").isJsonNull()
                    ? json.get("text").getAsString()
                    : null;
            String source = json.has("sourceName") && !json.get("sourceName").isJsonNull()
                    ? json.get("sourceName").getAsString()
                    : "unknown";

            if (text == null || text.isBlank()) return Optional.empty();

            return Optional.of(new Lyrics(text,source));


        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }








    public record Lyrics(String text, String source){}

}
