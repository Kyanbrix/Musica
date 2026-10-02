package com.github.kyanbrix.command;

import com.github.kyanbrix.GuildMusicManager;
import com.github.kyanbrix.MusicManager;
import com.github.kyanbrix.utils.Config;
import com.github.kyanbrix.utils.MusicUtil;
import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.LavalinkNode;
import dev.arbjerg.lavalink.client.Link;
import dev.arbjerg.lavalink.client.player.*;
import dev.arbjerg.lavalink.protocol.v4.TrackInfo;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

public class PlaySong implements ICommand {
    private static final Logger log = LoggerFactory.getLogger(PlaySong.class);
    private static final String SEARCH_PROVIDER = Config.get("search.provider", "ytsearch");
    private final LavalinkClient client;
    private final MusicManager musicManager;
    private static final Pattern URL_PATTERN = Pattern.compile(
            "^(https?|ftp)://[^\\s/$.?#].[^\\s]*$",
            Pattern.CASE_INSENSITIVE
    );

    public PlaySong(LavalinkClient client, MusicManager musicManager) {

        this.client = client;
        this.musicManager = musicManager;

    }

    @Override
    public void execute(MessageReceivedEvent event, String args) {

        if (args.isEmpty()) {
            replyUsage(event);
            return;
        }

        if (assertMemberInVoice(event)) handlePlay(event, args);


    }

    @Override
    public String commandName() {
        return "p";
    }

    @Override
    public String[] aliases() {
        return new String[]{"play","song","insert"};
    }

    @Override
    public String description() {
        return "Plays a song or playlist, or adds it to the queue";
    }

    @Override
    public String usage() {
        return "<song name or URL>";
    }

    private void handlePlay(MessageReceivedEvent event, String args) {

        // Discord users wrap links in <> to hide the embed preview
        String query = args.startsWith("<") && args.endsWith(">") ? args.substring(1, args.length() - 1) : args;
        long guildId = event.getGuild().getIdLong();

        if (client.getNodes().stream().noneMatch(LavalinkNode::getAvailable)) {
            event.getChannel().sendMessageEmbeds(MusicUtil.error("❌ The music server is not available right now. Please try again later.")).queue();
            return;
        }

        if (!connectIfNeeded(event)) return;

        event.getChannel().sendTyping().queue();

        GuildMusicManager guildMusicManager = musicManager.getOrCreate(guildId);
        guildMusicManager.setTextChannelId(event.getChannel().getIdLong());

        Link link = client.getOrCreateLink(guildId);

        String identifier = isUrl(query) ? query : SEARCH_PROVIDER + ":" + query;

        link.loadItem(identifier)
                .retryWhen(Retry.fixedDelay(2, Duration.ofSeconds(3)))
                .subscribe(
                        result -> handleResult(event, link, guildMusicManager, query, result),
                        error -> {
                            log.error("Error loading '{}': {}", query, error.getMessage());
                            event.getChannel().sendMessageEmbeds(MusicUtil.error("❌ An error occurred while loading the track.")).queue();
                        }
                );

    }

    /** @return false if the bot could not join the member's voice channel */
    private boolean connectIfNeeded(MessageReceivedEvent event) {

        Member self = event.getGuild().getSelfMember();
        if (self.getVoiceState() != null && self.getVoiceState().inAudioChannel()) return true;

        AudioChannel channel = event.getMember().getVoiceState().getChannel();

        if (!self.hasPermission(channel, Permission.VOICE_CONNECT, Permission.VOICE_SPEAK)) {
            event.getChannel().sendMessageEmbeds(MusicUtil.error("❌ I need permission to **connect** and **speak** in " + channel.getAsMention() + ".")).queue();
            return false;
        }

        musicManager.connect(channel);
        return true;
    }


