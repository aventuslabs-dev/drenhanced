# DR Improvement Mod — Fabric 1.21.4

Client-only QoL mod for **DungeonRealms**, ported from Aris's Fabric 1.19.4 mod
([ayoeo/dr_improvement_mod_1_19](https://github.com/ayoeo/dr_improvement_mod_1_19)) to **Fabric / Minecraft 1.21.4**.
A Forge 1.21.4 version lives on the `forge-1.21.4` branch.

> The original repo has no license (all rights reserved by its author). Get Aris's permission before publishing this port publicly.

## Install
Fabric Loader 0.16+ and **Fabric API** for 1.21.4, then drop the jar into `.minecraft/mods/`.
Settings: Mods menu → DR Improvement Mod (needs [ModMenu](https://modrinth.com/mod/modmenu)), or type `/drconfig`.
Saved to `config/dr_improvement_mod.json`.

## Features
- **Custom bars** (core shader): energy arcs around the crosshair, mana bar below, HP bar at the top. The HP boss bar is hidden but its text is kept.
- **Real HP on nameplates**, derived from DR's `health` objective and kept smooth.
- **Useful info**: ultimate cooldown, usable/carried potions + potion cooldown, combat timer (PvE 8 s / PvP 15 s), Rogue Shadowmeld scaling and "It's Just My Nature" window.
- **Hotbar abilities**: selecting an ability head uses it and returns to your previous slot.
- **1.8 feel**: no double-tap sprint, no miss-swing delay, no client crit effects, no re-equip bob, no swing on drop.
- Guild nametags (light green), colour-blind glow, item origin in tooltips, packet debounce.

## Build
JDK 21: `./gradlew build` → `build/libs/dr_improvement_mod-<version>.jar`.
CI builds every push, launches a headless client to verify every mixin applies and the shader compiles, then publishes the jar to the `ci-output` branch.

## Changes vs. the 1.19.4 original
- **HUD position**: the HUD text is now centred at any GUI scale. The original assumed GUI scale 2.
- **Config**: there is one set of defaults; the old screen disagreed with the saved ones. ModMenu is now optional, and `/drconfig` opens the settings without it.
- **Action-bar parsing**: no longer crashes when the server sends a malformed cooldown message.
- **Overlay rendering**: now a proper core shader that survives resource reloads. Empty pixels are discarded instead of left undefined, and the overlay has its own on/off toggle.
- **Boss bars**: the hook no longer depends on fixed variable positions in vanilla code, so it is less likely to break.
- **HP scores**: no longer dropped for players whose max HP isn't known yet.
- **Reflect handling**: a reflected hit restores the timers to exactly where they were before that hit.
- **Packet debounce**: the first packet goes out immediately, and debouncing only applies while you're in game.
- **Ability heads**: right-clicking a block with an ability head now uses the ability.
- **Server switching**: all tracked state is cleared when you disconnect.
- **Guild nametags**: an injection instead of an overwrite, so it stays compatible with other mods.
- **Crit effects**: suppressed only on your client.
