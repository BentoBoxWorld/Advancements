# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Advancements is a BentoBox addon that ties vanilla Minecraft advancements to islands: advancements are earned once per island, synced to all team members, grow the island's protection range (mechanic ported from the Boxed game mode), and give Challenges-style rewards (items, experience, Vault money, commands) to the player.

## Build Commands

Maven project targeting Java 21 / Paper 1.21.11 / BentoBox 3.17.0.

```bash
mvn clean package          # default goal; builds target/Advancements-*.jar
mvn test                   # run JUnit 5 + MockBukkit test suite
mvn test -Dtest=AdvancementsManagerTest   # run a single test class
```

Version is controlled by `build.version` in pom.xml; Jenkins CI profiles append build numbers and strip `-SNAPSHOT` on the master branch.

## Architecture

Production code under `src/main/java/world/bentobox/advancements/`:

- **Advancements** (addon entrypoint, declared in `addon.yml`; `AdvancementsPladdon` + `plugin.yml` wrap it as a Paper plugin) — loads `Settings`, registers the listener, hooks into every GameModeAddon *not* listed in the config's `disabled-game-modes` (note the inverted filter; Boxed is disabled by default because it has its own advancements), and registers the two placeholders. `isRegisteredGameModeWorld(World)` is the world-scoping check used everywhere else.
- **AdvancementsManager** — the core. Caches `IsleAdvancements` per island uniqueId (persisted via BentoBox `Database`, saved async, flushed in `onDisable`) and loads `advancements.yml`. `addAdvancement(Player, Advancement)` returns a `Result(score, reward)`: it checks world/island/rank (≥ MEMBER_RANK), records the advancement once per island, grows the protection range by the score (clamped to `[1, island.getRange()]`) firing an `IslandEvent.Reason.RANGE_CHANGE` event, and pays the reward. An advancement with score 0 and empty reward is not tracked at all. `checkIslandSize()` self-heals the range on world entry: baseline is the game mode's default protection range (`IWM.getIslandProtectionRange`) plus the sum of stored scores.
- **Reward** (record) — parsed from a YAML `rewards:` section: items (BentoBox `ItemParser` format, overflow drops at the player's feet), experience, money (via `plugin.getVault()`), commands (run through `Util.runCommands`, which supports `[player]` and the `[SUDO]` prefix).
- **listeners/AdvancementListener** — event wiring, ported from Boxed. `PlayerAdvancementDoneEvent` (survival only): revokes advancements from non-members if `deny-visitor-advancements`, otherwise records/rewards and tells the team one tick later. Join/world-change re-grant the island's stored advancements and run `checkIslandSize` (one-way sync — grants only; a true reset requires `clearAdv`, which also zeroes statistics). Team join/leave and new-island events optionally clear and grant per `Settings`. `PlayerPortalEvent` manually awards nether/end story+root advancements since portals in addon worlds don't trigger them naturally.
- **Settings** — config POJO with BentoBox `@ConfigEntry` annotations, stored at `addons/Advancements/config.yml`. Editing config structure means editing this class; `config.yml` in resources is only the shipped default and must be kept in sync manually.
- **objects/IsleAdvancements** — the persisted `DataObject`: island uniqueId + list of advancement keys (full namespaced form, e.g. `minecraft:adventure/bullseye`). Fields must be `@Expose`d.

### advancements.yml schema

Keys are namespaced-key paths *without* the `minecraft:` prefix (e.g. `'adventure/bullseye'`). An entry is either a plain int (protection-range increase only) or a section with `protection-range` and/or `rewards`. Unlisted advancements fall back to category defaults in the `settings:` section: roots (`*/root`) → `default-root-*`, recipes (`recipes/*`) → `*-recipe-*`, everything else → `unknown-advancement-*`. An explicit `rewards:` section on an entry overrides the category default reward. Note the asymmetry: config lookups use the path only, but the island's stored list and `Settings` grant-lists use the full `minecraft:`-prefixed key.

## Tests

JUnit 5 + Mockito + MockBukkit, following the CaveBlock/DimensionalTrees pattern:

- `CommonTestSetup` — base class: MockBukkit server, static `Bukkit`/`Util` mocks, BentoBox singleton injection via `WhiteBox`, mocked managers. Subclasses call `super.setUp()`/`super.tearDown()`.
- Addon tests build a real JAR (`addon.jar`) containing `config.yml`/`advancements.yml` because `onLoad`/`onEnable` extract resources from it. Manager tests instead pre-write `advancements.yml` into a stubbed data folder since `saveResource` on a mocked addon is a no-op.
- `DatabaseSetup` must be statically mocked in any test that constructs `AdvancementsManager` (it creates a `Database`).
- Don't assert addon `State.LOADED` after `onLoad()` — assert `getSettings() != null` instead (BentoBox Config can set DISABLED in a test context).
