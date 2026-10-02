# 🎵 Musica

Musica is a feature-rich, high-performance Discord music bot built with Java. It provides seamless audio playback, playlist management, and high-quality voice channel integration for your Discord server.

## ✨ Features

* **High-Quality Audio:** Stream music from YouTube, Spotify, Apple Music, Deezer, SoundCloud and more (powered by [Lavalink](https://github.com/lavalink-devs/Lavalink) and its plugins).
* **Queue Management:** View (paginated), shuffle, loop (track or whole queue), remove, and clear your server's queue.
* **Playback Control:** Pause, resume, skip, seek, volume, now-playing with a progress bar, and lyrics (via the LavaLyrics plugin).
* **Smart Voice Handling:** Only people in the bot's voice channel can control it, it joins self-deafened, pauses when server-muted, and leaves automatically when the queue ends or everyone leaves.
* **Stable & Fast:** Built on Java to handle multiple concurrent voice sessions without lag.

## 🎛️ Commands

Default prefix is `?` — run `?help` in Discord for the full list with aliases.

| Command | Description |
|---|---|
| `?play <song or URL>` | Play a song/playlist or add it to the queue |
| `?skip` | Skip the current song |
| `?pause` / `?resume` | Pause or resume playback |
| `?stop` | Stop, clear the queue and leave |
| `?queue [page]` | Show the queue |
| `?np` | Show the current song and its progress |
| `?loop [off\|track\|queue]` | Set the loop mode (no argument cycles) |
| `?shuffle` | Shuffle the queue |
| `?remove <position>` | Remove a song from the queue |
| `?clear` | Clear the queue |
| `?seek <1:30>` | Jump to a time in the current song |
| `?volume [0-200]` | Show or set the volume |
| `?lyrics` | Show lyrics for the current song |

## 🚀 Setup

1. Run a [Lavalink v4](https://lavalink.dev) server (add LavaSrc / LavaLyrics plugins for Spotify, Apple Music and lyrics).
2. Copy `config.example.properties` to `config.properties` (in the working directory or `src/main/resources`) and fill in your bot token and Lavalink password. Any key can also be set as an environment variable, e.g. `lavalink.password` → `LAVALINK_PASSWORD`.
3. In the Discord Developer Portal, enable the **Message Content** intent for your bot.
4. Build and run: `mvn package` then run `com.github.kyanbrix.Main`.

`config.properties` is git-ignored — never commit your token.

## 🛠️ Tech Stack

* **Language:** Java (21)
* **Dependency Management:** Maven (`pom.xml`)
* **Discord API:** [JDA](https://github.com/discord-jda/JDA)
* **Audio:** [Lavalink](https://github.com/lavalink-devs/Lavalink) via [lavalink-client](https://github.com/lavalink-devs/lavalink-client)
