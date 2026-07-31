# Advancements

A BentoBox addon that ties vanilla Minecraft advancements to islands. Works with island
game modes such as BSkyBlock, AcidIsland and CaveBlock.

## Features

- **Island-wide advancements** — advancements are earned once per island and synced to all
  team members. Join a team and you get the island's advancements; leave and (optionally)
  yours are reset.
- **Protection range growth** — like the Boxed game mode, each advancement can grow the
  island's protected area by a configurable amount (`advancements.yml`).
- **Rewards** — like the Challenges addon, each advancement can reward the player with
  items, experience, money (Vault) and commands.
- **Visitor protection** — advancements completed while visiting someone else's island are
  revoked (configurable).
- **Placeholders** — `%advancements_island_count%` and `%advancements_visited_island_count%`.

## Configuration

- `config.yml` — game mode hooks, broadcast, visitor denial and team join/leave resets.
- `advancements.yml` — per-advancement protection range increases and rewards, plus
  defaults for roots, recipes and unlisted advancements.
