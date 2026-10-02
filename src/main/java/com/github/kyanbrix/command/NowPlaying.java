package com.github.kyanbrix.command;

import com.github.kyanbrix.GuildMusicManager;
import com.github.kyanbrix.MusicManager;
import com.github.kyanbrix.utils.MusicUtil;
import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.Link;
import dev.arbjerg.lavalink.client.player.Track;
import dev.arbjerg.lavalink.protocol.v4.TrackInfo;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NowPlaying implements ICommand {

    private static final Logger log = LoggerFactory.getLogger(NowPlaying.class);
    private final LavalinkClient client;
    private final MusicManager musicManager;

    public NowPlaying(LavalinkClient client, MusicManager musicManager) {
        this.client = client;
        this.musicManager = musicManager;
    }

    @Override
    public void execute(MessageReceivedEvent event, String args) {

        long guildId = event.getGuild().getIdLong();
        Link link = client.getLinkIfCached(guildId);

        if (link == null) {
            event.getChannel().sendMessageEmbeds(MusicUtil.error("❌ Nothing is currently playing.")).queue();
            return;
        }

        // Fetch the player fresh so the position is up to date
        link.getPlayer().subscribe(player -> {

            Track track = player.getTrack();

            if (track == null) {
                event.getChannel().sendMessageEmbeds(MusicUtil.error("❌ Nothing is currently playing.")).queue();
                return;
            }

            TrackInfo info = track.getInfo();
            GuildMusicManager manager = musicManager.get(guildId);
            long requester = MusicUtil.getRequester(track);

            String progress = info.isStream()
                    ? "🔴 LIVE"
                    : String.format("%s\n`%s / %s`", MusicUtil.progressBar(player.getPosition(), info.getLength()),
                        MusicUtil.formatDuration(player.getPosition()), MusicUtil.formatDuration(info.getLength()));

            EmbedBuilder embed = new EmbedBuilder()
                    .setAuthor(player.getPaused() ? "⏸️ Paused" : "▶️ Now Playing")
                    .setTitle(MusicUtil.truncate(info.getTitle(), 250), info.getUri())
                    .setThumbnail(info.getArtworkUrl())
                    .setColor(MusicUtil.embedColor(info.getSourceName()))
                    .setDescription(progress)
                    .addField("Author", info.getAuthor(), true)
                    .addField("Volume", player.getVolume() + "%", true)
                    .addField("Loop", manager == null ? GuildMusicManager.LoopMode.OFF.label() : manager.getLoopMode().label(), true);

            if (requester != 0L) embed.addField("Requested by", "<@" + requester + ">", true);
            if (manager != null) embed.setFooter(manager.queueSize() + " track(s) in queue");

            event.getChannel().sendMessageEmbeds(embed.build()).queue();

        }, err -> {
            log.error("Error fetching player for now playing: {}", err.getMessage());
            event.getChannel().sendMessageEmbeds(MusicUtil.error("Could not access the player")).queue();
        });
    }

    @Override
    public String commandName() {
        return "np";
    }

    @Override
    public String[] aliases() {
        return new String[]{"nowplaying", "current", "now"};
    }

    @Override
    public String description() {
        return "Shows the current song and its progress";
    }
}
