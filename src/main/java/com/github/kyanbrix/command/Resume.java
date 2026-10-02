package com.github.kyanbrix.command;

import com.github.kyanbrix.Constant;
import com.github.kyanbrix.utils.MusicUtil;
import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.Link;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Resume implements ICommand {

    private static final Logger log = LoggerFactory.getLogger(Resume.class);
    private final LavalinkClient client;

    public Resume(LavalinkClient client) {
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

        if (assertMemberInVoice(event)) handleResume(event);

    }

    @Override
    public String commandName() {
        return "resume";
    }

    @Override
    public String[] aliases() {
        return new String[]{"rs", "unpause"};
    }

    @Override
    public String description() {
        return "Resumes the paused song";
    }

    private void handleResume(MessageReceivedEvent event) {

        Link link = client.getLinkIfCached(event.getGuild().getIdLong());

        if (link == null) {
            event.getChannel().sendMessageEmbeds(MusicUtil.error("❌ Nothing is currently playing")).queue();
            return;
        }

        link.getPlayer().subscribe(player -> {

            EmbedBuilder eb = new EmbedBuilder();
            if (player.getTrack() == null) {
                event.getChannel().sendMessageEmbeds(MusicUtil.error("❌ Nothing is currently playing")).queue();

                return;
            }
            if (!player.getPaused()) {
                eb.setDescription(String.format("▶️ Already playing. Use `%spause` to pause the song.", Constant.PREFIX));
                event.getChannel().sendMessageEmbeds(eb.build()).queue();
                return;
            }

            player.setPaused(false)
                    .subscribe(p -> { }, err -> log.error("Error resuming player: {}", err.getMessage()));

            eb.setDescription("▶️ Resumed: " + MusicUtil.trackLink(player.getTrack().getInfo()));
            eb.setColor(0xFFA500);
            event.getChannel().sendMessageEmbeds(eb.build()).queue();
        }, err -> {
            log.error("Error fetching player for resume: {}", err.getMessage());
            event.getChannel().sendMessageEmbeds(MusicUtil.error("Could not access the player")).queue();
        });
    }
}
