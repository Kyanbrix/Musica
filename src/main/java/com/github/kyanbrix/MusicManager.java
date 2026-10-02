package com.github.kyanbrix;

import com.github.kyanbrix.utils.Config;
import com.github.kyanbrix.utils.MusicUtil;
import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.Link;
import dev.arbjerg.lavalink.client.event.ReadyEvent;
import dev.arbjerg.lavalink.client.event.TrackEndEvent;
import dev.arbjerg.lavalink.client.event.TrackExceptionEvent;
import dev.arbjerg.lavalink.client.event.TrackStartEvent;
import dev.arbjerg.lavalink.client.event.TrackStuckEvent;
import dev.arbjerg.lavalink.client.player.Track;
import dev.arbjerg.lavalink.protocol.v4.Message.EmittedEvent.TrackEndEvent.AudioTrackEndReason;
import dev.arbjerg.lavalink.protocol.v4.TrackInfo;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MusicManager {

    private static final Logger log = LoggerFactory.getLogger(MusicManager.class);
    private final LavalinkClient lavalinkClient;
    private final JDA jda;
    private final Map<Long, GuildMusicManager> managerMap = new ConcurrentHashMap<>();
    private final long idleTimeoutSeconds = Config.getInt("idle.timeout.seconds", 180);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "idle-disconnect");
        thread.setDaemon(true);
        return thread;
    });

    public MusicManager(LavalinkClient lavalinkClient, JDA jda) {
        this.lavalinkClient = lavalinkClient;
        this.jda = jda;

        registerLavalinkEvents();
    }


    private void registerLavalinkEvents() {

        lavalinkClient.on(ReadyEvent.class).subscribe(readyEvent ->
                log.info("Node {} is ready, session ID {}", readyEvent.getNode().getName(), readyEvent.getSessionId()));

        lavalinkClient.on(TrackStartEvent.class).subscribe(trackStartEvent -> {

            long guildId = trackStartEvent.getGuildId();
            GuildMusicManager manager = managerMap.get(guildId);

            if (manager == null) return;

            Track track = trackStartEvent.getTrack();
            TrackInfo trackInfo = track.getInfo();
            String source = trackInfo.getSourceName();

            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(MusicUtil.embedColor(source))
                    .setDescription(String.format("%s Now Playing %s `%s`",
                            MusicUtil.sourceEmoji(source), MusicUtil.trackLink(trackInfo), MusicUtil.formatDuration(trackInfo.getLength())));

            long requester = MusicUtil.getRequester(track);
            if (requester != 0L) embed.appendDescription("\nRequested by <@" + requester + ">");

            announce(guildId, embed.build());
        }, err -> log.error("Error in TrackStartEvent handler", err));


        lavalinkClient.on(TrackEndEvent.class).subscribe(trackEndEvent -> {

            if (!trackEndEvent.getEndReason().getMayStartNext()) return;

            long guildId = trackEndEvent.getGuildId();
            GuildMusicManager manager = managerMap.get(guildId);
            if (manager == null) return;

            if (trackEndEvent.getEndReason() == AudioTrackEndReason.FINISHED) {
                Track ended = trackEndEvent.getTrack();

                switch (manager.getLoopMode()) {
                    case TRACK -> {
                        play(guildId, ended.makeClone());
                        return;
                    }
                    case QUEUE -> manager.enQueue(ended.makeClone());
                    case OFF -> { }
                }
            }

            playNextTrack(guildId, manager);
        }, err -> log.error("Error in TrackEndEvent handler", err));


        // Lavalink follows an exception with a TrackEndEvent (LOAD_FAILED), which advances the queue.
        // Advancing here as well would silently skip the next song.
        lavalinkClient.on(TrackExceptionEvent.class).subscribe(trackExceptionEvent -> {

            log.error("[Guild {}] Track exception on '{}': {}",
                    trackExceptionEvent.getGuildId(),
                    trackExceptionEvent.getTrack().getInfo().getTitle(),
                    trackExceptionEvent.getException().getMessage());

            announce(trackExceptionEvent.getGuildId(), MusicUtil.error(String.format("⚠️ Could not play %s, skipping it.",
                    MusicUtil.trackLink(trackExceptionEvent.getTrack().getInfo()))));
        }, err -> log.error("Error in TrackExceptionEvent handler", err));


        lavalinkClient.on(TrackStuckEvent.class).subscribe(trackStuckEvent -> {

            long guildId = trackStuckEvent.getGuildId();
            log.warn("[Guild {}] Track '{}' got stuck for {}ms", guildId,
                    trackStuckEvent.getTrack().getInfo().getTitle(), trackStuckEvent.getThresholdMs());

            GuildMusicManager manager = managerMap.get(guildId);
            if (manager == null) return;

            announce(guildId, MusicUtil.error(String.format("⚠️ %s got stuck, skipping it.",
                    MusicUtil.trackLink(trackStuckEvent.getTrack().getInfo()))));
            playNextTrack(guildId, manager);
        }, err -> log.error("Error in TrackStuckEvent handler", err));
    }


    public void playNextTrack(long guildId, GuildMusicManager guildMusicManager) {

        Track nextTrack = guildMusicManager.pollNext();

        if (nextTrack == null) {

            MessageEmbed embed = new EmbedBuilder()
                    .setDescription(String.format("There are no more tracks to play! I will leave in %s if nothing is added.",
                            MusicUtil.formatDuration(idleTimeoutSeconds * 1000)))
                    .setColor(Constant.ERROR_COLOR)
                    .build();

            announce(guildId, embed);
            scheduleDisconnect(guildId);
            return;
        }

        play(guildId, nextTrack);
    }

    public void play(long guildId, Track track) {

        lavalinkClient.getOrCreateLink(guildId)
                .createOrUpdatePlayer()
                .setTrack(track)
                .subscribe(
                        player -> { },
                        err -> {
                            log.error("[Guild {}] Error starting '{}': {}", guildId, track.getInfo().getTitle(), err.getMessage());
                            announce(guildId, MusicUtil.error("❌ Could not start playback. Please try again!"));
                        }
                );
    }

    /** Joins the channel self-deafened (no permission needed, unlike server-deafening). */
    public void connect(AudioChannel channel) {
        channel.getGuild().getAudioManager().setSelfDeafened(true);
        jda.getDirectAudioController().connect(channel);
    }

    /** Stops playback, forgets the queue and leaves the voice channel. Safe to call more than once. */
    public void disconnectAndClean(long guildId) {

        GuildMusicManager manager = managerMap.remove(guildId);
        if (manager != null) {
            manager.cancelDisconnectTask();
            manager.clearQueue();
        }

        Link link = lavalinkClient.getLinkIfCached(guildId);
        if (link != null) {
            link.destroy().subscribe(
                    unit -> { },
                    err -> log.error("[Guild {}] Error destroying link: {}", guildId, err.getMessage()));
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild != null && guild.getSelfMember().getVoiceState() != null
                && guild.getSelfMember().getVoiceState().inAudioChannel()) {
            jda.getDirectAudioController().disconnect(guild);
        }
    }

    /** Leaves the voice channel after the idle timeout unless {@link #cancelDisconnect(long)} is called first. */
    public void scheduleDisconnect(long guildId) {

        GuildMusicManager manager = managerMap.get(guildId);
        if (manager == null) return;

        manager.setDisconnectTask(scheduler.schedule(() -> {
            if (managerMap.get(guildId) != manager) return;

            announce(guildId, new EmbedBuilder()
                    .setDescription("👋 Left the voice channel due to inactivity.")
                    .setColor(0x708090)
                    .build());
            disconnectAndClean(guildId);
            log.info("[Guild {}] Disconnected due to inactivity.", guildId);
        }, idleTimeoutSeconds, TimeUnit.SECONDS));
    }

    public void cancelDisconnect(long guildId) {
        GuildMusicManager manager = managerMap.get(guildId);
        if (manager != null) manager.cancelDisconnectTask();
    }

    /** Sends an embed to the channel the music was last requested from. */
    public void announce(long guildId, MessageEmbed embed) {

        GuildMusicManager manager = managerMap.get(guildId);
        Guild guild = jda.getGuildById(guildId);

        if (manager == null || guild == null || manager.getTextChannelId() == 0L) return;

        // Covers text channels as well as the text chat of voice/stage channels
        GuildMessageChannel channel = guild.getChannelById(GuildMessageChannel.class, manager.getTextChannelId());

        if (channel == null || !channel.canTalk()) return;

        channel.sendMessageEmbeds(embed).queue(null, err -> log.warn("[Guild {}] Could not send message: {}", guildId, err.getMessage()));
    }

    public GuildMusicManager getOrCreate(long guildId) {

        return managerMap.computeIfAbsent(guildId, GuildMusicManager::new);
    }

    /** @return the guild's manager, or null if nothing has been played there */
    public GuildMusicManager get(long guildId) {

        return managerMap.get(guildId);
    }

    /** Forgets the guild's queue without touching the voice connection; prefer {@link #disconnectAndClean(long)}. */
    public void remove(long guildId) {

        GuildMusicManager manager = managerMap.remove(guildId);
        if (manager != null) manager.cancelDisconnectTask();
    }

    public long getIdleTimeoutSeconds() {
        return idleTimeoutSeconds;
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}
