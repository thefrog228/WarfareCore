# WarfareCore

A mostly-vanilla anarchy server plugin for **Paper 1.21.11** (Java 21). Adds a
custom claim system, three TNT tiers for progression-based raiding, custom items,
configurable crafting recipes, and refillable loot chests — all behind a modular,
expandable architecture.

## Building

You need a machine with internet access (to download Paper + Gradle) and Java 21.

```bash
cd WarfareCore
./gradlew build
```

The finished plugin jar is written to:

```
build/libs/WarfareCore-1.0.0.jar
```

Drop that single jar into your server's `plugins/` folder and restart. The plugin
generates its own `config.yml` and data files on first run.

> On Windows, use `gradlew.bat build` instead of `./gradlew build`.

## Commands

All commands are under `/warfare` (aliases: `/wc`, `/wcore`).

| Command | Permission | Description |
| --- | --- | --- |
| `/warfare reload` | `warfare.command.reload` | Reload config, explosion block sets, and loot tables. |
| `/warfare give <type> [amount]` | `warfare.command.give` | Get a custom item (e.g. `packed_tnt`). |
| `/warfare loot create <table>` | `warfare.command.loot` | Bind the chest you're looking at to a loot table. |
| `/warfare loot remove` | `warfare.command.loot` | Unregister the targeted loot chest. |
| `/warfare loot refill` | `warfare.command.loot` | Immediately refill the targeted loot chest. |
| `/warfare loot list` | `warfare.command.loot` | List loot tables and registered chest count. |

Bypass all claim protection with `warfare.claims.bypass`.

## Systems

- **Claims** — place a Claim Block (or Advanced Claim Block) to protect a
  configurable radius. Protection covers breaking, placing, buckets, interaction,
  pistons, fluids, fire, and ordinary explosions. Per-player claim limit enforced.
- **TNT tiers** — Normal (vanilla recipe, larger radius), Packed (9 TNT, breaks
  obsidian-class blocks), Reinforced (breaks claim blocks). Vanilla blast
  resistance is never used; the plugin controls every broken block by tier.
- **Custom items** — built on vanilla materials with a hidden identity tag, so
  detection survives renaming/repacking. CustomModelData numbers are configurable
  for a future resource pack.
- **Loot chests** — bind chests to weighted loot tables; they auto-refill on a
  fixed timer (default 15 minutes).
- **Storage** — claims and loot chests persist via a swappable backend. YAML is
  implemented; SQLite is stubbed behind the same interface (`storage.type`).

## Configuration

Everything practical is configurable in `config.yml`: claim radii and limit,
explosion radii/shape/drops, strong-block and never-break lists, all recipes,
loot tables and refill timing, every message (MiniMessage or `&` codes), and
permission nodes.

## Architecture notes

- Never scans worlds. Claim lookups use a chunk index; explosions only touch
  blocks within the radius; loot uses a single shared refill task.
- Each major system is its own manager wired in dependency order by the main
  class, making it straightforward to add economy, auctions, leaderboards, more
  TNT tiers, custom mobs/ores, etc.
