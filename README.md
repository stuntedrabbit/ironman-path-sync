# Ironman Path Sync

RuneLite plugin for [ironmanpath.app](https://www.ironmanpath.app). It sends your bank
(plus inventory and worn items, optional) and your skill levels to the site, so the
Set builder can pick the best gear for a boss from what you actually own.

## How it works

1. On the site: **Set builder > My bank > Link with RuneLite**. The site shows a 6-letter code.
2. In RuneLite: open the **Ironman Path Sync** side panel, paste the code and press **Link with this code**.
3. Open your bank in game once. The plugin sends the snapshot and the server answers with a private key
   that the plugin stores. The code is used once and expires after 15 minutes.
4. From then on the bank is sent every time you open it. **Sync now** sends levels, inventory, worn items
   and the last bank the client saw this session. **Unlink** deletes the key on the plugin
   (the site has its own Unlink that deletes it on the server).

## Saved sets and the bank filter

Sets you save on the website show up in the plugin side panel. **Show in bank** filters your bank
(a temporary bank tag laid out like the equipment screen) so you can withdraw the set with one click
per item. The plugin never withdraws, equips or clicks anything for you: RuneLite does not allow plugins
to automate game actions. **Clear bank filter** restores the normal bank view.

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
