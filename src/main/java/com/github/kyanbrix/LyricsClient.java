package com.github.kyanbrix;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.arbjerg.lavalink.client.LavalinkNode;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;


public class LyricsClient {


    private static final Logger log = LoggerFactory.getLogger(LyricsClient.class);


    public Mono<Optional<Lyrics>> fetchLyrics(LavalinkNode node, long guildId) {

        String path = String.format("/v4/sessions/%s/players/%d/track/lyrics?skipTrackSource=true", node.getSessionId(), guildId);

        return node.customRequest(builder -> builder.path(path).get())
                .map(this::parse);
    }

    private Optional<Lyrics> parse(Response response) {

        try (response) {

            // 404: no lyrics for this track, 204: plugin found nothing
            if (response.code() == 404 || response.code() == 204) {
                return Optional.empty();
            }

            if (!response.isSuccessful()) {
                log.warn("LavaLyrics returned HTTP {}", response.code());
                return Optional.empty();
            }

            String body = response.body().string();
            if (body.isBlank()) return Optional.empty();

            JsonObject json = JsonParser.parseString(body).getAsJsonObject();

            String text = getString(json, "text");
            String source = Optional.ofNullable(getString(json, "sourceName")).orElse("unknown");

            // Synced lyrics may only come as timed lines
            if ((text == null || text.isBlank()) && json.has("lines") && json.get("lines").isJsonArray()) {
                StringBuilder sb = new StringBuilder();
                JsonArray lines = json.getAsJsonArray("lines");
                for (JsonElement line : lines) {
                    String lineText = line.isJsonObject() ? getString(line.getAsJsonObject(), "line") : null;
                    if (lineText != null) sb.append(lineText).append('\n');
                }
                text = sb.toString();
            }

            if (text == null || text.isBlank()) return Optional.empty();

            return Optional.of(new Lyrics(text.strip(), source));

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

    }

    private String getString(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : null;
    }


    public record Lyrics(String text, String source){}

}
