package com.github.kyanbrix;

import com.github.kyanbrix.command.*;
import com.sedmelluq.discord.lavaplayer.jdaudp.NativeAudioSendFactory;
import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.LavalinkNode;
import dev.arbjerg.lavalink.client.NodeOptions;
import dev.arbjerg.lavalink.libraries.jda.JDAVoiceUpdateListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.audio.AudioModuleConfig;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {

    private static final long CLIENT_ID = 1487388210027040929L;
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {



        LavalinkClient client = new LavalinkClient(CLIENT_ID);

        client.addNode(new NodeOptions.Builder()
                .setName("kyanbrix")
                .setPassword("")
                .setServerUri("")
                .build()
        );


        NativeAudioSendFactory audioSendFactory = new NativeAudioSendFactory();

        AudioModuleConfig audioModuleConfig = new AudioModuleConfig()
                .withAudioSendFactory(audioSendFactory);

        JDA jda = JDABuilder.createLight(System.getenv("TOKEN"),
                        GatewayIntent.GUILD_VOICE_STATES, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES)
                .enableCache(CacheFlag.VOICE_STATE)
                .setVoiceDispatchInterceptor(new JDAVoiceUpdateListener(client))
                .setAudioModuleConfig(audioModuleConfig)
                .setActivity(Activity.listening("? prefix"))
                .build();


        MusicManager musicManager = new MusicManager(client,jda);
        LyricsClient lyricsClient = new LyricsClient();


        var commands = new CommandManager();

        commands.addCommands(new PlaySong(client,musicManager), new SkipSong(client,musicManager),
                new Pause(client), new Resume(client),
                new StopSong(client,musicManager),new ShuffleQueue(musicManager),
                new ClearQueue(musicManager), new GetLyrics(client,lyricsClient));

        jda.addEventListener(commands, new VoiceChannelListener(musicManager,client));



    }


}