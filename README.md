# МК ТВ — Macedonian TV for Android TV

A remote-friendly Android TV app for watching Macedonian television: every **MRT Play** channel, the main private stations, MRT radio, and **catch-up** (past programmes).

![Home](docs/screenshots/home.png)

## Features

- **Live TV**: 24 channels in rows (МРТ, National, Albanian-language, Regional, Music…). A hero panel shows the focused channel.
- **Catch-up, all inside the app**:
  - **MRT archive**: browse sections (News, Sport, MRT Production, Documentary, 50 Years of MRT, Promo), then shows, then episodes, with infinite scroll. The archive plays worldwide.
  - **18 private stations from their official YouTube channels**: the latest videos plus the station's playlists as shows (e.g. Sitel → Тема на денот, Kanal 5 → Менуваме животи), with infinite scroll. They play in the app with YouTube's official embedded player and remote controls: OK pause, ◀/▶ seek 10 s, ▲/▼ previous or next video, and the next video starts automatically.
  - **YouTube Premium (optional)**: turn on **Без реклами** (no ads) in Архива to open videos in your signed-in YouTube app instead, ad-free with Premium. The YouTube TV app keeps its own history, so Back stays in YouTube; press Home and open МК ТВ to return. Off by default, so videos stay in МК ТВ.
- **Radio**: MR 1, MR 2, MR 3, MR SAT.
- **Built for the remote**: D-pad everywhere, a gold focus ring with zoom, a 10-foot layout with overscan-safe margins, and Macedonian labels.
- **Player**: ▲/▼ or CH+/CH− zap channels, ◀ opens the channel list, OK shows channel info. In catch-up, OK is play/pause and ◀/▶ seek 10 s.
- **Favourites**: hold OK on any channel. The app also remembers the last channel you watched.
- **VPN-aware**: channels limited to North Macedonia carry a 🔒 МК badge. When one is blocked, the app says "turn on a VPN set to North Macedonia" instead of showing an error.
- **Channel list updates over the air**: the app downloads [`channels.json`](app/src/main/assets/channels.json) from this repo, so new or fixed stream links arrive without reinstalling.

| Catch-up (MRT archive) | Channel list in the player |
|---|---|
| ![Catch-up](docs/screenshots/catch-up.png) | ![Channel list](docs/screenshots/channel-list.png) |
| **Station catch-up from YouTube** | **YouTube video playing in the app** |
| ![YouTube](docs/screenshots/stations-youtube.png) | ![YouTube player](docs/screenshots/youtube-player.png) |
| **MK-only channel without a VPN** | |
| ![VPN hint](docs/screenshots/vpn-hint.png) | |

## Install on your TV

