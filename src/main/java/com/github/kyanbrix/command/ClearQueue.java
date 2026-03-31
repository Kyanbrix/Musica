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
    public void execute(MessageReceivedEvent event) {

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

    private void handleClear(MessageReceivedEvent event) {

        long guildId = event.getGuild().getIdLong();

        GuildMusicManager guildMusicManager = musicManager.getOrCreate(guildId);

        guildMusicManager.clearQueue();

        MessageEmbed embed = new EmbedBuilder()
                .setColor(0xFFFF00)
                .setDescription("Tracks has been cleared")
                .build();

        event.getChannel().sendMessageEmbeds(embed).queue();


    }

}
