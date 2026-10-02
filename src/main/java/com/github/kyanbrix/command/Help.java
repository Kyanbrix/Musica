package com.github.kyanbrix.command;

import com.github.kyanbrix.Constant;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class Help implements ICommand {

    private final CommandManager commandManager;

    public Help(CommandManager commandManager) {
        this.commandManager = commandManager;
    }

    @Override
    public void execute(MessageReceivedEvent event, String args) {

        StringBuilder sb = new StringBuilder();

        for (ICommand command : commandManager.getCommands()) {

            sb.append(String.format("`%s%s%s` — %s", Constant.PREFIX, command.commandName(),
                    command.usage().isEmpty() ? "" : " " + command.usage(), command.description()));

            if (command.aliases().length > 0) {
                sb.append(" *(").append(String.join(", ", command.aliases())).append(")*");
            }
            sb.append('\n');
        }

        event.getChannel().sendMessageEmbeds(new EmbedBuilder()
                .setTitle("🎵 Music Commands")
                .setDescription(sb.toString())
                .setColor(0x1DB954)
                .setFooter("<required> [optional]")
                .build()).queue();
    }

    @Override
    public String commandName() {
        return "help";
    }

    @Override
    public String[] aliases() {
        return new String[]{"h", "commands"};
    }

    @Override
    public String description() {
        return "Shows this list";
    }
}
