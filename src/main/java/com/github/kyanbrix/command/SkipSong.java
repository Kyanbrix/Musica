package com.github.kyanbrix.command;

import com.github.kyanbrix.GuildMusicManager;
import com.github.kyanbrix.MusicManager;
import com.github.kyanbrix.utils.MusicUtil;
import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.Link;
import dev.arbjerg.lavalink.client.player.Track;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SkipSong implements ICommand {

    private static final Logger log = LoggerFactory.getLogger(SkipSong.class);
    private final LavalinkClient client;
    private final MusicManager musicManager;

    public SkipSong(LavalinkClient client, MusicManager musicManager) {
        this.client = client;
        this.musicManager = musicManager;
    }


    @Override
    public void execute(MessageReceivedEvent event, String args) {

        if (assertMemberInVoice(event)) handleSkip(event);


    }

    @Override
    public String commandName() {
        return "s";
    }

    @Override
    public String[] aliases() {
        return new String[]{"skip", "next"};
    }

    @Override
    public String description() {
        return "Skips the current song";
    }

    private void handleSkip(MessageReceivedEvent event) {

        long guildId = event.getGuild().getIdLong();

        GuildMusicManager guildMusicManager = musicManager.get(guildId);
        Link link = client.getLinkIfCached(guildId);

        if (guildMusicManager == null || link == null) {
            event.getChannel().sendMessageEmbeds(MusicUtil.error("❌ Nothing is currently playing!")).queue();
            return;
        }

        link.getPlayer().subscribe(lavalinkPlayer -> {

            Track skipped = lavalinkPlayer.getTrack();

            if (skipped == null) {

                event.getChannel().sendMessageEmbeds(MusicUtil.error("❌ Nothing is currently playing!"))
                        .queue();

                return;
            }

            // In queue-loop mode a skipped song still comes around again
            if (guildMusicManager.getLoopMode() == GuildMusicManager.LoopMode.QUEUE) {
                guildMusicManager.enQueue(skipped.makeClone());
            }

            Track nextTrack = guildMusicManager.pollNext();

            if (nextTrack == null) {

                musicManager.disconnectAndClean(guildId);

                MessageEmbed embed = new EmbedBuilder()
                        .setDescription("⏭️ Skipped! The queue is now empty — disconnecting.")
                        .build();

                event.getChannel()
                        .sendMessageEmbeds(embed)
                        .queue();

            }else {

                musicManager.play(guildId, nextTrack);

                MessageEmbed embed = new EmbedBuilder()
                        .setDescription(String.format("%s has been skipped by %s", MusicUtil.trackLink(skipped.getInfo()), event.getAuthor().getAsMention()))
                        .setColor(0x7FFFD4)
                        .build();

                event.getChannel().sendMessageEmbeds(embed).queue();

            }

        },err -> {
            log.error("Error fetching player for skip: {}",err.getMessage());
            event.getChannel().sendMessageEmbeds(MusicUtil.error("Could not access the player")).queue();
        });

    }
}
