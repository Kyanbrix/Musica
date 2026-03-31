package com.github.kyanbrix.command;

import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.Link;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Pause implements ICommand {

    private static final Logger log = LoggerFactory.getLogger(Pause.class);
    private final LavalinkClient client;

    public Pause(LavalinkClient client) {
        this.client = client;
    }

    @Override
    public void execute(MessageReceivedEvent event) {

        Guild guild = event.getGuild();

        guild.retrieveMemberById(event.getJDA().getSelfUser().getIdLong()).queue(member -> {

            GuildVoiceState voiceState = member.getVoiceState();

            if (voiceState != null) {

                if (member.getVoiceState().isGuildMuted()) {
                    MessageEmbed embed = new EmbedBuilder()
                            .setDescription("It appears I am muted! You need to unmute me first.")
                            .setColor(0xB22222)
                            .build();

                    event.getChannel().sendMessageEmbeds(embed).queue();

                }else {

                    if (assertMemberInVoice(event)) handlePause(event);

                }

            }

        });

    }

    @Override
    public String commandName() {
        return "pause";
    }

    @Override
    public String[] aliases() {
        return new String[]{"freeze","paws"};
    }

    private void handlePause(MessageReceivedEvent event) {

        assertMemberInVoice(event);

        long guildId = event.getGuild().getIdLong();
        Link link = client.getOrCreateLink(guildId);

        link.getPlayer().subscribe(player -> {

            if (player.getTrack() == null) {
                MessageEmbed embed = new EmbedBuilder()
                        .setColor(0xB22222)
                        .setDescription("❌ Nothing is currently playing.")
                        .build();
                event.getChannel().sendMessageEmbeds(embed).queue();
                return;
            }

            if (player.getPaused()) {
                MessageEmbed embed = new EmbedBuilder()
                        .setDescription("⏸️ Song is already paused. `!resume / !rs` to continue.")
                        .build();
                event.getChannel().sendMessageEmbeds(embed).queue();
                return;
            }

            player.setPaused(true)
                    .doOnError(err-> log.error("Error pausing a track"))
                    .subscribe();

            MessageEmbed embed = new EmbedBuilder()
                    .setAuthor(event.getAuthor().getName()+" paused the song",null,event.getAuthor().getEffectiveAvatarUrl())
                    .setDescription("⏸️ Paused: [**"+player.getTrack().getInfo().getTitle()+"**]("+player.getTrack().getInfo().getUri()+")")
                            .build();

            event.getChannel().sendMessageEmbeds(embed).queue();

        });



    }

}
