# DR Improvement Mod — Forge 1.21.4

Client-only QoL mod for **DungeonRealms**, ported from Aris's Fabric 1.19.4 mod
([ayoeo/dr_improvement_mod_1_19](https://github.com/ayoeo/dr_improvement_mod_1_19)) to **Forge 54.x / Minecraft 1.21.4**.

> The original repo has no license, which means all rights are reserved by its author.
> Get Aris's permission before publishing this port publicly (CurseForge, Modrinth, a public repo).

## Features
- **Custom bars** (core shader): energy arcs around the crosshair, mana bar under it, HP bar at the top. The HP boss bar is hidden but its text is kept, and other boss bars move down to make room.
- **Real HP on nameplates**: works out each player's max HP from DR's `health` objective and keeps the score in step with smooth entity health.
- **Useful info**: ultimate cooldown, usable/carried potions with the potion cooldown, combat timer (PvE 8 s / PvP 15 s), and for Rogue the Shadowmeld scaling and "It's Just My Nature" bonus window.
- **Hotbar abilities**: selecting an ability head uses it and returns you to your previous slot.
- **1.8 feel**: no double-tap sprint, no miss-swing delay, no client crit effects, no re-equip bob, no swing on drop.
- **Guild nametags** in light green, **colour-blind glow**, item **origin** in tooltips, and **packet debounce** for client-settings and tab-complete packets.

Everything can be toggled in **Mods → DR Improvement Mod → Config**. Settings are saved to `config/dr_improvement_mod-client.toml`.

## Build
Needs JDK 21. The first build downloads Minecraft and Forge, which takes a few minutes.

```
./gradlew build          # jar lands in build/libs/
./gradlew runClient      # dev client
```

Alternatively, push the project to GitHub. The included workflow (`.github/workflows/build.yml`) builds the jar and uploads it as an artifact.

## Install
Install Forge **1.21.4-54.x**, then put the jar from `build/libs/` into `.minecraft/mods/`.

## Changes vs. the Fabric 1.19.4 version

### Fixes
- **HUD position**: the text was placed at framebuffer/4, so it was only centred at GUI scale 2. It's now centred at any scale.
- **Config defaults**: the ModMenu screen defaults disagreed with the real defaults. There's now one source of truth.
- **Potion parsing**: an empty number in the potion action-bar text crashed the HUD with a `NumberFormatException`. Parsing is now safe.
- **Overlay shader**:
  - It used to be raw GL: no reload support, no state tracking, and it drew on every `Profiler.swap` call. It's now a proper core shader through the shader manager.
  - It used to draw even with info off. It now has its own toggle.
  - `fragColor` was undefined on empty pixels. It's now initialised, and those pixels are discarded.
- **Health scores**: health score packets were dropped outright, so players with an unknown max HP showed nothing. They now pass through, and the mod only overrides them when it knows better.
- **Reflect rollback**: rollback went through property setters and could flip-flop on a second reflect. It now restores a snapshot of the state before the hit.
- **Packet throttling**:
  - The first settings or tab-complete packet was always delayed. It now goes out immediately, and only bursts are held back.
  - Throttling now applies only during play, not during login or configuration.
- **Ability heads on blocks**: right-clicking a block with an ability head did nothing. It now uses the ability.
- **Logging out**: state is cleared, so data no longer leaks between servers.
- **Guild nametags**: an `@Overwrite` was replaced with an injection, which is compatible with other mods.
- **Crit effects**: suppressed only on the client side, so singleplayer is unaffected.

### Porting changes
- **New options**: hotbar abilities, packet throttle and 1.8 combat feel are now individual options. All default to on, as before.
- **Forge events instead of mixins**: boss bar, crits, tooltips, HUD and tick now use Forge events where one exists, which means fewer mixins to break.
- **Item origin lookup**: item origin is now read from the 1.20.5+ `custom_data` component instead of raw NBT.
