package com.github.kyanbrix.command;

import com.github.kyanbrix.GuildMusicManager;
import com.github.kyanbrix.GuildMusicManager.LoopMode;
import com.github.kyanbrix.MusicManager;
import com.github.kyanbrix.utils.MusicUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Locale;

public class LoopQueue implements ICommand {

    private final MusicManager musicManager;

    public LoopQueue(MusicManager musicManager) {
        this.musicManager = musicManager;
    }

    @Override
    public void execute(MessageReceivedEvent event, String args) {

        if (!assertMemberInVoice(event)) return;

        GuildMusicManager manager = musicManager.get(event.getGuild().getIdLong());

        if (manager == null) {
            event.getChannel().sendMessageEmbeds(MusicUtil.error("❌ Nothing is currently playing.")).queue();
            return;
        }

        LoopMode mode = switch (args.toLowerCase(Locale.ROOT)) {
            // No argument cycles OFF -> TRACK -> QUEUE -> OFF
            case "" -> LoopMode.values()[(manager.getLoopMode().ordinal() + 1) % LoopMode.values().length];
            case "off", "none", "disable" -> LoopMode.OFF;
            case "track", "song", "one", "current" -> LoopMode.TRACK;
            case "queue", "all", "q" -> LoopMode.QUEUE;
            default -> null;
        };

        if (mode == null) {
            replyUsage(event);
            return;
        }

        manager.setLoopMode(mode);

        event.getChannel().sendMessageEmbeds(new EmbedBuilder()
                .setDescription("Loop mode set to **" + mode.label() + "**")
                .setColor(0x9370DB)
                .build()).queue();
    }

    @Override
    public String commandName() {
        return "loop";
    }

    @Override
    public String[] aliases() {
        return new String[]{"repeat", "l"};
    }

    @Override
    public String description() {
        return "Repeats the current song or the whole queue";
    }

    @Override
    public String usage() {
        return "[off | track | queue]";
    }
}
