package com.github.kyanbrix;

import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.LavalinkNode;
import dev.arbjerg.lavalink.client.Link;
import dev.arbjerg.lavalink.client.event.ReadyEvent;
import dev.arbjerg.lavalink.client.event.TrackEndEvent;
import dev.arbjerg.lavalink.client.event.TrackExceptionEvent;
import dev.arbjerg.lavalink.client.event.TrackStartEvent;
import dev.arbjerg.lavalink.client.player.Track;
import dev.arbjerg.lavalink.protocol.v4.TrackInfo;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.unions.ChannelUnion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MusicManager {

    private static final Logger log = LoggerFactory.getLogger(MusicManager.class);
    private final LavalinkClient lavalinkClient;
    private final JDA jda;
    private final Map<Long, GuildMusicManager> managerMap = new ConcurrentHashMap<>();

    public MusicManager(LavalinkClient lavalinkClient, JDA jda) {
        this.lavalinkClient = lavalinkClient;
        this.jda = jda;

        registerLavalinkEvents();
    }



    private void registerLavalinkEvents() {

        lavalinkClient.on(ReadyEvent.class).subscribe(readyEvent -> {

            final LavalinkNode node = readyEvent.getNode();

            log.info("Node {} is ready, session ID {}",node,readyEvent.getSessionId());

        });

        lavalinkClient.on(TrackStartEvent.class).subscribe(trackStartEvent -> {

            Guild guild = jda.getGuildById(trackStartEvent.getGuildId());

            GuildMusicManager manager = managerMap.get(trackStartEvent.getGuildId());

            if (guild == null) return;

            TrackInfo trackInfo = trackStartEvent.getTrack().getInfo();
            String source = trackInfo.getSourceName();

            String emoji = sourceEmoji(source);
            int color = embedColor(source);

            MessageEmbed embed = new EmbedBuilder()
                    .setColor(color)
                    .setDescription(String.format("%s Now Playing [**%s**](%s)",emoji,trackInfo.getTitle(),trackInfo.getUri()))
                    .build();

            long id = manager.getTextChannelId();

            TextChannel textChannel = guild.getTextChannelById(id);

            if (textChannel != null) {

                textChannel.sendMessageEmbeds(embed).queue();

            }else {
                VoiceChannel channel = guild.getVoiceChannelById(manager.getTextChannelId());

                if (channel == null) return;

                channel.sendMessageEmbeds(embed).queue();
            }


        });


        lavalinkClient.on(TrackEndEvent.class).subscribe(trackEndEvent -> {
            long guildId = trackEndEvent.getGuildId();

           if (trackEndEvent.getEndReason().getMayStartNext()) {
               GuildMusicManager manager = managerMap.get(guildId);
               if (manager != null) {

                   playNextTrack(guildId,manager);
               }
           }

        });


        lavalinkClient.on(TrackExceptionEvent.class).subscribe(trackExceptionEvent -> {

            log.error("[Guild {}] Track exception on '{}': {}",
                    trackExceptionEvent.getGuildId(),
                    trackExceptionEvent.getTrack().getInfo().getTitle(),
                    trackExceptionEvent.getException().getMessage());

            GuildMusicManager guildManager = managerMap.get(trackExceptionEvent.getGuildId());

            if (guildManager != null) {
                playNextTrack(trackExceptionEvent.getGuildId(), guildManager);
            }

        });
    }


    public void playNextTrack(long guildId, GuildMusicManager guildMusicManager) {

        Track nextTrack = guildMusicManager.pollNext();

        if (nextTrack == null) {
            log.info("Null");

            return;
        }

        lavalinkClient.getOrCreateLink(guildId)
                .getPlayer()
                .flatMap(lavalinkPlayer -> lavalinkPlayer.setTrack(nextTrack))
                .doOnError(throwable -> {
                    log.error("Error Advancing queue {}",throwable.getMessage());
                }).subscribe();


    }


    public void disconnectAndClean(long guildId) {
        Link link = lavalinkClient.getOrCreateLink(guildId);

        link.destroy().subscribe();

        Guild guild = jda.getGuildById(guildId);

        if (guild != null) {
            guild.getAudioManager().closeAudioConnection();
        }


    }

    public GuildMusicManager getOrCreate(long guildId) {

        return managerMap.computeIfAbsent(guildId, GuildMusicManager::new);
    }

    public void remove(long guildId) {

        managerMap.remove(guildId);
    }

    private String sourceEmoji(String source) {

        switch (source) {

            case "spotify" -> {
                return "<:Spotify:1487793100314378330>";
            }

            case "youtube" -> {
                return "<:Youtubelogo:1487803020707234012>";
            }

            case "applemusic" -> {
                return "<:apple_music:1487793599591747654>";
            }

            case "deezer" -> {
                return "<:Deezer:1487793743968075836>";
            }
        }

        return "null";
    }

    private int embedColor(String source) {
        switch (source) {

            case "spotify" -> {
                return 0x00FF7F;
            }

            case "youtube" -> {
                return 0xFF0000;
            }

            case "applemusic" -> {
                return 0xDC143C;
            }

            case "deezer" -> {
                return 0x8B008B;
            }
        }

        return 0x708090;
    }
}
