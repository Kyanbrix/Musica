package com.github.kyanbrix.command;

import com.github.kyanbrix.Constant;
import com.github.kyanbrix.GuildMusicManager;
import com.github.kyanbrix.MusicManager;
import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.LavalinkNode;
import dev.arbjerg.lavalink.client.Link;
import dev.arbjerg.lavalink.client.player.*;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class PlaySong implements ICommand {
    private static final Logger log = LoggerFactory.getLogger(PlaySong.class);
    private final LavalinkClient client;
    private final MusicManager musicManager;
    private static final ExecutorService service = Executors.newFixedThreadPool(2);
    private static final Pattern URL_PATTERN = Pattern.compile(
            "^(https?|ftp)://[^\\s/$.?#].[^\\s]*$",
            Pattern.CASE_INSENSITIVE
    );

    public PlaySong(LavalinkClient client, MusicManager musicManager) {

        this.client = client;
        this.musicManager = musicManager;

    }

    @Override
    public void execute(MessageReceivedEvent event) {

        if (assertMemberInVoice(event)) handlePlay(event);


    }

    @Override
    public String commandName() {
        return "p";
    }

    @Override
    public String[] aliases() {
        return new String[]{"play","song","insert"};
    }


    private String song(String message) {

        String[] args = message.split("\\s+");

        String invokeCommand = args[0].substring(Constant.PREFIX.length());

        if (args.length == 1) {
            return "null";
        }



        StringBuilder sb = new StringBuilder();

        if (invokeCommand.equalsIgnoreCase(commandName())) {


            for (int index = 1; index < args.length; index++)  {

                sb.append(args[index]).append(" ");

            }

            return sb.toString().strip();

        }else {


            for (String alias: aliases()) {

                if (invokeCommand.equalsIgnoreCase(alias)) {
                    for (int index = 1; index < args.length; index++)  {

                        sb.append(args[index]).append(" ");

                    }

                    return sb.toString().strip();
                }

            }

        }

        return "null";

    }

    private void handlePlay(MessageReceivedEvent event) {

        String query = song(event.getMessage().getContentRaw());
        long guildId = event.getGuild().getIdLong();


        service.submit(() -> {
            event.getChannel().sendTyping().queue();

            GuildMusicManager guildMusicManager = musicManager.getOrCreate(guildId);
            guildMusicManager.setTextChannelId(event.getChannel().getIdLong());

            Link link = client.getOrCreateLink(guildId);

            String identifier = isUrl(query) ? query: "ytsearch:"+ query;

            Optional<LavalinkNode> nodeOptional = client.getNodes()
                    .stream()
                    .filter(LavalinkNode::getAvailable)
                    .findFirst();


            nodeOptional.ifPresent(System.out::println);

            if (nodeOptional.isEmpty()) {
                event.getChannel().sendMessage("No Lavalink node is available").queue();
                return;
            }

            LavalinkNode node = nodeOptional.get();


            node.loadItem(identifier)
                    .retryWhen(Retry.fixedDelay(2, Duration.ofSeconds(3)))
                    .doOnError(err -> log.error("Error loading {}",err.getMessage()))
                    .subscribe(
                    result  -> handleResult(event, link, guildMusicManager, query, result),
                    error   -> {
                        log.error("Error loading '{}': {}", query, error.getMessage());
                        event.getChannel().sendMessage("❌ An error occurred while loading the track.").queue();
                    }

            );
        });

    }


    private void handleResult(MessageReceivedEvent event, Link link, GuildMusicManager guildMusicManager, String query, LavalinkLoadResult result) {

        switch (result) {

            case TrackLoaded trackLoaded -> queueOrPlay(event, link, guildMusicManager, trackLoaded.getTrack());
            case SearchResult searchResult -> {
                List<Track> tracks = searchResult.getTracks();
                if (tracks.isEmpty()) {

                    return;
                }

                queueOrPlay(event, link, guildMusicManager, tracks.getFirst());
            }

            case PlaylistLoaded playlistLoaded -> {

                List<Track> tracks = playlistLoaded.getTracks();

                if (tracks.isEmpty()) {
                    event.getChannel().sendMessage("❌ The playlist is empty.").queue();
                    return;
                }

                String playlistName = playlistLoaded.getInfo().getName();

                link.getPlayer().subscribe(player-> {

                    if (player.getTrack() != null) {

                        tracks.forEach(guildMusicManager::enQueue);

                        MessageEmbed embed = new EmbedBuilder()
                                .setTitle("Added Playlist to Queue")
                                .addField("Playlist",String.format("[**%s**](%s)",playlistName,query),false)
                                .addField("Total Tracks",tracks.size()+" songs",false)
                                .setFooter("Requested by: "+event.getAuthor().getName(),event.getAuthor().getEffectiveAvatarUrl())
                                .build();

                        event.getChannel().sendMessageEmbeds(embed).queue();

                    } else {

                        Track track = tracks.getFirst();

                        tracks.subList(1, tracks.size()).forEach(guildMusicManager::enQueue);

                        MessageEmbed embed = new EmbedBuilder()
                                .addField("Playlist",String.format("[**%s**](%s)",playlistName,query),false)
                                .addField("Total Tracks",tracks.size()+" songs",false)
                                .setFooter("Requested by: "+event.getAuthor().getName(),event.getAuthor().getEffectiveAvatarUrl())
                                .build();

                        event.getChannel().sendMessageEmbeds(embed).queue();

                        queueOrPlay(event,link,guildMusicManager,track);


                    }

                });


            }
            case NoMatches ignored -> {
                MessageEmbed embed = new EmbedBuilder()
                        .setColor(0xB22222)
                        .setDescription("❌ No results found for: **"+query+"**")
                        .build();
                event.getChannel().sendMessageEmbeds(embed)
                    .queue();
            }

            case LoadFailed loadFailed -> {
                String reason = loadFailed.getException().getMessage();
                log.error("Load failed for '{}': {}", query, reason);

                MessageEmbed embed = new EmbedBuilder()
                        .setColor(0xB22222)
                        .setDescription("❌ Failed to load track. **Please try again!!**")
                        .build();
                event.getChannel()
                        .sendMessageEmbeds(embed)
                        .queue();
            }
            default -> {
                log.warn("Unhandled LavalinkLoadResult type: {}", result.getClass().getSimpleName());
                event.getChannel()
                        .sendMessage("❌ Unexpected response from Lavalink.")
                        .queue();
            }
        }

    }

    private void queueOrPlay(MessageReceivedEvent event, Link link, GuildMusicManager guildMusicManager , Track track) {

        link.getPlayer().subscribe(player -> {

            if (player.getTrack() != null) {


                guildMusicManager.enQueue(track);
                String emoji = sourceEmoji(track.getInfo().getSourceName());
                int color = embedColor(track.getInfo().getSourceName());

                MessageEmbed embed = new EmbedBuilder()
                        .setTitle(emoji+" Added Track")
                        .setThumbnail(track.getInfo().getArtworkUrl())
                        .addField("Song",String.format("[**%s**](%s)",track.getInfo().getTitle(),track.getInfo().getUri()),false)
                        .addField("Song Length",formatDuration(track.getInfo().getLength()),true)
                        .addField("Position in Queue",guildMusicManager.getTrackPosition(track)+"",true)
                        .addBlankField(true)
                        .setColor(color)
                        .setFooter("Requested by: "+event.getAuthor().getName(),event.getAuthor().getEffectiveAvatarUrl())
                        .setDescription("")
                                .build();

                event.getChannel().sendMessageEmbeds(embed).queue();

            } else {

                player.setTrack(track)
                        .doOnError(err -> log.error("Error setting a track {}",err.getMessage()))
                        .subscribe();

            }


        }, err -> {
            log.error("Error fetching player: {}",err.getMessage());
            event.getChannel().sendMessage("Cannot play!").queue();
        });

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

    private String sourceEmoji(String source) {

        switch (source) {

            case "spotify" -> {
                return "<:Spotify:1487793100314378330>";
            }

            case "youtube" -> {
                return "<:Youtubelogo:1487803020707234012>";
            }

            case "applemusic" -> {
                return "<:apple_music:1487793599591747654>";
            }

            case "deezer" -> {
                return "<:Deezer:1487793743968075836>";
            }
        }

        return "null";
    }

    private int embedColor(String source) {
        switch (source) {

            case "spotify" -> {
                return 0x00FF7F;
            }

            case "youtube" -> {
                return 0xFF0000;
            }

            case "applemusic" -> {
                return 0xDC143C;
            }

            case "deezer" -> {
                return 0x8B008B;
            }
        }

        return 0x708090;
    }




}
