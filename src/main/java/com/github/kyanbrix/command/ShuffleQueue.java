package com.github.kyanbrix.command;

import com.github.kyanbrix.GuildMusicManager;
import com.github.kyanbrix.MusicManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class ShuffleQueue implements ICommand {

    private final MusicManager musicManager;
    public ShuffleQueue(MusicManager musicManager) {
        this.musicManager = musicManager;
    }

    @Override
    public void execute(MessageReceivedEvent event, String args) {

        if (assertMemberInVoice(event)) handleShuffle(event);

    }

    @Override
    public String commandName() {
        return "shuffle";
    }

    private void handleShuffle(MessageReceivedEvent event) {

        GuildMusicManager manager = musicManager.get(event.getGuild().getIdLong());

        EmbedBuilder eb = new EmbedBuilder();

        if (manager == null || manager.isQueueEmpty()) {

            eb.setDescription("The queue is empty!");
            eb.setColor(0x8B0000);
            event.getChannel().sendMessageEmbeds(eb.build()).queue();
            return;
        }

        manager.shuffleQueue();

        eb.setDescription("🔀 Shuffled **" + manager.queueSize() + "** tracks in the queue");
        eb.setColor(0xFF8C00);
        event.getChannel().sendMessageEmbeds(eb.build()).queue();


    }

    @Override
    public String[] aliases() {
        return new String[]{"sh","shuf","shuff"};
    }

    @Override
    public String description() {
        return "Shuffles the queue";
    }
}
