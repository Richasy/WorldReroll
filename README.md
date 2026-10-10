# World Reroll

A small client-only **Minecraft 26.3 / Fabric / Java 25** tool for trying another singleplayer survival start without repeatedly entering world settings.

## Use

Install the JAR alongside Fabric API in the intended instance's `mods` directory. Open the pause menu and click **Reroll**, next to the bottom menu button. The settings panel offers **Random spawn** and **Near a village**. Select a preference, continue and confirm **Reroll world** to keep the current world's name, mode, difficulty and difficulty lock, game rules, world generator, structure and bonus-chest options, enabled features and world datapacks, but use a new random seed. The confirmation describes the deletion before anything starts. English and Simplified Chinese are included.

Village mode uses a bounded vanilla structure search around the initial spawn, then moves both the player and the world spawn to safe terrain within 64 blocks on each horizontal axis of the village's located position. It does not filter seeds for a natural village spawn. Locating and moving run on the integrated server; the old world is kept until the client receives the new position and its chunk, then becomes playable. No structures or terrain are added. If no village or suitable terrain is found, the attempt fails and keeps the source. Village mode is disabled when structure generation is off. The last preference is remembered for this client session.

The old world is saved and closed using Minecraft's normal shutdown. A fresh folder is created and world datapacks are copied. After the replacement becomes playable for 20 consecutive client ticks, the exact original save is deleted using Minecraft's own storage API. **This deletes the old terrain, buildings, inventory and progress permanently.** Only use it for worlds you intend to discard.

There is no entry on multiplayer servers or in a world open to LAN. There are no new dimensions, gameplay items, commands, automatic resets or Mixins. Lithergy is not a dependency.

## Failure behavior

Creating or loading the replacement never authorizes deleting the source. Cancellation or creation failure keeps the old world. A client restart does not resume any pending deletion. Every attempt pins the exact source and replacement directories with exclusive random marker files, creation metadata and file identity when available; replaced or redirected folders are rejected. Windows may not expose a filesystem file key, so the exclusive marker is required on every platform. World datapack symlinks are not copied.

Deletion errors are logged with the two world IDs and reported in game. The replacement remains usable; if deletion has started, cleanup can be partial. Failed replacement folders are left for diagnosis. Crash-interrupted attempts can leave harmless `.worldreroll-*` marker files inside their saves; they never schedule deletion on their own.

The tool retains world-generation configuration and datapacks, not arbitrary third-party per-save state. A new world starts with new player state and progression. Compatibility with mods that replace the normal world-loading flow requires separate validation.

## Development

Versions are pinned in `gradle.properties`, and the checked-in Gradle wrapper has its distribution checksum. Set `JAVA_HOME` to JDK 25 for the current shell only, then run:

```powershell
.\gradlew.bat build --no-daemon
.\gradlew.bat runClientGameTest --no-daemon
```

Native client tests use disposable worlds under `build/run/clientGameTest`; manual development uses `run/client`. Never run the deletion scenarios against personal saves. Unit tests cover attempt transitions, cancellation, repeated activation, path traversal, active-world exclusion and changed source identity. The native scenario drives the pause/confirm/cancel interface, preserved settings and an active copied datapack, new seed and new player/terrain state, deletion only after entering the replacement, an unrelated save's survival and failed recreation preserving its source.

Inspired by the workflow of [Start Reroll](https://github.com/euruseve/StartReroll); this is an independent implementation using Minecraft 26.3's public world and storage APIs.
