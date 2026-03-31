package com.github.kyanbrix.command;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface ICommand {

    void execute(MessageReceivedEvent event);

    String commandName();

    default void deleteMessage(Message message) {
        message.delete().queue();
    }

    default String[] aliases() {
        return new String[]{};
    }

    default boolean assertMemberInVoice(MessageReceivedEvent event) {

        Member member = event.getMember();

        if (member == null) {
            throw new IllegalArgumentException("Member is null");
        }

        GuildVoiceState guildVoiceState = event.getMember().getVoiceState();

        if (guildVoiceState != null) {

            if (!guildVoiceState.inAudioChannel()) {
                MessageEmbed embed = new EmbedBuilder()
                        .setAuthor(member.getEffectiveName(),null,member.getUser().getEffectiveAvatarUrl())
                        .setColor(0x8B0000)
                        .setDescription("You need to be in a voice channel to use this command!")
                        .build();

                event.getChannel().sendMessageEmbeds(embed).queue();
                return false;

            }
        }

        event.getJDA().getDirectAudioController().connect(guildVoiceState.getChannel());
        return true;
    }

}
