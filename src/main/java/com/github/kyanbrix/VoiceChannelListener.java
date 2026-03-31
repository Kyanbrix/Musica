package com.github.kyanbrix;

import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.Link;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
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
        if (!event.getMember().getUser().isBot()) return;
        if (!event.getMember().getId().equals(event.getJDA().getSelfUser().getId())) return;

        Guild guild = event.getGuild();

        Link link = client.getOrCreateLink(guild.getIdLong());

        if (event.isGuildMuted()) {

            link.getPlayer().subscribe(player -> {

                if (player.getTrack() == null) {
                    return;
                }

                if (player.getPaused()) {
                    return;
                }

                player.setPaused(true).doOnError(throwable -> log.error("Error on pausing player",throwable)).subscribe();

                MessageEmbed embed = new EmbedBuilder()
                        .setDescription("I have been muted, I will pause the track until I have been unmuted from the admins!")
                        .setColor(0xB22222)
                        .build();

                long channelId = musicManager.getOrCreate(guild.getIdLong()).getTextChannelId();

                if (channelId != 0L) {

                    TextChannel channel = guild.getTextChannelById(channelId);

                    if (channel != null) {

                        channel.sendMessageEmbeds(embed).queue();

                    }else {

                        VoiceChannel voiceChannel = guild.getVoiceChannelById(channelId);

                        if (voiceChannel == null) return;

                        voiceChannel.sendMessageEmbeds(embed).queue();

                    }


                }

            });



        }else {

            link.getPlayer().subscribe(player -> {

                if (player.getTrack() == null) {
                    return;
                }

                if (!player.getPaused()) {
                    return;
                }

                player.setPaused(false)
                        .doOnError(throwable -> log.error(throwable.getMessage()))
                        .subscribe();

                long channelId = musicManager.getOrCreate(guild.getIdLong()).getTextChannelId();

                MessageEmbed embed = new EmbedBuilder()
                        .setDescription("I have been unmuted, I will resume playing!")
                        .setColor(0xFFD700)
                        .build();

                if (channelId != 0L) {

                    TextChannel channel = guild.getTextChannelById(channelId);

                    if (channel != null) {

                        channel.sendMessageEmbeds(embed).queue();

                    }else {

                        VoiceChannel voiceChannel = guild.getVoiceChannelById(channelId);

                        if (voiceChannel == null) return;

                        voiceChannel.sendMessageEmbeds(embed).queue();

                    }


                }



            });


        }

    }

    @Override
    public void onGuildVoiceUpdate(@NonNull GuildVoiceUpdateEvent event) {

        if (!event.getMember().getUser().isBot()) return;

        //Check if a user is a bot itself
        if (event.getMember().getIdLong() != event.getJDA().getSelfUser().getIdLong()) return;

        AudioChannelUnion joined = event.getChannelJoined();
        AudioChannelUnion left = event.getChannelLeft();

        Guild guild = event.getGuild();

        if (joined == null && left != null) {

            GuildMusicManager manager = musicManager.getOrCreate(event.getGuild().getIdLong());

            manager.clearQueue();
            musicManager.disconnectAndClean(event.getGuild().getIdLong());
            musicManager.remove(event.getGuild().getIdLong());

            TextChannel channel = guild.getTextChannelById(manager.getTextChannelId());

            EmbedBuilder eb = new EmbedBuilder();
            eb.setDescription("⚠️ I was disconnected from the voice channel.");
            eb.setColor(0xB22222);
            if (channel != null) {

                channel.sendMessageEmbeds(eb.build()).queue();

            }else {

                VoiceChannel voiceChannel = guild.getVoiceChannelById(manager.getTextChannelId());

                if (voiceChannel == null) return;

                voiceChannel.sendMessageEmbeds(eb.build()).queue();

            }

            return;
        }

        //Bot is moved
        if (joined != null && left != null) {

            long textChannelId = musicManager.getOrCreate(guild.getIdLong()).getTextChannelId();

            if (textChannelId != 0L) {
                TextChannel channel = guild.getTextChannelById(textChannelId);

                EmbedBuilder eb = new EmbedBuilder();
                eb.setDescription("⚠️ I was moved to "+joined.getAsMention());
                eb.setColor(0xB22222);
                if (channel != null) {

                    channel.sendMessageEmbeds(eb.build()).queue();

                }else {

                    VoiceChannel voiceChannel = guild.getVoiceChannelById(textChannelId);

                    if (voiceChannel == null) return;

                    voiceChannel.sendMessageEmbeds(eb.build()).queue();

                }

            }


        }


    }
}
