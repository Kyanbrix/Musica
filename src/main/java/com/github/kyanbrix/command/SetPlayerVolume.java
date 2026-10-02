package com.github.kyanbrix.command;

import com.github.kyanbrix.utils.MusicUtil;
import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.Link;
import dev.arbjerg.lavalink.client.player.LavalinkPlayer;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SetPlayerVolume implements ICommand {

    private static final Logger log = LoggerFactory.getLogger(SetPlayerVolume.class);
    private final LavalinkClient client;

    public SetPlayerVolume(LavalinkClient client) {
        this.client = client;
    }


    @Override
    public void execute(MessageReceivedEvent event, String args) {

        if (assertMemberInVoice(event)) handleVolume(event, args);


    }

    @Override
    public String commandName() {
        return "v";
    }

    @Override
    public String[] aliases() {
        return new String[]{"volume","sound","vol"};
    }

    @Override
    public String description() {
        return "Shows or sets the volume (0-200, normal is 100)";
    }

    @Override
    public String usage() {
        return "[0-200]";
    }

    private String buildVolumeBar(int volume) {
        int filled = (int) Math.round(volume / 10.0); // 0-20 blocks for 0-200
        filled = Math.min(filled, 20);
        int empty  = 20 - filled;
        return "▐" + "█".repeat(filled) + "░".repeat(empty) + "▌";
    }

    private String volumeEmoji(int volume) {
        if (volume == 0)   return "🔇";
        if (volume <= 30)  return "🔈";
        if (volume <= 70)  return "🔉";
        return "🔊";
    }

    private void handleVolume(MessageReceivedEvent event, String args) {

        Link link = client.getLinkIfCached(event.getGuild().getIdLong());

        if (link == null) {
            event.getChannel().sendMessageEmbeds(MusicUtil.error("❌ Nothing is currently playing.")).queue();
            return;
        }

        // No argument: show the current volume
        if (args.isEmpty()) {
            LavalinkPlayer player = link.getCachedPlayer();
            int volume = player == null ? 100 : player.getVolume();

            event.getChannel().sendMessageEmbeds(new EmbedBuilder()
                    .setDescription(String.format("%s **Current volume: %d%%**\n%s", volumeEmoji(volume), volume, buildVolumeBar(volume)))
                    .setColor(0x90EE90)
                    .build()).queue();
            return;
        }

        int setVolume;
        try {
            setVolume = Integer.parseInt(args.replace("%", "").strip());
        }catch (NumberFormatException e) {
            replyUsage(event);
            return;
        }

        if (setVolume < 0 || setVolume > 200) {
            event.getChannel().sendMessageEmbeds(MusicUtil.error("❌ Volume must be between **0** and **200**. Normal is **100**.")).queue();
            return;
        }

        link.createOrUpdatePlayer()
                .setVolume(setVolume)
                .subscribe(lavalinkPlayer -> {

                    EmbedBuilder builder = new EmbedBuilder();
                    String bar = buildVolumeBar(setVolume);
                    builder.setDescription(String.format("%s **Volume set to %d%%**\n%s",volumeEmoji(setVolume),setVolume,bar));
                    builder.setColor(0x90EE90);
                    event.getChannel().sendMessageEmbeds(builder.build()).queue();

                },err-> {
                    log.error("Error setting a volume", err);
                    event.getChannel().sendMessageEmbeds(MusicUtil.error("Could not set volume")).queue();
                });

    }
}