1. Download `mk-tv-<version>.apk` from [Releases](https://github.com/rmilev/mk-tv/releases).
2. On the TV, install it either way:
   - **Downloader app** (from the Play Store): enter the APK link, then allow "Install unknown apps" for Downloader when asked.
   - **adb**: turn on developer mode and network debugging, then run `adb connect <tv-ip>` and `adb install mk-tv-<version>.apk`.
3. МК ТВ appears in the TV's app row.

**VPN:** MRT 1–5 only play from Macedonian IP addresses. Run a VPN app on the TV, set to **North Macedonia**, for those channels. Everything else plays worldwide.

## Channels

| Channel | Group | Live | Catch-up |
|---|---|---|---|
| МРТ 1 · 2 · 3 · 4 · 5 | МРТ | MK only (VPN) | MRT archive (in app) |
| МРТ 1 САТ, МРТ 2 САТ, Собраниски канал | МРТ | Worldwide | MRT archive (in app) |
| Сител, Канал 5, Наша ТВ | National | Worldwide | YouTube |
| Телма, Алфа, Клан Македонија | National | — (no public stream) | YouTube |
| 24 Вести, Alsat, М-Нет HD | — | — (subscriber-only stream) | YouTube |
| ТВ21, TV Shenja, TV Koha | Albanian-language | Worldwide | YouTube |
| Канал 8, ТВ Нова 12, ТВ Здравкин | Regional | Worldwide | YouTube |
| Folk TV, Love TV, Sky Folk, TopEstrada, Фолк Клуб | Music | Worldwide | YouTube (some) |
| Cool TV, ИНТВ Австралија | Other | Worldwide | YouTube (INTV) |
| Македонско радио 1 · 2 · 3 · САТ | Radio | Worldwide | — |

The app covers **all 12 live channels on MRT Play** (MRT 1–5, MRT 1 SAT, MRT 2 SAT, the Assembly channel and 4 radio stations).

**Verification (30 Oct 2026):**
- **Worldwide** streams were checked from the US down to an actual video segment.
- **MRT 1–5** were checked from probes inside North Macedonia (Skopje, via [Globalping](https://globalping.io)). The master playlist and live segment lists returned 200, so they play with a VPN set to North Macedonia.
- **Removed:** the streams for 24 Вести, Alsat, TV Era, МТМ, Шутел and M-Net. They failed from three Macedonian ISPs, because they only work for the operator's own subscribers. Their YouTube catch-up stays.

Sitel and Kanal 5 come from teve.mk, a public restream listed by iptv-org, because neither station publishes its own live player.

## How it works

- **MRT Play** signs live links for 30 minutes. The app scrapes a fresh link from the channel page each time you tune in, and fetches another one if a link expires mid-stream.
- **The MRT archive** is read live from play.mrt.com.mk: section pages, a JSON episode list paged 24 at a time, then the episode's HLS link.
- **Other stations** are fixed HLS URLs, or page plus regex entries (`type: "page"`) when the link changes.
- **YouTube catch-up** reads each channel's public `/videos` and `/playlists` pages (the `ytInitialData` the website renders) and loads more through the web client's browse continuation. No API key is needed. The app shows no ads of its own. Videos play in the official IFrame player ([android-youtube-player](https://github.com/PierfrancescoSoffritti/android-youtube-player)) with the origin set to `https://rmilev.github.io`, because YouTube rejects embeds that have no origin (error 152).

### Add or fix a channel

Edit [`app/src/main/assets/channels.json`](app/src/main/assets/channels.json) and **increase `version`**. Installed apps pick up the change the next time they start.

```json
{ "id": "example", "name": "Пример ТВ", "group": "Регионални", "logo": "https://…/logo.png",
  "type": "url", "url": "https://…/playlist.m3u8", "mkOnly": false,
  "catchup": { "type": "youtube", "youtubeChannelId": "UC…" } }
```

For a link that changes, use `"type": "page"` with a `"page"` URL and a `"pattern"` regex that matches the `.m3u8` link.

## VPN Switcher (companion app)

`vpnswitch/` is a separate small TV app that switches your VPN country per app: open МК ТВ and it connects to North Macedonia, open Stremio and it connects to the US, and so on. It drives [OpenVPN for Android](https://f-droid.org/packages/de.blinkt.openvpn/) through that app's remote-control API, using Private Internet Access's official OpenVPN profiles (every PIA region is built in). An accessibility service sees which app comes to the front (it never reads the screen) and connects that app's region, turns the VPN off, or leaves it as it is.

Setup on the TV: install OpenVPN for Android, open VPN Switcher, and follow its five steps (allow control, allow VPN, PIA login, turn it on under Settings → Accessibility → Services). Then pick a region for each app on the right.

## Groove — live lyrics (companion app)

`lyrics/` is Groove, a TV app that listens to whatever is playing in the room (a record, a streaming app, anything) through the TV's built-in microphone. It shows the cover art and the lyrics in time with the music. It keeps listening the whole time it's on screen:
- It re-checks every 8 seconds, so a skipped track, a seek or a new song shows within a few seconds.
- A short break in the sound, like a skip or the groove between tracks on a record, brings the next check forward.
- On a turntable running slightly fast or slow, it measures the speed and keeps the lyrics in step.

- **Recognition:** Shazam's song-matching service. The app makes the audio fingerprint itself with a Kotlin port of [SongRec](https://github.com/marin-m/SongRec)'s algorithm, the same approach the open-source [Audile](https://github.com/aleksey-saenko/MusicRecognizer) app uses. This isn't an official Shazam API, so it may stop working if Shazam changes it.
- **Lyrics:** [LRCLIB](https://lrclib.net), a free, open database of time-synced lyrics.
- **Artist photos:** Shazam's photo plus up to four widescreen photos from [TheAudioDB](https://www.theaudiodb.com), shown in a random order (new for every song) every 40 seconds.
- **Looks:** Stage (artist photo with lyrics beside it), Glass (a frosted panel) and Cinema (centred lyrics over a slowly drifting photo). Up and down switch; Groove remembers your choice.
- **Remote:** OK re-checks right away. Left and right shift the lyrics 0.2 s later or earlier if they feel off; the app remembers the setting.

Only the fingerprint (a list of spectral peaks) leaves the TV, never the audio itself, and the microphone is only on while the app is on screen. The `lyrics/` module is GPL-3.0 (see `lyrics/LICENSE`) because its fingerprint code is derived from SongRec.

## Build

You need JDK 17 and the Android SDK (platform 35).

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # signed if keystore.properties exists, otherwise debug-signed
```

Release signing reads `keystore.properties` from the project root, which git ignores:

```
storeFile=/path/to/release.jks
storePassword=…
keyAlias=mktv
keyPassword=…
```

Stack: Kotlin, Jetpack Compose for TV (`androidx.tv:tv-material`), Media3 ExoPlayer (HLS), android-youtube-player (official YouTube IFrame player), OkHttp, Coil, kotlinx.serialization.

## Disclaimer

This is an unofficial, non-commercial app. All programmes, logos and trademarks belong to their broadcasters: Македонска радио телевизија and the stations listed. The app only plays streams that the broadcasters (or public directories such as iptv-org) make available on the web, and it does not bypass geo-restrictions or DRM.
