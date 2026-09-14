# Ironman Path Sync

RuneLite plugin for [ironmanpath.app](https://www.ironmanpath.app). It sends your bank
(plus inventory and worn items, optional) and your skill levels to the site, so the
Set builder can pick the best gear for a boss from what you actually own.

## How it works

1. On the site: **Set builder > My bank > Link with RuneLite**. The site shows a 6-letter code.
2. In RuneLite: open the plugin settings and paste the code in **Link code**.
3. Open your bank in game. The plugin sends the snapshot once the bank finishes loading.
4. The site receives it in a few seconds. The code expires after 15 minutes.

## What is sent

Only game data: your display name, your real skill levels, and a list of item ids with
quantities. Nothing else. The plugin never reads or sends your password, e-mail, session
or any account information (RuneLite does not expose those to plugins).

Nothing is sent unless you typed a valid code. Data is only read while your bank is open.

## Privacy

Snapshots are stored on the site's server only long enough for the site to pick them up
(they are deleted when the code expires). You can delete your data from the site at any time.

## Build and run locally (developers)

Requires JDK 11.

```
./gradlew build
./gradlew run          # starts RuneLite in developer mode with the plugin loaded
```

## License

BSD 2-Clause. See LICENSE.
