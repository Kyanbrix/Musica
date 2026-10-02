package com.github.kyanbrix.command;

import com.github.kyanbrix.Constant;
import com.github.kyanbrix.utils.MusicUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public interface ICommand {

    /**
     * @param args everything after the command name, stripped (empty if nothing was given)
     */
    void execute(MessageReceivedEvent event, String args);

    String commandName();

    default String[] aliases() {
        return new String[]{};
    }

    /** One-line explanation shown in the help command. */
    String description();

    /** Arguments shown in the help command, e.g. "&lt;song name or URL&gt;". */
    default String usage() {
        return "";
    }

    /**
     * Checks the author is in a voice channel, and in the same one as the bot if the bot is already connected,
     * so people outside the channel can't control (or steal) the music.
     */
    default boolean assertMemberInVoice(MessageReceivedEvent event) {

        Member member = event.getMember();
        if (member == null) return false;

        GuildVoiceState memberVoiceState = member.getVoiceState();

        if (memberVoiceState == null || !memberVoiceState.inAudioChannel()) {
            MessageEmbed embed = new EmbedBuilder()
                    .setAuthor(member.getEffectiveName(), null, member.getUser().getEffectiveAvatarUrl())
                    .setColor(0x8B0000)
                    .setDescription("You need to be in a voice channel to use this command!")
                    .build();

            event.getChannel().sendMessageEmbeds(embed).queue();
            return false;
        }

        GuildVoiceState selfVoiceState = event.getGuild().getSelfMember().getVoiceState();
        AudioChannel botChannel = selfVoiceState == null ? null : selfVoiceState.getChannel();

        if (botChannel != null && !botChannel.equals(memberVoiceState.getChannel())) {
            event.getChannel().sendMessageEmbeds(MusicUtil.error("You need to be in " + botChannel.getAsMention() + " to use this command!")).queue();
            return false;
        }

        return true;
    }

    default void replyUsage(MessageReceivedEvent event) {
        event.getChannel().sendMessageEmbeds(MusicUtil.error(
                String.format("Usage: `%s%s %s`", Constant.PREFIX, commandName(), usage()))).queue();
    }

}
