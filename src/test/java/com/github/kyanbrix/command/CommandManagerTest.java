package com.github.kyanbrix.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommandManagerTest {

    /** Registering the real command set must not throw, otherwise the bot fails at startup. */
    @Test
    void realCommandsHaveNoConflictingAliases() {
        CommandManager commands = new CommandManager();

        assertDoesNotThrow(() -> commands.addCommands(new PlaySong(null, null), new SkipSong(null, null),
                new Pause(null), new Resume(null), new StopSong(null), new QueueSong(null, null),
                new NowPlaying(null, null), new ShuffleQueue(null), new LoopQueue(null), new RemoveTrack(null),
                new ClearQueue(null), new SeekTrack(null), new GetLyrics(null, null),
                new SetPlayerVolume(null), new Help(commands)));

        for (ICommand command : commands.getCommands()) {
            assertFalse(command.description().isBlank(), command.commandName() + " needs a description for help");
        }
    }

    @Test
    void duplicateAliasIsRejected() {
        CommandManager commands = new CommandManager();
        commands.addCommands(new Stub("play", "p"));

        assertThrows(IllegalArgumentException.class, () -> commands.addCommands(new Stub("pause", "P")));
    }

    private record Stub(String name, String alias) implements ICommand {
        @Override
        public void execute(MessageReceivedEvent event, String args) {
        }

        @Override
        public String commandName() {
            return name;
        }

        @Override
        public String[] aliases() {
            return new String[]{alias};
        }

        @Override
        public String description() {
            return "stub";
        }
    }
}
