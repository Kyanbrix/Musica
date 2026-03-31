package com.github.kyanbrix.command;

import com.github.kyanbrix.LyricsClient;
import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.LavalinkNode;
import dev.arbjerg.lavalink.protocol.v4.TrackInfo;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Optional;

public class GetLyrics implements ICommand {

    private final LavalinkClient client;

    private final LyricsClient lyricsClient;

    public GetLyrics(LavalinkClient client, LyricsClient lyricsClient) {
        this.client = client;
        this.lyricsClient = lyricsClient;
    }

    @Override
    public void execute(MessageReceivedEvent event) {

        if (assertMemberInVoice(event)) handleLyrics(event);

    }

    @Override
    public String commandName() {
        return "lyrics";
    }


    private void handleLyrics(MessageReceivedEvent event) {

        long guildId = event.getGuild().getIdLong();

        Optional<LavalinkNode> node = client.getNodes()
                .stream()
                .filter(LavalinkNode::getAvailable)
                .findFirst();

        node.ifPresent(lavalinkNode -> {

            client.getOrCreateLink(guildId).getPlayer().subscribe(player -> {
                EmbedBuilder builder = new EmbedBuilder();
                if (player.getTrack() == null) {
                    builder.setDescription("Nothing is currently playing.");
                    event.getChannel().sendMessageEmbeds(builder.build()).queue();
                    return;
                }

                TrackInfo trackInfo = player.getTrack().getInfo();

                Optional<LyricsClient.Lyrics> lyricsOptional = lyricsClient.fetchLyrics(lavalinkNode.getSessionId(),guildId);

                if (lyricsOptional.isEmpty()) {

                    builder.setDescription(String.format("No Lyrics found for **%s**.",trackInfo.getTitle()));
                    event.getChannel().sendMessageEmbeds(builder.build()).queue();
                    return;
                }

                LyricsClient.Lyrics lyrics = lyricsOptional.get();

                String fullLyrics = lyrics.text();

                String displayLyrics = fullLyrics.length() > 3900 ? fullLyrics.substring(0,3900) : fullLyrics;

                MessageEmbed embed = new EmbedBuilder()
                        .setTitle("\uD83C\uDFB5" + trackInfo.getTitle())
                        .setColor(0xF0E68C)
                        .setDescription(displayLyrics)
                        .build();

                event.getChannel().sendMessageEmbeds(embed).queue();


            });

        });







    }
}
