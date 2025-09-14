# QuackedDucks 1.0

A "lightweight" cross-loader Minecraft mod that adds ducks with simple AI, natural spawns, sounds, items, and hatchable eggs. Built with Architectury to support both Fabric and NeoForge. GeckoLib5 for duck model and animations.
>V1.0.0 Got CurseForge Approval! Just search Ducky Quack Pack!
## Features
- Duck entity (ambient/hurt/death sounds, basic behaviors, follow/line logic, egg-laying).
- Custom Duck entity AI keep ducks in a line or always in order.
- Duck items, foods with crafts and recipes.
- - (mod-wiki coming soon.)
- Natural spawns across selected overworld biomes (rivers, swamps, plains, forests, etc.).
- Items: duck egg (throwable; chance to hatch ducklings), spawn egg, meat/food variants, feathers, more.
- Fabric and NeoForge builds from the same codebase.
## Coming to V1.1
- plug-in config for custom biomes (from other mods) and adjustbale spawn rate.
- more animations, sounds and textures.
- flying ducks is a planned feature.
## Game/Loader Compatibility
- **Minecraft:** 1.21.8
- **Fabric:** Loader `0.17.2` (or newer), **Fabric API** required
- **NeoForge:** `21.8.x`
- **Shared libs (both loaders):** Architectury `17.0.8+`, GeckoLib `5.x`

## Manual Installation (Client)
> jars can be found under modloader folder / build / libs / Quack-<modloader>-1.0.0.jar
### Fabric client
1. Install a Fabric profile for Minecraft 1.21.8 (via the Fabric installer/launcher).
2. In your game directory (`.minecraft/mods`):
   - Add `architectury-17.0.8+ (fabric).jar`
   - Add `geckolib-5.x (fabric).jar`
   - Add `fabric-api (1.21.8).jar`
   - Add `quack-fabric-<version>.jar`
   > from official sources.

### NeoForge client
1. Install NeoForge for 1.21.8.
2. In your game directory (`.minecraft/mods`):
   - Add `architectury-17.0.8+ (neoforge).jar`
   - Add `geckolib-5.x (neoforge).jar`
   - Add `quack-neoforge-<version>.jar`

## Manual Installation (Dedicated Server)

### server (or neoforge)
1. Place the Fabric or Neoforge server launcher jar for **1.21.8** (e.g. `fabric-server-mc.1.21.8-loader.0.17.2-launcher.1.1.0.jar`).
2. Run once to generate files, accept the EULA in `eula.txt`.
3. Put these in the server `mods/`:
   - `architectury-17.0.8+ (fabric).jar`
   - `geckolib-5.x (fabric).jar`
   - `fabric-api (1.21.8).jar`
   - `quack-fabric-<version>.jar`
4. Start the server:
