# BuildFFA Plugin v4.0 (1.8.8)

BuildFFA plugin for **Minecraft 1.8.8** — CarbonSpigot compatible.

**Created by Muvixo**
**Plugin author: VanSaMa**
**Made by PixelValley**

## What's New in v4.0

- **PlaceholderAPI support** — stats & top leaderboards usable in holograms
- **GUI Kit Editor** — Clean inventory menu with Save/Cancel/Reset buttons
- **No Item Drops** — Broken blocks vanish, items can't be dropped
- **No Item Pickups** — Players can't pick up dropped items
- **Auto Kit Restore** — Kit returns after `/clear`, death, void, or empty inventory
- **Fixed High Limit** — No more disappearing blocks at the limit
- **Infinite Food & Blocks** — Kept from v3.3
- **Safe config updates** — New options are merged into config.yml without wiping your settings

## Commands

| Command | Description |
|---------|-------------|
| `/buildffa help` | Show help |
| `/buildffa kiteditor` | Open GUI kit editor |
| `/buildffa kiteditor reset` | Reset kit to default |
| `/buildffa setvoid` | Set void kill height |
| `/buildffa sethighlimit` | Set high Y limit |
| `/buildffa creator` | Show credits |
| `/buildffa reload` | Reload config |

## PlaceholderAPI Placeholders

Identifier: `buildffa`

| Placeholder | Returns |
|---|---|
| `%buildffa_kills%` | Player's kills |
| `%buildffa_deaths%` | Player's deaths |
| `%buildffa_kdr%` | Player's KDR |
| `%buildffa_killstreak%` | Player's current killstreak |
| `%buildffa_best_killstreak%` | Player's best killstreak |
| `%buildffa_top_name_kills_1%` | Name of #1 player by kills |
| `%buildffa_top_value_kills_1%` | Kills of #1 player |
| `%buildffa_top_name_deaths_1%` | Name of #1 by deaths |
| `%buildffa_top_value_kdr_1%` | KDR of #1 player |
| `%buildffa_top_name_killstreak_1%` | Name of #1 by best streak |
| `%buildffa_top_rank_kills%` | Viewer's own rank by kills |
| `%buildffa_top_rank_kdr%` | Viewer's own rank by KDR |

Ranks 1–50 supported. Types: `kills`, `deaths`, `kdr`, `killstreak` (or `streak`).

## Installation

1. Drop JAR into `plugins/`
2. Make sure **PlaceholderAPI** is installed (for placeholders)
3. Restart server
4. Config auto-created / safely merged

## Credits

- **Muvixo** — Creator
- **VanSaMa** — Plugin author
- **PixelValley** — Team