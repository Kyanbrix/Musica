package com.github.kyanbrix;

import com.github.kyanbrix.utils.MusicUtil;
import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.Link;
import dev.arbjerg.lavalink.client.player.LavalinkPlayer;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceGuildMuteEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VoiceChannelListener extends ListenerAdapter {


    private static final Logger log = LoggerFactory.getLogger(VoiceChannelListener.class);
    private final MusicManager musicManager;
    private final LavalinkClient client;
    public VoiceChannelListener(MusicManager musicManager, LavalinkClient client) {
        this.musicManager = musicManager;
        this.client = client;
    }

    @Override
    public void onGuildVoiceGuildMute(@NonNull GuildVoiceGuildMuteEvent event) {
        if (event.getMember().getIdLong() != event.getJDA().getSelfUser().getIdLong()) return;

        Guild guild = event.getGuild();
        long guildId = guild.getIdLong();
        boolean muted = event.isGuildMuted();

        Link link = client.getLinkIfCached(guildId);
        if (link == null) return;

        link.getPlayer().subscribe(player -> {

            if (player.getTrack() == null || player.getPaused() == muted) return;

            player.setPaused(muted)
                    .subscribe(p -> { }, throwable -> log.error("Error (un)pausing player after mute change", throwable));

            musicManager.announce(guildId, muted
                    ? MusicUtil.error("I have been muted, I will pause the track until I have been unmuted by the admins!")
                    : new EmbedBuilder()
                        .setDescription("I have been unmuted, I will resume playing!")
                        .setColor(0xFFD700)
                        .build());
        }, err -> log.error("Error fetching player after mute change", err));

    }

    @Override
    public void onGuildVoiceUpdate(@NonNull GuildVoiceUpdateEvent event) {

        Guild guild = event.getGuild();

        if (event.getMember().getIdLong() == event.getJDA().getSelfUser().getIdLong()) {
            handleSelfUpdate(event, guild);
        } else {
            handleListenerChange(guild);
        }
    }

    private void handleSelfUpdate(GuildVoiceUpdateEvent event, Guild guild) {

        AudioChannelUnion joined = event.getChannelJoined();
        AudioChannelUnion left = event.getChannelLeft();
        long guildId = guild.getIdLong();

        if (joined != null && left == null) {

            client.getOrCreateLink(guildId).getPlayer().subscribe(lavalinkPlayer -> {

                lavalinkPlayer.setVolume(100)
                        .subscribe(p -> { }, err -> log.error("Could not set volume", err));

            }, err -> log.error("Could not fetch player on join", err));
            return;
        }


        if (joined == null && left != null) {

            if (musicManager.get(guildId) != null) {
                musicManager.announce(guildId, MusicUtil.error("⚠️ I was disconnected from the voice channel."));
            }

            musicManager.disconnectAndClean(guildId);
            return;
        }
        if (joined != null) {

            musicManager.announce(guildId, new EmbedBuilder()
                    .setDescription("⚠️ I was moved to " + joined.getAsMention())
                    .setColor(Constant.ERROR_COLOR)
                    .build());

            handleListenerChange(guild);
        }

    }

    private void handleListenerChange(Guild guild) {

        GuildVoiceState selfState = guild.getSelfMember().getVoiceState();
        if (selfState == null || !selfState.inAudioChannel()) return;

        long guildId = guild.getIdLong();
        GuildMusicManager manager = musicManager.get(guildId);
        if (manager == null) return;

        AudioChannel channel = selfState.getChannel();
        boolean alone = channel.getMembers().stream().allMatch(member -> member.getUser().isBot());

        if (alone) {
            // Don't restart a running timer on every unrelated voice update
            if (!manager.hasDisconnectTask()) musicManager.scheduleDisconnect(guildId);
            return;
        }

        Link link = client.getLinkIfCached(guildId);
        LavalinkPlayer player = link == null ? null : link.getCachedPlayer();
        if (player != null && player.getTrack() != null) {
            musicManager.cancelDisconnect(guildId);
        }
    }
}
