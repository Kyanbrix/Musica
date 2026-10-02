package com.github.kyanbrix;

import com.github.kyanbrix.command.*;
import com.github.kyanbrix.utils.Config;
import dev.arbjerg.lavalink.client.Helpers;
import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.NodeOptions;
import dev.arbjerg.lavalink.libraries.jda.JDAVoiceUpdateListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {

        final String token = Config.require("TOKEN");

        LavalinkClient client = new LavalinkClient(Helpers.getUserIdFromToken(token));

        client.addNode(new NodeOptions.Builder()
                .setName(Config.get("lavalink.name", "kyanbrix"))
                .setPassword(Config.require("lavalink.password"))
                .setServerUri(Config.get("lavalink.uri", "ws://127.0.0.1:2333"))
                .build()
        );


        JDA jda = JDABuilder.createLight(token,
                        GatewayIntent.GUILD_VOICE_STATES, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES)
                .enableCache(CacheFlag.VOICE_STATE)
                .setMemberCachePolicy(MemberCachePolicy.VOICE)
                .setVoiceDispatchInterceptor(new JDAVoiceUpdateListener(client))
                .setActivity(Activity.listening(Constant.PREFIX + "help / " + Constant.PREFIX + "play"))
                .build();


        MusicManager musicManager = new MusicManager(client,jda);
        LyricsClient lyricsClient = new LyricsClient();
        QueueSong queueSong = new QueueSong(client, musicManager);


        var commands = new CommandManager();

        commands.addCommands(new PlaySong(client,musicManager), new SkipSong(client,musicManager),
                new Pause(client), new Resume(client),
                new StopSong(musicManager), queueSong, new NowPlaying(client, musicManager),
                new ShuffleQueue(musicManager), new LoopQueue(musicManager), new RemoveTrack(musicManager),
                new ClearQueue(musicManager), new SeekTrack(client), new GetLyrics(client,lyricsClient),
                new SetPlayerVolume(client), new Help(commands));

        jda.addEventListener(commands, queueSong, new VoiceChannelListener(musicManager,client));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down...");
            musicManager.shutdown();
            jda.shutdown();
            client.close();
        }, "shutdown"));

    }

}
