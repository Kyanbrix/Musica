package com.github.kyanbrix.command;

import com.github.kyanbrix.GuildMusicManager;
import com.github.kyanbrix.MusicManager;
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
    public void execute(MessageReceivedEvent event) {

        if (assertMemberInVoice(event)) handleSkip(event);


    }

    @Override
    public String commandName() {
        return "s";
    }

    @Override
    public String[] aliases() {
        return new String[]{"skip"};
    }

    private void handleSkip(MessageReceivedEvent event) {

        long guildId = event.getGuild().getIdLong();

        GuildMusicManager guildMusicManager = musicManager.getOrCreate(guildId);
        Link link = client.getOrCreateLink(guildId);


        link.getPlayer().subscribe(lavalinkPlayer -> {

            if (lavalinkPlayer.getTrack() == null) {

                event.getChannel().sendMessage("Nothing currently playing!")
                        .queue();

                return;
            }

            Track nextTrack = guildMusicManager.pollNext();

            if (nextTrack == null) {

                lavalinkPlayer.setTrack(null)
                        .doOnSuccess(v -> {
                            link.destroy().subscribe();
                            event.getGuild().getAudioManager().closeAudioConnection();
                            musicManager.remove(guildId);
                        })
                        .doOnError(err -> log.error("Error during skip-to-empty: {}", err.getMessage()))
                        .subscribe();
                MessageEmbed embed = new EmbedBuilder()
                        .setDescription("⏭️ Skipped! The queue is now empty — disconnecting.")
                        .build();

                event.getChannel()
                        .sendMessageEmbeds(embed)
                        .queue();

            }else {

                lavalinkPlayer.setTrack(nextTrack)
                        .doOnError(err -> log.error("Error skipping to next song {}",err.getMessage()))
                        .subscribe();

                MessageEmbed embed = new EmbedBuilder()
                        .setDescription(String.format("[**%s**](%s) has been skipped by <@%s>",lavalinkPlayer.getTrack().getInfo().getTitle(),lavalinkPlayer.getTrack().getInfo().getUri(),event.getAuthor().getId()))
                        .setColor(0x7FFFD4)
                        .build();

                event.getChannel().sendMessageEmbeds(embed).queue();

            }

        },err -> {
            log.error("Error fetching player for skip: {}",err.getMessage());
            event.getChannel().sendMessage("Could not access the player").queue();
        });

    }
}