    private void handleResult(MessageReceivedEvent event, Link link, GuildMusicManager guildMusicManager, String query, LavalinkLoadResult result) {

        long requesterId = event.getAuthor().getIdLong();

        switch (result) {

            case TrackLoaded trackLoaded -> queueOrPlay(event, link, guildMusicManager, trackLoaded.getTrack());
            case SearchResult searchResult -> {
                List<Track> tracks = searchResult.getTracks();
                if (tracks.isEmpty()) {
                    noMatches(event, query);
                    return;
                }

                queueOrPlay(event, link, guildMusicManager, tracks.getFirst());
            }

            case PlaylistLoaded playlistLoaded -> {

                List<Track> tracks = playlistLoaded.getTracks();

                if (tracks.isEmpty()) {
                    event.getChannel().sendMessageEmbeds(MusicUtil.error("❌ The playlist is empty.")).queue();
                    return;
                }

                tracks.forEach(track -> MusicUtil.setRequester(track, requesterId));
                musicManager.cancelDisconnect(guildMusicManager.getGuildId());

                String playlistName = MarkdownSanitizer.escape(playlistLoaded.getInfo().getName());
                long totalLength = tracks.stream().mapToLong(track -> track.getInfo().getLength()).sum();

                link.getPlayer().subscribe(player-> {

                    boolean playing = player.getTrack() != null;

                    if (playing) {
                        guildMusicManager.enQueueAll(tracks);
                    } else {
                        guildMusicManager.enQueueAll(tracks.subList(1, tracks.size()));
                        musicManager.play(guildMusicManager.getGuildId(), tracks.getFirst());
                    }

                    MessageEmbed embed = new EmbedBuilder()
                            .setTitle(playing ? "Added Playlist to Queue" : "Playing Playlist")
                            .addField("Playlist",String.format("[**%s**](%s)",playlistName,query),false)
                            .addField("Total Tracks",tracks.size()+" songs",true)
                            .addField("Total Length",MusicUtil.formatDuration(totalLength),true)
                            .setThumbnail(tracks.getFirst().getInfo().getArtworkUrl())
                            .setFooter("Requested by: "+event.getAuthor().getName(),event.getAuthor().getEffectiveAvatarUrl())
                            .build();

                    event.getChannel().sendMessageEmbeds(embed).queue();

                }, err -> playerError(event, err));


            }
            case NoMatches ignored -> noMatches(event, query);

            case LoadFailed loadFailed -> {
                String reason = loadFailed.getException().getMessage();
                log.error("Load failed for '{}': {}", query, reason);

                event.getChannel()
                        .sendMessageEmbeds(MusicUtil.error("❌ Failed to load track. **Please try again!!**"))
                        .queue();
            }
            default -> {
                log.warn("Unhandled LavalinkLoadResult type: {}", result.getClass().getSimpleName());
                event.getChannel()
                        .sendMessageEmbeds(MusicUtil.error("❌ Unexpected response from Lavalink."))
                        .queue();
            }
        }

    }

    private void queueOrPlay(MessageReceivedEvent event, Link link, GuildMusicManager guildMusicManager , Track track) {

        MusicUtil.setRequester(track, event.getAuthor().getIdLong());
        musicManager.cancelDisconnect(guildMusicManager.getGuildId());

        link.getPlayer().subscribe(player -> {

            if (player.getTrack() == null) {
                // The "Now Playing" message is sent by MusicManager when the track starts
                musicManager.play(guildMusicManager.getGuildId(), track);
                return;
            }

            int position = guildMusicManager.enQueue(track);
            TrackInfo info = track.getInfo();
            String emoji = MusicUtil.sourceEmoji(info.getSourceName());
            int color = MusicUtil.embedColor(info.getSourceName());

            MessageEmbed embed = new EmbedBuilder()
                    .setTitle(emoji+" Added Track")
                    .setThumbnail(info.getArtworkUrl())
                    .addField("Song",MusicUtil.trackLink(info),false)
                    .addField("Song Length",MusicUtil.formatDuration(info.getLength()),true)
                    .addField("Position in Queue",String.valueOf(position),true)
                    .addBlankField(true)
                    .setColor(color)
                    .setFooter("Requested by: "+event.getAuthor().getName(),event.getAuthor().getEffectiveAvatarUrl())
                    .build();

            event.getChannel().sendMessageEmbeds(embed).queue();

        }, err -> playerError(event, err));

    }

    private void noMatches(MessageReceivedEvent event, String query) {
        event.getChannel().sendMessageEmbeds(MusicUtil.error("❌ No results found for: **" + MarkdownSanitizer.escape(query) + "**")).queue();
    }

    private void playerError(MessageReceivedEvent event, Throwable err) {
        log.error("Error fetching player: {}", err.getMessage());
        event.getChannel().sendMessageEmbeds(MusicUtil.error("❌ Cannot play right now, please try again!")).queue();
    }

    private boolean isUrl(String query) {
        return URL_PATTERN.matcher(query).matches();
    }

}
