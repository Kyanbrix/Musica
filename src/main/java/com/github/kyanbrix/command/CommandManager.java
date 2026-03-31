package com.github.kyanbrix.command;

import com.github.kyanbrix.Constant;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CommandManager extends ListenerAdapter {

    private final Map<String, ICommand> commands = new ConcurrentHashMap<>();

    @Override
    public void onMessageReceived(@NonNull MessageReceivedEvent event) {

        if (event.getAuthor().isBot()) return;

        String message = event.getMessage().getContentRaw().toLowerCase();
        final String PREFIX = Constant.PREFIX;

        if (message.startsWith(PREFIX)) {

            String[] args = message.split("\\s+");
            String msgCommand = args[0].substring(PREFIX.length());

            for (Map.Entry<String, ICommand> command : commands.entrySet()) {

                ICommand iCommand = command.getValue();

                if (msgCommand.equals(command.getKey())) {
                    iCommand.execute(event);
                    return;
                }else {

                    for (String alias: iCommand.aliases()) {

                        if (msgCommand.equals(alias)) {
                            iCommand.execute(event);
                            return;
                        }

                    }

                }


            }



        }



    }

    public void addCommands(ICommand ... iCommands) {

        for (ICommand iCommand : iCommands) {

            if (commands.containsKey(iCommand.commandName())) {
                throw new IllegalArgumentException("Duplicate Command ID");
            }

            commands.put(iCommand.commandName(),iCommand);

        }

    }

}
