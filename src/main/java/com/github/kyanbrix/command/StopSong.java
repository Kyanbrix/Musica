package com.github.kyanbrix.command;

import com.github.kyanbrix.Constant;
import com.github.kyanbrix.MusicManager;
import com.github.kyanbrix.utils.MusicUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StopSong implements ICommand {
    private static final Logger log = LoggerFactory.getLogger(StopSong.class);
    private final MusicManager musicManager;

    public StopSong(MusicManager musicManager) {

        this.musicManager = musicManager;
    }

    @Override
    public void execute(MessageReceivedEvent event, String args) {

        if (assertMemberInVoice(event)) handleStop(event);

    }

    @Override
    public String commandName() {
        return "stop";
    }

    @Override
    public String[] aliases() {

        return new String[]{"dc","disconnect","dis","discon","disconn","leave"};
    }

    @Override
    public String description() {
        return "Stops the music, clears the queue and leaves the voice channel";
    }

    private void handleStop(MessageReceivedEvent event) {

        long guildId = event.getGuild().getIdLong();

        GuildVoiceState selfVoiceState = event.getGuild().getSelfMember().getVoiceState();
        if (selfVoiceState == null || !selfVoiceState.inAudioChannel()) {
            event.getChannel().sendMessageEmbeds(MusicUtil.error("❌ I'm not in a voice channel.")).queue();
            return;
        }

        // Clears the queue, destroys the player and leaves the voice channel
        musicManager.disconnectAndClean(guildId);
        log.info("[Guild {}] Stopped and cleaned up.", guildId);

        MessageEmbed embed = new EmbedBuilder()
                .setColor(Constant.ERROR_COLOR)
                .setAuthor("Nabunturan",null,event.getJDA().getSelfUser().getEffectiveAvatarUrl())
                .setDescription("⏹️ Thank you for using jockie nabunturan as your musician!")
                .build();

        event.getChannel().sendMessageEmbeds(embed).queue();
    }
}
