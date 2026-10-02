package com.github.kyanbrix.command;

import com.github.kyanbrix.GuildMusicManager;
import com.github.kyanbrix.MusicManager;
import com.github.kyanbrix.utils.MusicUtil;
import dev.arbjerg.lavalink.client.player.Track;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class RemoveTrack implements ICommand {

    private final MusicManager musicManager;

    public RemoveTrack(MusicManager musicManager) {
        this.musicManager = musicManager;
    }

    @Override
    public void execute(MessageReceivedEvent event, String args) {

        if (!assertMemberInVoice(event)) return;

        int position;
        try {
            position = Integer.parseInt(args);
        } catch (NumberFormatException e) {
            replyUsage(event);
            return;
        }

        GuildMusicManager manager = musicManager.get(event.getGuild().getIdLong());
        Track removed = manager == null ? null : manager.removeTrack(position);

        if (removed == null) {
            int size = manager == null ? 0 : manager.queueSize();
            event.getChannel().sendMessageEmbeds(MusicUtil.error(size == 0
                    ? "The queue is empty!"
                    : "❌ Pick a position between **1** and **" + size + "**.")).queue();
            return;
        }

        event.getChannel().sendMessageEmbeds(new EmbedBuilder()
                .setDescription("🗑️ Removed " + MusicUtil.trackLink(removed.getInfo()) + " from the queue")
                .setColor(0xFFFF00)
                .build()).queue();
    }

    @Override
    public String commandName() {
        return "remove";
    }

    @Override
    public String[] aliases() {
        return new String[]{"rm", "delete"};
    }

    @Override
    public String description() {
        return "Removes a song from the queue by its position";
    }

    @Override
    public String usage() {
        return "<position>";
    }
}
