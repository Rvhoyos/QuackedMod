[![Release](https://img.shields.io/github/v/release/Rvhoyos/QuackedMod)](https://github.com/Rvhoyos/QuackedMod/releases)
![GitHub Downloads](https://img.shields.io/github/downloads/Rvhoyos/QuackedMod/total)
[![CurseForge Downloads](https://img.shields.io/curseforge/dt/1345567?label=CurseForge%20downloads)](https://www.curseforge.com/minecraft/mc-mods/ducky-quack-pack)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/ducky-quack-pack?label=Modrinth%20downloads)](https://modrinth.com/mod/ducky-quack-pack)
[![License](https://img.shields.io/badge/License-All%20Rights%20Reserved-lightgrey)](#license)
---
# QuackedDucks 1.1.3

A "lightweight" cross-loader Minecraft mod that adds ducks with simple AI, natural spawns, sounds, items, and hatchable eggs. Supports both Fabric and NeoForge. GeckoLib5 for duck model and animations.
>Find us on curseforge or modrinth website / client.

[Mod Wiki Page](https://quackedmod.wiki/)
## Features
![Issues](https://img.shields.io/github/issues/Rvhoyos/QuackedMod)
![Stars](https://img.shields.io/github/stars/Rvhoyos/QuackedMod?style=social)

- **In-game Configuration**: Real-time GUI (via `/quack config`) for tweaking parameters like size, speed, and sound frequency. Requires OP/Cheats.
- **Dynamic Hitbox Scaling**: Entity hitboxes scale perfectly with the model size (no "punching through").
- **Custom** GeckoLib model/animations.
- **Taming & Sitting**: The lead duck of a flock can be tamed and told to sit!
- **Follow‑the‑leader**: Flocks self‑organize into lines.
- **Migration**: Flocks occasionally migrate to new distant spots.
- **Natural spawns** across temperate, taiga, wetlands, rivers, and shores.
- **Tempted** with seeds; breeds like chickens.
- **Quack**, hurt, and death sounds.
- **Duck Eggs**: Ducks lay eggs periodically.
### Drops & Items
- **Duck Meat** and Cooked Duck foods.
- **Duck Feathers** for crafting.
- **Duck‑feather Arrow** variant.
- **Foie Gras** (late‑game food) with short, meaningful buffs.
---
## Game/Loader Compatibility
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.8-informational)
![Loaders](https://img.shields.io/badge/Loaders-Fabric%20%7C%20NeoForge-informational)
![Java](https://img.shields.io/badge/Java-21-informational)
- **Minecraft:** 1.21.8
- **Fabric:** Loader `0.17.2` (or newer), **Fabric API** required
- **NeoForge:** `21.8.x`
- **Shared libs (both loaders):** GeckoLib `5.x`
## Curse Forge & modrinth Installation (easy):
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/ducky-quack-pack)
- [modrinth](https://modrinth.com/mod/ducky-quack-pack)
## Manual Installation (Client)
>Download official Jar from curseforge or modrinth.
>For building your own jars,clone the repository and -
> execute commands under #Dev end of this page for more details.
### Fabric client
1. Install a Fabric profile for Minecraft 1.21.8 (via the Fabric installer/launcher).
2. In your game directory (`.minecraft/mods`):
   - Add `geckolib-5.x (fabric).jar`
   - Add `fabric-api (1.21.8).jar`
   - Add `quack-fabric-1.1.3.jar`
>Get from official sources. [Geckolib](https://modrinth.com/mod/geckolib/versions?g=1.21.8&l=fabric&l=neoforge)

### NeoForge client
1. Install NeoForge for 1.21.8.
2. In your game directory (`.minecraft/mods`):
   - Add `geckolib-5.x (neoforge).jar`
   - Add `quack-neoforge-1.1.3.jar`
>Get from official sources. [Geckolib](https://modrinth.com/mod/geckolib/versions?g=1.21.8&l=fabric&l=neoforge)

## Manual Installation (Dedicated Server)

### Fabric or NeoForge Server
1. Place the Fabric or Neoforge server launcher jar for **1.21.8** (e.g. `fabric-server-mc.1.21.8-loader.0.17.2-launcher.1.1.0.jar`).
2. Run once to generate files, accept the EULA in `eula.txt`.
3. Put these in the server `mods/`:
   - `geckolib-5.x (fabric).jar`
   - `fabric-api (1.21.8).jar`
   - `quack-fabric-1.1.3.jar` (or neoforge equivalent)
4. Start the server and connect.
---
# Dev
## Building distributable JARs (Gradle)

**Prereqs:** JDK 21+, Git, and the Gradle wrapper included in this repo.

From the repo root, build each loader **module** explicitly:
> Jars can be found under <modloader>/build/libs **not** under common folder.

# Fabric release jar
`./gradlew :fabric:remapJar` or `./gradlew :fabric:build`
### running into issues?
`./gradlew :fabric:clean :fabric:build`
# NeoForge release jar
`./gradlew :neoforge:jar` or `./gradlew :neoforge:build`
### running into issues?
`./gradlew :neoforge:clean :neoforge:build`
