package com.github.kyanbrix;

import dev.arbjerg.lavalink.client.player.Track;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class GuildMusicManager {

    private final long guildId;
    private final Queue<Track> trackQueue = new ConcurrentLinkedQueue<>();
    private long textChannelId = 0L;

    public GuildMusicManager(long guildId) {
        this.guildId = guildId;
    }

    public void enQueue(Track track) {

        this.trackQueue.add(track);
    }

    public void clearQueue() {
        this.trackQueue.clear();
    }

    public Track pollNext() {
        return trackQueue.poll();
    }

    public boolean isQueueEmpty() {
        return trackQueue.isEmpty();
    }

    public long getGuildId() {
        return guildId;
    }

    public Queue<Track> getTrackQueue() {
        return trackQueue;
    }

    public void removeTrack(Track track) {
        trackQueue.remove(track);
    }

    public int getTrackPosition(Track searchTrack) {

        List<Track> tracks = new ArrayList<>(trackQueue);

        for (int i = 0; i < trackQueue.size(); i++) {

            Track track = tracks.get(i);

            if (searchTrack.equals(track)) {

                return i + 1;
            }

        }

        return 0;

    }

    public void setTextChannelId(long textChannelId) {
        this.textChannelId = textChannelId;
    }

    public long getTextChannelId() {
        return textChannelId;
    }


    public void shuffleQueue() {
        List<Track> trackList = new ArrayList<>(trackQueue);
        Collections.shuffle(trackList);
        trackQueue.clear();
        trackQueue.addAll(trackList);
    }






}
