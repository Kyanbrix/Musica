package com.github.kyanbrix.command;

import com.github.kyanbrix.Constant;
import com.github.kyanbrix.utils.MusicUtil;
import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.Link;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Pause implements ICommand {

    private static final Logger log = LoggerFactory.getLogger(Pause.class);
    private final LavalinkClient client;

    public Pause(LavalinkClient client) {
        this.client = client;
    }

    @Override
    public void execute(MessageReceivedEvent event, String args) {

        // The bot's own member is always cached, no need to fetch it
        GuildVoiceState voiceState = event.getGuild().getSelfMember().getVoiceState();

        if (voiceState != null && voiceState.isGuildMuted()) {
            event.getChannel().sendMessageEmbeds(MusicUtil.error("It appears I am muted! You need to unmute me first.")).queue();
            return;
        }

        if (assertMemberInVoice(event)) handlePause(event);

    }

    @Override
    public String commandName() {
        return "pause";
    }

    @Override
    public String[] aliases() {
        return new String[]{"freeze","paws"};
    }

    @Override
    public String description() {
        return "Pauses the current song";
    }

    private void handlePause(MessageReceivedEvent event) {

        Link link = client.getLinkIfCached(event.getGuild().getIdLong());

        if (link == null) {
            event.getChannel().sendMessageEmbeds(MusicUtil.error("❌ Nothing is currently playing.")).queue();
            return;
        }

        link.getPlayer().subscribe(player -> {

            if (player.getTrack() == null) {
                event.getChannel().sendMessageEmbeds(MusicUtil.error("❌ Nothing is currently playing.")).queue();
                return;
            }

            if (player.getPaused()) {
                MessageEmbed embed = new EmbedBuilder()
                        .setDescription(String.format("⏸️ Song is already paused. `%sresume / %srs` to continue.", Constant.PREFIX, Constant.PREFIX))
                        .build();
                event.getChannel().sendMessageEmbeds(embed).queue();
                return;
            }

            player.setPaused(true)
                    .subscribe(p -> { }, err -> log.error("Error pausing a track: {}", err.getMessage()));

            MessageEmbed embed = new EmbedBuilder()
                    .setAuthor(event.getAuthor().getName()+" paused the song",null,event.getAuthor().getEffectiveAvatarUrl())
                    .setDescription("⏸️ Paused: " + MusicUtil.trackLink(player.getTrack().getInfo()))
                            .build();

            event.getChannel().sendMessageEmbeds(embed).queue();

        }, err -> {
            log.error("Error fetching player for pause: {}", err.getMessage());
            event.getChannel().sendMessageEmbeds(MusicUtil.error("Could not access the player")).queue();
        });

    }

}
