package com.github.kyanbrix.command;

import com.github.kyanbrix.utils.MusicUtil;
import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.Link;
import dev.arbjerg.lavalink.client.player.LavalinkPlayer;
import dev.arbjerg.lavalink.client.player.Track;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SeekTrack implements ICommand {

    private static final Logger log = LoggerFactory.getLogger(SeekTrack.class);
    private final LavalinkClient client;

    public SeekTrack(LavalinkClient client) {
        this.client = client;
    }

    @Override
    public void execute(MessageReceivedEvent event, String args) {

        if (!assertMemberInVoice(event)) return;

        long position = MusicUtil.parseDuration(args);
        if (args.isEmpty() || position < 0) {
            replyUsage(event);
            return;
        }

        Link link = client.getLinkIfCached(event.getGuild().getIdLong());
        LavalinkPlayer player = link == null ? null : link.getCachedPlayer();
        Track track = player == null ? null : player.getTrack();

        if (track == null) {
            event.getChannel().sendMessageEmbeds(MusicUtil.error("❌ Nothing is currently playing.")).queue();
            return;
        }

        if (!track.getInfo().isSeekable()) {
            event.getChannel().sendMessageEmbeds(MusicUtil.error("❌ This track can't be seeked.")).queue();
            return;
        }

        long length = track.getInfo().getLength();
        if (position >= length) {
            event.getChannel().sendMessageEmbeds(MusicUtil.error("❌ The song is only **" + MusicUtil.formatDuration(length) + "** long.")).queue();
            return;
        }

        link.createOrUpdatePlayer()
                .setPosition(position)
                .subscribe(p -> event.getChannel().sendMessageEmbeds(new EmbedBuilder()
                        .setDescription(String.format("⏩ Moved to `%s / %s`", MusicUtil.formatDuration(position), MusicUtil.formatDuration(length)))
                        .setColor(0x87CEEB)
                        .build()).queue(),
                        err -> {
                            log.error("Error seeking: {}", err.getMessage());
                            event.getChannel().sendMessageEmbeds(MusicUtil.error("Could not seek the track")).queue();
                        });
    }

    @Override
    public String commandName() {
        return "seek";
    }

    @Override
    public String[] aliases() {
        return new String[]{"jump"};
    }

    @Override
    public String description() {
        return "Jumps to a time in the current song";
    }

    @Override
    public String usage() {
        return "<time, e.g. 1:30 or 90>";
    }
}
