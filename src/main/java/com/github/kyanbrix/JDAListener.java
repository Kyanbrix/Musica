package com.github.kyanbrix;

import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.LavalinkNode;
import dev.arbjerg.lavalink.client.Link;
import dev.arbjerg.lavalink.client.player.*;
import dev.arbjerg.lavalink.protocol.v4.TrackInfo;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.guild.invite.GuildInviteCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class JDAListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(JDAListener.class);
    private final LavalinkClient client;
    private final MusicManager musicManager;
    private static final Pattern URL_PATTERN = Pattern.compile(
            "^(https?|ftp)://[^\\s/$.?#].[^\\s]*$",
            Pattern.CASE_INSENSITIVE
    );

    public JDAListener(LavalinkClient client, MusicManager musicManager) {
        this.client = client;
        this.musicManager = musicManager;
    }

    @Override
    public void onGuildInviteCreate(@NonNull GuildInviteCreateEvent event) {
    }

    @Override
    public void onButtonInteraction(@NonNull ButtonInteractionEvent event) {
        final String id = event.getId();

        if (!id.startsWith("queue_prev:") && !id.startsWith("queue_next:")) return;
        if (event.getGuild() == null) return;



        // Button ID format: "queue_prev:{page}" or "queue_next:{page}"
        String[] parts   = id.split(":");
        int currentPage  = Integer.parseInt(parts[1]);
        int requestedPage = id.startsWith("queue_next:") ? currentPage + 1 : currentPage - 1;

        long guildId = event.getGuild().getIdLong();
        GuildMusicManager guildManager = musicManager.getOrCreate(guildId);
        Link link = client.getOrCreateLink(guildId);

        // deferEdit acknowledges the click without sending a new message
        event.deferEdit().queue();

        link.getPlayer().subscribe(player -> {
            Track currentTrack = player.getTrack();
            List<Track> queue  = new ArrayList<>(guildManager.getTrackQueue());

            // Clamp to valid page range
            int totalPages = Math.max(1, (int) Math.ceil(queue.size() / (double) 10));
            int page = Math.max(0, Math.min(requestedPage, totalPages - 1));

            MessageEmbed embed  = queueEmbed(currentTrack, queue, page);
            List<Button> buttons = buildPageButtons(page, queue.size());

            if (buttons.isEmpty()) {
                event.getHook().editOriginalEmbeds(embed).setComponents().queue();
            } else {
                event.getHook().editOriginalEmbeds(embed)
                        .setComponents(ActionRow.of(buttons))
                        .queue();
            }
        }, err -> log.error("Error fetching player for queue pagination: {}", err.getMessage()));

    }

    @Override
    public void onSlashCommandInteraction(@NonNull SlashCommandInteractionEvent event) {

        Guild guild = event.getGuild();

        if (guild == null) return;

        String commandName = event.getName();

        switch (commandName) {

            case "play" -> handlePlay(event);


            case "stop" -> handleStop(event);

            case "skip" -> handleSkip(event);

            case "queue" -> handleQueue(event);

        }



    }

    @Override
    public void onReady(@NonNull ReadyEvent event) {

        event.getJDA().updateCommands().queue();
    }


    private void handlePlay(SlashCommandInteractionEvent event) {

        String query = event.getOption("song").getAsString();
        long guildId = event.getGuild().getIdLong();

        event.deferReply().queue();

        Member member = event.getMember();

        event.getJDA().getDirectAudioController().connect(member.getVoiceState().getChannel());

        GuildMusicManager guildMusicManager = musicManager.getOrCreate(guildId);
        Link link = client.getOrCreateLink(guildId);

        String identifier = isUrl(query) ? query: "ytsearch:"+ query;

        Optional<LavalinkNode> nodeOptional = client.getNodes()
                .stream()
                .filter(LavalinkNode::getAvailable)
                .findFirst();


        nodeOptional.ifPresent(System.out::println);

        if (nodeOptional.isEmpty()) {
            event.getHook().editOriginal("No Lavalink node is available")
                    .queue();

            return;
        }

        LavalinkNode node = nodeOptional.get();

        System.out.println("Slash Command: "+identifier);

        node.loadItem(identifier).subscribe(
                result  -> handleResult(event, link, guildMusicManager, query, result),
                error   -> {
                    log.error("Error loading '{}': {}", query, error.getMessage());
                    event.getHook()
                            .editOriginal("❌ An error occurred while loading the track.")
                            .queue();
                }
        );

    }


    private void handleResult(SlashCommandInteractionEvent event, Link link, GuildMusicManager guildMusicManager, String query, LavalinkLoadResult result) {


        System.out.println("Result:"+ result);

        switch (result) {

            case TrackLoaded trackLoaded -> queueOrPlay(event, link, guildMusicManager, trackLoaded.getTrack());
            case SearchResult searchResult -> {
                List<Track> tracks = searchResult.getTracks();
                if (tracks.isEmpty()) {
                    event.getHook()
                            .editOriginal("❌ No results found for: **" + query + "**")
                            .queue();
                    return;
                }
                queueOrPlay(event, link, guildMusicManager, tracks.getFirst());
            }
            case PlaylistLoaded playlistLoaded -> {

                List<Track> tracks = playlistLoaded.getTracks();
                if (tracks.isEmpty()) {
                    event.getHook().editOriginal("❌ The playlist is empty.").queue();
                    return;
                }

                // Play the first track; add the rest to the queue
                Track first = tracks.getFirst();
                tracks.subList(1, tracks.size()).forEach(guildMusicManager::enQueue);
                queueOrPlay(event, link, guildMusicManager, first);

                // Override queueOrPlay's message with a richer playlist summary
                String playlistName = playlistLoaded.getInfo().getName();
                event.getHook()
                        .editOriginal("📋 Loaded playlist **" + playlistName
                                + "** — **" + tracks.size() + "** tracks queued!")
                        .queue();

            }
            case NoMatches noMatches -> event.getHook()
                    .editOriginal("❌ No results found for: **" + query + "**")
                    .queue();
            case LoadFailed loadFailed -> {
                String reason = loadFailed.getException().getMessage();
                log.error("Load failed for '{}': {}", query, reason);
                event.getHook()
                        .editOriginal("❌ Failed to load track. Reason: " + reason)
                        .queue();
            }
            default -> {
                log.warn("Unhandled LavalinkLoadResult type: {}", result.getClass().getSimpleName());
                event.getHook()
                        .editOriginal("❌ Unexpected response from Lavalink.")
                        .queue();
            }
        }

    }

    private void handleSkip(SlashCommandInteractionEvent event) {

        long guildId = event.getGuild().getIdLong();

        GuildMusicManager guildMusicManager = musicManager.getOrCreate(guildId);
        Link link = client.getOrCreateLink(guildId);

        event.deferReply().queue();

        link.getPlayer().subscribe(lavalinkPlayer -> {

            if (lavalinkPlayer.getTrack() == null) {

                event.getHook().sendMessage("Nothing currently playing!")
                        .queue();

                return;
            }

            Track nextTrack = guildMusicManager.pollNext();

            if (nextTrack == null) {

                lavalinkPlayer.setTrack(null)
                        .doOnSuccess(v -> {
                            link.destroy().subscribe();
                            event.getGuild().getAudioManager().closeAudioConnection();
                            musicManager.remove(guildId);
                        })
                        .doOnError(err -> log.error("Error during skip-to-empty: {}", err.getMessage()))
                        .subscribe();
                event.getHook()
                        .editOriginal("⏭️ Skipped! The queue is now empty — disconnecting.")
                        .queue();

            }else {

                lavalinkPlayer.setTrack(nextTrack)
                        .doOnError(err -> log.error("Error skipping to next song {}",err.getMessage()))
                        .subscribe();

                event.getHook()
                        .editOriginalFormat("⏭\uFE0F Skipped! Now playing: **%s** ",nextTrack.getInfo().getTitle())
                        .queue();


            }

        },err -> {
            log.error("Error fetching player for skip: {}",err.getMessage());
            event.getHook().editOriginalFormat("Could not access the player").queue();
        });

    }

    private void handleStop(SlashCommandInteractionEvent event) {

        long guildId      = event.getGuild().getIdLong();
        GuildMusicManager guildManager = musicManager.getOrCreate(guildId);
        Link link = client.getOrCreateLink(guildId);

        // 1. Clear the queue immediately
        guildManager.clearQueue();

        // 2. Stop player, destroy link, close voice connection
        link.getPlayer()
                .flatMap(player -> player.setTrack(null))   // null track = stop playback
                .doOnSuccess(v -> {
                    link.destroy().subscribe();
                    event.getGuild().getAudioManager().closeAudioConnection();
                    musicManager.remove(guildId);
                    log.info("[Guild {}] Stopped and cleaned up.", guildId);
                })
                .doOnError(err -> log.error("Error during stop: {}", err.getMessage()))
                .subscribe();

        // Reply immediately (the cleanup above is async but fast)
        event.reply("⏹️ Stopped the music, cleared the queue, and disconnected.").queue();
    }

    private void handleQueue(SlashCommandInteractionEvent event) {
        long guildId = event.getGuild().getIdLong();

        event.deferReply().queue();

        GuildMusicManager guildMusicManager = musicManager.getOrCreate(guildId);

        Link link = client.getOrCreateLink(guildId);

        link.getPlayer().subscribe(lavalinkPlayer -> {

            Track currentTrack = lavalinkPlayer.getTrack();
            List<Track> queue = new ArrayList<>(guildMusicManager.getTrackQueue());


            if (currentTrack == null && queue.isEmpty()) {

                event.getHook().sendMessage("The queue is empty").queue();
                return;
            }

            MessageEmbed embed = queueEmbed(currentTrack,queue,0);
            List<Button> buttons = buildPageButtons(0,queue.size());

            if (buttons.isEmpty()) {
                event.getHook().sendMessageEmbeds(embed).queue();
            }else {
                event.getHook().sendMessageEmbeds(embed)
                        .setComponents(ActionRow.of(buttons))
                        .queue();
            }

        },err-> {
            log.error("Error fetching player for /queue: {}", err.getMessage());
            event.getHook().editOriginal("❌ Could not fetch the player.").queue();
        });


    }

    private void queueOrPlay(SlashCommandInteractionEvent event, Link link, GuildMusicManager guildMusicManager , Track track) {

        link.getPlayer().subscribe(player -> {
            String title = track.getInfo().getTitle();

            if (player.getTrack() != null) {

                guildMusicManager.enQueue(track);

                event.getHook().sendMessage("Added to Queue: "+title).queue();
            } else {

                player.setTrack(track)
                        .doOnError(err -> log.error("Error setting a track {}",err.getMessage()))
                        .subscribe();

                event.getHook().sendMessageFormat("Now Playing : **%s** Length: **%d** Author: **%s**",track.getInfo().getTitle(),track.getInfo().getLength(),track.getInfo().getAuthor()).queue();

            }


        }, err -> {
            log.error("Error fetching player: {}",err.getMessage());
            event.getHook().editOriginal("Cannot play!").queue();
        });

    }

    private MessageEmbed queueEmbed(Track currentTrack, List<Track> queue, int page) {

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setColor(0x1DB954);

        if (currentTrack != null) {

            TrackInfo trackInfo = currentTrack.getInfo();
            embedBuilder.addField("Now Playing",String.format("[%s](%s) `%s`",trackInfo.getTitle(),trackInfo.getUri(),formatDuration(trackInfo.getLength())),false);

        }


        if (queue.isEmpty()) {
            embedBuilder.addField("Up Next","No tracks in queue",false);
        }else {
            int totalPages = (int) Math.ceil(queue.size() / (double) 10);
            int start = page * 10;
            int end = Math.min(start + 10, queue.size());

            StringBuilder sb = new StringBuilder();

            for (int i = start; i < end; i++) {
                TrackInfo info = queue.get(i).getInfo();
                sb.append(String.format("'%d' %s  %s\n",i+1,info.getTitle(),formatDuration(info.getLength())));

            }

            embedBuilder.addField("📋  Up Next", sb.toString(), false);
            embedBuilder.setFooter(String.format("Page %d/%d . %d track(s) in queue",page + 1,totalPages,queue.size()));

        }

        return embedBuilder.build();

    }

    private List<Button> buildPageButtons(int page, int queueSize) {
        int totalPages = (int) Math.ceil(queueSize / (double) 10);
        if (totalPages <= 1) return Collections.emptyList();

        Button prev = Button.secondary("queue_prev:" + page, "◀  Previous")
                .withDisabled(page == 0);
        Button next = Button.secondary("queue_next:" + page, "Next  ▶")
                .withDisabled(page >= totalPages - 1);
        return List.of(prev, next);
    }



    private boolean isUrl(String query) {
        return URL_PATTERN.matcher(query).matches();
    }

    private String formatDuration(long ms) {
        if (ms == 0) return "LIVE";
        long totalSec = ms / 1000;
        long hours    = totalSec / 3600;
        long minutes  = (totalSec % 3600) / 60;
        long seconds  = totalSec % 60;
        return hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes, seconds)
                : String.format("%d:%02d", minutes, seconds);
    }
}
