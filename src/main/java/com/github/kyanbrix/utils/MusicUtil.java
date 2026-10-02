package com.github.kyanbrix.utils;

import com.github.kyanbrix.Constant;
import dev.arbjerg.lavalink.client.player.Track;
import dev.arbjerg.lavalink.protocol.v4.TrackInfo;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;

import java.util.Map;

public final class MusicUtil {

    private MusicUtil() {
    }

    public static String formatDuration(long ms) {
        if (ms <= 0) return "LIVE";
        long totalSec = ms / 1000;
        long hours    = totalSec / 3600;
        long minutes  = (totalSec % 3600) / 60;
        long seconds  = totalSec % 60;
        return hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes, seconds)
                : String.format("%d:%02d", minutes, seconds);
    }

    /**
     * Parses "90", "1:30" or "1:02:30" into milliseconds.
     *
     * @return the duration in ms, or -1 if the input is not a valid time
     */
    public static long parseDuration(String input) {
        String[] parts = input.strip().split(":");
        if (parts.length > 3) return -1;

        long seconds = 0;
        try {
            for (String part : parts) {
                long value = Long.parseLong(part);
                if (value < 0) return -1;
                seconds = seconds * 60 + value;
            }
        } catch (NumberFormatException e) {
            return -1;
        }
        return seconds * 1000;
    }

    public static String progressBar(long position, long length) {
        final int size = 15;
        int marker = length <= 0 ? 0 : (int) Math.min(size - 1, position * size / length);
        return "▬".repeat(marker) + "🔘" + "▬".repeat(size - 1 - marker);
    }

    /** Masked markdown link to the track, safe against titles containing markdown characters. */
    public static String trackLink(TrackInfo info) {
        String title = MarkdownSanitizer.escape(truncate(info.getTitle(), 80))
                .replace("[", "\\[")
                .replace("]", "\\]");
        return info.getUri() == null ? "**" + title + "**" : String.format("[**%s**](%s)", title, info.getUri());
    }

    public static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    public static String sourceEmoji(String source) {

        return switch (source) {
            case "spotify" -> "<:Spotify:1487793100314378330>";
            case "youtube" -> "<:Youtubelogo:1487803020707234012>";
            case "applemusic" -> "<:apple_music:1487793599591747654>";
            case "deezer" -> "<:Deezer:1487793743968075836>";
            case "soundcloud" -> "☁️";
            default -> "🎵";
        };
    }

    public static int embedColor(String source) {

        return switch (source) {
            case "spotify" -> 0x00FF7F;
            case "youtube" -> 0xFF0000;
            case "applemusic" -> 0xDC143C;
            case "deezer" -> 0x8B008B;
            case "soundcloud" -> 0xFF7700;
            default -> 0x708090;
        };
    }

    /** Stores who queued the track; Lavalink echoes user data back in every event. */
    public static void setRequester(Track track, long userId) {
        track.setUserData(Map.of("requester", Long.toString(userId)));
    }

    /** @return the requester's user ID, or 0 if unknown */
    public static long getRequester(Track track) {
        try {
            Map<?, ?> data = track.getUserData(Map.class);
            Object requester = data == null ? null : data.get("requester");
            return requester == null ? 0L : Long.parseLong(requester.toString());
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    public static MessageEmbed error(String description) {
        return new EmbedBuilder()
                .setColor(Constant.ERROR_COLOR)
                .setDescription(description)
                .build();
    }

}
