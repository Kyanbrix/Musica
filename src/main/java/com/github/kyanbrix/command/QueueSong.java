package com.github.kyanbrix.command;

import com.github.kyanbrix.GuildMusicManager;
import com.github.kyanbrix.MusicManager;
import com.github.kyanbrix.utils.MusicUtil;
import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.Link;
import dev.arbjerg.lavalink.client.player.LavalinkPlayer;
import dev.arbjerg.lavalink.client.player.Track;
import dev.arbjerg.lavalink.protocol.v4.TrackInfo;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.List;

/** Shows the queue. Also a listener, because it handles its own page buttons. */
public class QueueSong extends ListenerAdapter implements ICommand {
    private static final String BUTTON_PREFIX = "queue_page:";
    private static final int PAGE_SIZE = 10;
    private final LavalinkClient client;
    private final MusicManager musicManager;

    public QueueSong(LavalinkClient client, MusicManager musicManager) {
        this.client = client;
        this.musicManager = musicManager;
    }


    @Override
    public void execute(MessageReceivedEvent event, String args) {

        long guildId = event.getGuild().getIdLong();
        GuildMusicManager guildMusicManager = musicManager.get(guildId);
        Track currentTrack = currentTrack(guildId);
        List<Track> queue = guildMusicManager == null ? List.of() : guildMusicManager.getTrackQueue();

        if (currentTrack == null && queue.isEmpty()) {
            event.getChannel().sendMessageEmbeds(MusicUtil.error("The queue is empty!")).queue();
            return;
        }

        int page = 0;
        if (!args.isEmpty()) {
            try {
                page = clampPage(Integer.parseInt(args) - 1, queue.size());
            } catch (NumberFormatException ignored) {
                // Not a page number, just show the first page
            }
        }

        List<Button> buttons = buildPageButtons(page, queue.size());
        var action = event.getChannel().sendMessageEmbeds(queueEmbed(currentTrack, queue, page, guildMusicManager));

        if (!buttons.isEmpty()) action.setComponents(ActionRow.of(buttons));
        action.queue();
    }

    @Override
    public void onButtonInteraction(@NonNull ButtonInteractionEvent event) {

        // getComponentId() is the ID we gave the button; getId() would be the interaction ID
        String id = event.getComponentId();
        if (!id.startsWith(BUTTON_PREFIX) || event.getGuild() == null) return;

        int requestedPage;
        try {
            requestedPage = Integer.parseInt(id.substring(BUTTON_PREFIX.length()));
        } catch (NumberFormatException e) {
            return;
        }

        long guildId = event.getGuild().getIdLong();
        GuildMusicManager guildMusicManager = musicManager.get(guildId);
        List<Track> queue = guildMusicManager == null ? List.of() : guildMusicManager.getTrackQueue();

        // The queue may have changed since the message was sent, so clamp to what exists now
        int page = clampPage(requestedPage, queue.size());
        MessageEmbed embed = queueEmbed(currentTrack(guildId), queue, page, guildMusicManager);
        List<Button> buttons = buildPageButtons(page, queue.size());

        if (buttons.isEmpty()) {
            event.editMessageEmbeds(embed).setComponents().queue();
        } else {
            event.editMessageEmbeds(embed).setComponents(ActionRow.of(buttons)).queue();
        }
    }

    @Override
    public String commandName() {
        return "q";
    }

    @Override
    public String[] aliases() {
        return new String[]{"playlist","tracks","queue"};
    }

    @Override
    public String description() {
        return "Shows the songs in the queue";
    }

    @Override
    public String usage() {
        return "[page]";
    }

    private Track currentTrack(long guildId) {
        Link link = client.getLinkIfCached(guildId);
        LavalinkPlayer player = link == null ? null : link.getCachedPlayer();
        return player == null ? null : player.getTrack();
    }

    private MessageEmbed queueEmbed(Track currentTrack, List<Track> queue, int page, GuildMusicManager guildMusicManager) {

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setColor(0x1DB954);
        embedBuilder.setTitle("📋 Queue");

        if (currentTrack != null) {

            TrackInfo trackInfo = currentTrack.getInfo();
            embedBuilder.addField("Now Playing",String.format("%s `%s`",MusicUtil.trackLink(trackInfo),MusicUtil.formatDuration(trackInfo.getLength())),false);

        }

        long totalLength = queue.stream().mapToLong(track -> track.getInfo().getLength()).sum();
        String loop = guildMusicManager == null ? GuildMusicManager.LoopMode.OFF.label() : guildMusicManager.getLoopMode().label();

        if (queue.isEmpty()) {
            embedBuilder.addField("Up Next","No tracks in queue",false);
            embedBuilder.setFooter("Loop: " + loop);
        }else {
            int start = page * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, queue.size());

            StringBuilder sb = new StringBuilder();

            for (int i = start; i < end; i++) {
                TrackInfo info = queue.get(i).getInfo();
                sb.append(String.format("`%d.` %s `%s`\n",i+1,MusicUtil.trackLink(info),MusicUtil.formatDuration(info.getLength())));

            }

            // The description holds 4096 characters, a field only 1024 which 10 links can exceed
            embedBuilder.setDescription("**Up Next**\n" + sb);
            embedBuilder.setFooter(String.format("Page %d/%d • %d track(s) • %s total • Loop: %s",
                    page + 1, totalPages(queue.size()), queue.size(), MusicUtil.formatDuration(totalLength), loop));

        }

        return embedBuilder.build();

    }

    private List<Button> buildPageButtons(int page, int queueSize) {
        int totalPages = totalPages(queueSize);
        if (totalPages <= 1) return Collections.emptyList();

        // IDs carry the target page; page-1 and page+1 always differ, so the IDs are unique
        Button prev = Button.secondary(BUTTON_PREFIX + (page - 1), "◀  Previous")
                .withDisabled(page == 0);
        Button next = Button.secondary(BUTTON_PREFIX + (page + 1), "Next  ▶")
                .withDisabled(page >= totalPages - 1);
        return List.of(prev, next);
    }

    private int totalPages(int queueSize) {
        return Math.max(1, (queueSize + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private int clampPage(int page, int queueSize) {
        return Math.max(0, Math.min(page, totalPages(queueSize) - 1));
    }

}
