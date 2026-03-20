[![Release](https://img.shields.io/github/v/release/Rvhoyos/QuackedMod)](https://github.com/Rvhoyos/QuackedMod/releases)
![GitHub Downloads](https://img.shields.io/github/downloads/Rvhoyos/QuackedMod/total)
[![CurseForge Downloads](https://img.shields.io/curseforge/dt/1345567?label=CurseForge%20downloads)](https://www.curseforge.com/minecraft/mc-mods/ducky-quack-pack)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/ducky-quack-pack?label=Modrinth%20downloads)](https://modrinth.com/mod/ducky-quack-pack)
[![License](https://img.shields.io/badge/License-All%20Rights%20Reserved-lightgrey)](#license)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-informational)
![Loaders](https://img.shields.io/badge/Loaders-Fabric%20%7C%20NeoForge-informational)

---

# QuackedDucks 2.0

A cross-loader Minecraft mod that adds ducks with a fully rewritten flocking AI, natural spawns, custom sounds, taming, and craftable items. Supports Fabric and NeoForge. Powered by GeckoLib 5.4.5.

> Find us on [CurseForge](https://www.curseforge.com/minecraft/mc-mods/ducky-quack-pack) or [Modrinth](https://modrinth.com/mod/ducky-quack-pack).

---

## What's New in 2.0

2.0 is a ground-up rewrite of the duck AI. The old goal-based system has been replaced with a single hierarchical state machine (`DuckBrainGoal`) that owns all locomotion decisions. This eliminates the race conditions and formation bugs that plagued 1.x.

- **Flocking AI rewrite** — Ducks self-organise into leader/follower chains. The leader duck is marked with a particle crown. Followers track their leader on the ground and in the air.
- **Migration** — Untamed leader ducks periodically fly 80–140 blocks to a new location. Their followers go with them.
- **Graceful landing** — Ducks descend using flying navigation before switching to ground mode, preventing the sink-into-water bug from 1.x.
- **Taming overhaul** — Tame the leader duck of a flock to lead the whole group. Tamed ducks follow their owner and are not afraid of predators.
- **Animations** — Idle, walk, fly, panic, and a rare DAB animation via GeckoLib.
- **Config system** — Real-time in-game configuration via `/quack config` (requires OP/cheats). All values are hot-applied without a restart.

---

## Features

### Duck AI
- Ducks form **leader/follower chains** of up to 3 (leader + 2 followers, then followers chain). Leaders have a particle crown above their head.
- **Migration**: every few minutes an untamed leader picks a random land target 80–140 blocks away and flies there. Its followers switch to air-follow and come along.
- **Panic**: any hit causes adult ducks to fly to a random escape point 8–16 blocks away. Baby ducks sprint on the ground instead.
- Ducks avoid **monsters, wolves, polar bears, and bees** on the ground (suppressed while flying or when tamed).
- Baby ducks follow the nearest adult.

### Taming
- Feed seeds to a duck to tame it. Only the **leader** duck of a flock needs to be tamed — the wild followers trail the tamed leader who follows you.
- Tamed ducks follow their owner when within 12 blocks and stop pursuing when the owner moves further than 20 blocks away.
- Tamed ducks are **calm around predators** — they will not flee from wolves or monsters.
- Tamed ducks do not migrate.

### Spawning
- Spawns across temperate, forest, taiga, wetland, river, and shore biomes.
- Wet biomes (rivers, swamps, beaches) have bonus spawn weight.
- Group sizes and spawn weights are configurable.

### Items & Crafting

| Item | Source | Notes |
|------|--------|-------|
| Duck Meat | Duck drop (raw) | Low nutrition raw |
| Cooked Duck | Smelt/smoke/campfire Duck Meat | Good food |
| Duck Feather | Duck drop | Crafting ingredient |
| Duck Feather Arrow | Craft: flint + duck feather + stick (column) → 4 arrows | Functionally identical to vanilla arrows |
| Duck Egg | Laid periodically by adult ducks | Throwable; hatches a baby duck |
| Empty Foie Gras Bowl | Craft: 3 planks + duck feather (bowl shape) → 4 bowls | Vessel for Foie Gras |
| Foie Gras | Craft (shapeless): cooked duck + empty bowl | Grants Regeneration II + Absorption + Saturation |
| Duck Spawn Egg | Creative only | Spawns a duck |

### Configuration (`/quack config`)
All values sync server → client on join. Changes apply to all loaded ducks immediately.

| Setting | Default | Description |
|---------|---------|-------------|
| `maxHealth` | 6.0 | Duck HP |
| `movementSpeed` | 0.25 | Base walk speed |
| `duckWidth` / `duckHeight` | 0.75 / 0.95 | Hitbox dimensions |
| `babyScale` | 0.5 | Baby duck size multiplier |
| `ambientSoundInterval` | 120 | Ticks between ambient quacks |
| `migrationCooldownTicks` | 3600 | Ticks between migrations (3600 ≈ 3 min) |
| `dabChance` | 5 | 1-in-N chance of DAB animation per idle window |
| `baseWeight` | 3 | Base biome spawn weight |
| `wetBiomeBonusWeight` | 3 | Extra weight for wet biomes |
| `minGroupSize` / `maxGroupSize` | 1 / 1 | Spawn group size range |

Config is stored in `config/quack.json`. On version upgrades, existing values are preserved and new fields are written with defaults.

---

---

## Compatibility

| | Version |
|--|--|
| Minecraft | 1.21.11 |
| Fabric Loader | 0.17.2+ |
| Fabric API | Required |
| NeoForge | 21.11.x |
| GeckoLib | 5.4.5 (required, both loaders) |
| Java | 21 |

---

## Installation

### Via Launcher (Recommended)
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/ducky-quack-pack)
- [Modrinth](https://modrinth.com/mod/ducky-quack-pack)

### Manual — Fabric Client
1. Install a Fabric profile for Minecraft 1.21.11.
2. Drop into `.minecraft/mods/`:
   - `fabric-api (1.21.11).jar`
   - `geckolib-fabric-5.4.5.jar`
   - `quack-fabric-2.0.jar`

### Manual — NeoForge Client
1. Install NeoForge for 1.21.11.
2. Drop into `.minecraft/mods/`:
   - `geckolib-neoforge-5.4.5.jar`
   - `quack-neoforge-2.0.jar`

### Manual — Dedicated Server
1. Set up a Fabric or NeoForge server for 1.21.11.
2. Drop the appropriate jars (see above) into the server `mods/` folder.
3. Start the server. Config generates at `config/quack.json`.

> GeckoLib is available at [modrinth.com/mod/geckolib](https://modrinth.com/mod/geckolib/versions?g=1.21.11).

---

## Building from Source

**Requirements:** JDK 21+, Git.

```bash
# Clone
git clone https://github.com/Rvhoyos/QuackedMod.git
cd QuackedMod

# Build both loaders
./gradlew :fabric:build
./gradlew :neoforge:build

# Output JARs
# fabric/build/libs/quack-fabric-*.jar
# neoforge/build/libs/quack-neoforge-*.jar

# Clean build if needed
./gradlew :fabric:clean :fabric:build
./gradlew :neoforge:clean :neoforge:build
```

---

## License

All Rights Reserved. You may not redistribute, modify, or use this mod's assets or source code without explicit permission.
