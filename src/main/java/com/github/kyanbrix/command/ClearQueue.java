package com.github.kyanbrix.command;

import com.github.kyanbrix.GuildMusicManager;
import com.github.kyanbrix.MusicManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;



public class ClearQueue implements ICommand {

    private final MusicManager musicManager;
    public ClearQueue(MusicManager musicManager) {
        this.musicManager = musicManager;
    }

    @Override
    public void execute(MessageReceivedEvent event, String args) {

        if (assertMemberInVoice(event)) handleClear(event);

    }

    @Override
    public String commandName() {
        return "clear";
    }

    @Override
    public String[] aliases() {
        return new String[]{"clr"};
    }

    @Override
    public String description() {
        return "Removes every song from the queue (keeps the current one playing)";
    }

    private void handleClear(MessageReceivedEvent event) {

        long guildId = event.getGuild().getIdLong();

        GuildMusicManager guildMusicManager = musicManager.get(guildId);
        int cleared = guildMusicManager == null ? 0 : guildMusicManager.queueSize();

        if (guildMusicManager != null) guildMusicManager.clearQueue();

        MessageEmbed embed = new EmbedBuilder()
                .setColor(0xFFFF00)
                .setDescription("🗑️ Cleared **" + cleared + "** track(s) from the queue")
                .build();

        event.getChannel().sendMessageEmbeds(embed).queue();


    }

}
