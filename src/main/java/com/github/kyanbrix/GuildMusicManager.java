package com.github.kyanbrix;

import dev.arbjerg.lavalink.client.player.Track;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;


public class GuildMusicManager {

    public enum LoopMode {
        OFF("➡️ Off"), TRACK("🔂 Track"), QUEUE("🔁 Queue");

        private final String label;

        LoopMode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final long guildId;
    private final LinkedList<Track> trackQueue = new LinkedList<>();
    private volatile long textChannelId = 0L;
    private volatile LoopMode loopMode = LoopMode.OFF;
    private ScheduledFuture<?> disconnectTask;

    public GuildMusicManager(long guildId) {
        this.guildId = guildId;
    }

    /** @return the 1-based position of the track in the queue */
    public synchronized int enQueue(Track track) {
        trackQueue.add(track);
        return trackQueue.size();
    }

    public synchronized void enQueueAll(Collection<Track> tracks) {
        trackQueue.addAll(tracks);
    }

    public synchronized void clearQueue() {
        trackQueue.clear();
    }

    public synchronized Track pollNext() {
        return trackQueue.poll();
    }

    public synchronized boolean isQueueEmpty() {
        return trackQueue.isEmpty();
    }

    public synchronized int queueSize() {
        return trackQueue.size();
    }

    /** @return a copy of the queue that is safe to iterate */
    public synchronized List<Track> getTrackQueue() {
        return new ArrayList<>(trackQueue);
    }

    /**
     * @param position 1-based position in the queue
     * @return the removed track, or null if the position is out of range
     */
    public synchronized Track removeTrack(int position) {
        if (position < 1 || position > trackQueue.size()) return null;
        return trackQueue.remove(position - 1);
    }

    public synchronized void shuffleQueue() {
        Collections.shuffle(trackQueue);
    }

    public long getGuildId() {
        return guildId;
    }

    public void setTextChannelId(long textChannelId) {
        this.textChannelId = textChannelId;
    }

    public long getTextChannelId() {
        return textChannelId;
    }

    public LoopMode getLoopMode() {
        return loopMode;
    }

    public void setLoopMode(LoopMode loopMode) {
        this.loopMode = loopMode;
    }

    synchronized void setDisconnectTask(ScheduledFuture<?> task) {
        cancelDisconnectTask();
        this.disconnectTask = task;
    }

    synchronized boolean hasDisconnectTask() {
        return disconnectTask != null && !disconnectTask.isDone();
    }

    synchronized void cancelDisconnectTask() {
        if (disconnectTask != null) {
            disconnectTask.cancel(false);
            disconnectTask = null;
        }
    }

}
