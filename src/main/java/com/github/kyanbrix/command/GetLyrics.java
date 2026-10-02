package com.github.kyanbrix.command;

import com.github.kyanbrix.LyricsClient;
import com.github.kyanbrix.utils.MusicUtil;
import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.Link;
import dev.arbjerg.lavalink.client.player.LavalinkPlayer;
import dev.arbjerg.lavalink.protocol.v4.TrackInfo;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetLyrics implements ICommand {

    private static final Logger log = LoggerFactory.getLogger(GetLyrics.class);
    private static final int MAX_LENGTH = 4000; // embed description limit is 4096

    private final LavalinkClient client;

    private final LyricsClient lyricsClient;

    public GetLyrics(LavalinkClient client, LyricsClient lyricsClient) {
        this.client = client;
        this.lyricsClient = lyricsClient;
    }

    @Override
    public void execute(MessageReceivedEvent event, String args) {

        handleLyrics(event);

    }

    @Override
    public String commandName() {
        return "lyrics";
    }

    @Override
    public String[] aliases() {
        return new String[]{"ly"};
    }

    @Override
    public String description() {
        return "Shows the lyrics of the current song";
    }

    private void handleLyrics(MessageReceivedEvent event) {

        long guildId = event.getGuild().getIdLong();
        Link link = client.getLinkIfCached(guildId);
        LavalinkPlayer player = link == null ? null : link.getCachedPlayer();

        if (player == null || player.getTrack() == null) {
            event.getChannel().sendMessageEmbeds(MusicUtil.error("Nothing is currently playing.")).queue();
            return;
        }

        TrackInfo trackInfo = player.getTrack().getInfo();
        event.getChannel().sendTyping().queue();

        // The lyrics endpoint is per player, so ask the node the player lives on
        lyricsClient.fetchLyrics(link.getNode(), guildId).subscribe(lyricsOptional -> {

            if (lyricsOptional.isEmpty()) {
                event.getChannel().sendMessageEmbeds(MusicUtil.error(String.format("No Lyrics found for **%s**.", trackInfo.getTitle()))).queue();
                return;
            }

            LyricsClient.Lyrics lyrics = lyricsOptional.get();

            MessageEmbed embed = new EmbedBuilder()
                    .setTitle("🎵 " + MusicUtil.truncate(trackInfo.getTitle(), 250), trackInfo.getUri())
                    .setColor(0xF0E68C)
                    .setThumbnail(trackInfo.getArtworkUrl())
                    .setDescription(MusicUtil.truncate(lyrics.text(), MAX_LENGTH))
                    .setFooter("Lyrics provided by " + lyrics.source())
                    .build();

            event.getChannel().sendMessageEmbeds(embed).queue();

        }, err -> {
            log.error("Error fetching lyrics: {}", err.getMessage());
            event.getChannel().sendMessageEmbeds(MusicUtil.error("❌ Could not fetch the lyrics right now.")).queue();
        });

    }
}
