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

public class Resume implements ICommand {

    private static final Logger log = LoggerFactory.getLogger(Resume.class);
    private final LavalinkClient client;

    public Resume(LavalinkClient client) {
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

                    if (assertMemberInVoice(event)) handleResume(event);

                }

            }

        });

    }

    @Override
    public String commandName() {
        return "resume";
    }

    @Override
    public String[] aliases() {
        return new String[]{"rs"};
    }

    private void handleResume(MessageReceivedEvent event) {

        long guildId = event.getGuild().getIdLong();
        Link link = client.getOrCreateLink(guildId);

        link.getPlayer().subscribe(player -> {

            EmbedBuilder eb = new EmbedBuilder();
            if (player.getTrack() == null) {
                eb.setDescription("❌ Nothing is currently playing");
                event.getChannel().sendMessageEmbeds(eb.build()).queue();

                return;
            }
            if (!player.getPaused()) {
                eb.setDescription("▶️ Already playing. Use `?pause` to pause the song.");
                event.getChannel().sendMessageEmbeds(eb.build()).queue();
                return;
            }

            player.setPaused(false)
                    .doOnError(err -> log.error("Error resuming player: {}", err.getMessage()))
                    .subscribe();

            eb.setDescription(String.format("▶️ Resumed: [**%s**](%s)",player.getTrack().getInfo().getTitle(),player.getTrack().getInfo().getUri()));
            eb.setColor(0xFFA500);
            event.getChannel().sendMessageEmbeds(eb.build()).queue();
        }, err -> {
            log.error("Error fetching player for resume: {}", err.getMessage());
            event.getChannel().sendMessage("Could not access the player").queue();
        });
    }
}
