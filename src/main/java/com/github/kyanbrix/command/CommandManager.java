package com.github.kyanbrix.command;

import com.github.kyanbrix.Constant;
import com.github.kyanbrix.utils.MusicUtil;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CommandManager extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(CommandManager.class);

    // Name and every alias point to the command, so a lookup is a single map access
    private final Map<String, ICommand> lookup = new ConcurrentHashMap<>();
    private final List<ICommand> commands = new ArrayList<>();

    @Override
    public void onMessageReceived(@NonNull MessageReceivedEvent event) {

        if (event.getAuthor().isBot() || !event.isFromGuild()) return;

        final String PREFIX = Constant.PREFIX;
        String message = event.getMessage().getContentRaw().strip();

        if (!message.startsWith(PREFIX)) return;

        // Keep the original case of the arguments (URLs are case-sensitive), only the command name is lowered
        String[] parts = message.substring(PREFIX.length()).split("\\s+", 2);
        String msgCommand = parts[0].toLowerCase(Locale.ROOT);
        String args = parts.length > 1 ? parts[1].strip() : "";

        ICommand command = lookup.get(msgCommand);
        if (command == null) return;

        try {
            command.execute(event, args);
        } catch (Exception e) {
            log.error("Command '{}' failed for message '{}'", msgCommand, message, e);
            event.getChannel().sendMessageEmbeds(MusicUtil.error("❌ Something went wrong while running that command.")).queue();
        }
    }

    public void addCommands(ICommand ... iCommands) {

        for (ICommand iCommand : iCommands) {

            register(iCommand.commandName(), iCommand);

            for (String alias : iCommand.aliases()) {
                if (!alias.equalsIgnoreCase(iCommand.commandName())) register(alias, iCommand);
            }

            commands.add(iCommand);
        }

    }

    private void register(String name, ICommand iCommand) {

        ICommand existing = lookup.putIfAbsent(name.toLowerCase(Locale.ROOT), iCommand);

        if (existing != null) {
            throw new IllegalArgumentException("Duplicate command name or alias '" + name + "' used by "
                    + existing.getClass().getSimpleName() + " and " + iCommand.getClass().getSimpleName());
        }
    }

    public List<ICommand> getCommands() {
        return Collections.unmodifiableList(commands);
    }

}
