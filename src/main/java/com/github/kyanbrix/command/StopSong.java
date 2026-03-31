package com.github.kyanbrix.command;

import com.github.kyanbrix.GuildMusicManager;
import com.github.kyanbrix.MusicManager;
import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.Link;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StopSong implements ICommand {
    private final LavalinkClient client;
    private static final Logger log = LoggerFactory.getLogger(StopSong.class);
    private final MusicManager musicManager;

    public StopSong(LavalinkClient client, MusicManager musicManager) {

        this.client = client;
        this.musicManager = musicManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {

        if (assertMemberInVoice(event)) handleStop(event);

    }

    @Override
    public String commandName() {
        return "stop";
    }

    @Override
    public String[] aliases() {

        return new String[]{"stop"};
    }

    private void handleStop(MessageReceivedEvent event) {

        long guildId = event.getGuild().getIdLong();
        GuildMusicManager guildManager = musicManager.getOrCreate(guildId);
        Link link = client.getOrCreateLink(guildId);

        // 1. Clear the queue immediately
        guildManager.clearQueue();

        // 2. Stop player, destroy link, close voice connection
        link.getPlayer()
                .flatMap(player -> player.setTrack(null))   // null track = stop playback
                .doOnSuccess(v -> {
                    musicManager.disconnectAndClean(guildId);
                    musicManager.remove(guildId);
                    log.info("[Guild {}] Stopped and cleaned up.", guildId);
                })
                .doOnError(err -> log.error("Error during stop: {}", err.getMessage()))
                .subscribe();

        // Reply immediately (the cleanup above is async but fast)
        MessageEmbed embed = new EmbedBuilder()
                .setColor(0xB22222)
                .setDescription("⏹️ Stopped the music, cleared the queue, and disconnected.")
                .build();

        event.getChannel().sendMessageEmbeds(embed).queue();
    }
}
