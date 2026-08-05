# CubixSMP

![Development status](https://img.shields.io/badge/status-Stable-brightgreen)

**CubixSMP** — a Paper 1.21.4 plugin that adds an advanced leveling system (Cubix Level) to your SMP server. Players earn experience (XP) for various in-game activities and level up.

🌍 Originally built for Russian-language SMP servers, but every message is fully translatable via the `messages` section in `config.yml`.

---

## 📋 Table of contents

- [Features](#-features)
- [XP sources](#-xp-sources)
- [Commands and permissions](#-commands-and-permissions)
- [Placeholders (PlaceholderAPI)](#-placeholders-placeholderapi)
- [Installation](#-installation)
- [Configuration](#-configuration)
- [Building from source](#-building-from-source)
- [Requirements](#-requirements)
- [Project structure](#-project-structure)
- [FAQ](#-faq)
- [License](#-license)

---

## ✨ Features

- **8 activities** that grant XP (mining, farming, woodcutting, fishing, hunting, exploration, online time, daily bonus)
- **Flexible leveling system** — the XP formula is configurable in `config.yml`
- **Cheater protection** — XP is only awarded for naturally generated blocks and mobs
- **PlaceholderAPI** — integration with TAB, Scoreboards, Chat and other plugins
- **Fully translatable** — all messages live in the `messages` section of `config.yml`
- **⚒ Anvil enchant cap** — combining two books in an anvil can never exceed the vanilla enchantment limit (e.g. two Efficiency V books won't give Efficiency VI; over-limit enchants can only be bought with donate currency)
- **📍 Region ActionBar** — shows the name of the WorldGuard region the player is standing in, colored green if they have access and red if they don't
- **🔢 Account UID** — every new player gets a sequential account number (1st player = 1, 2nd = 2, …), shown via the `%cubixsmp_uid%` placeholder and stored permanently in `playerdata/`

---

## ⛏ XP sources

| Activity | Description | XP |
|:--------:|-------------|:--:|
| ⛏ Mining | Mining ores (coal → ancient debris) | 1–10 XP |
| 🌾 Farming | Harvesting crops, pumpkins, melons, berries, honey | 0.5–5 XP |
| 🌲 Woodcutting | Any log (oak, spruce, birch, jungle, acacia, cherry, mangrove, nether stems) | 0.2 XP |
| 🎣 Fishing | Any caught fish | 5 XP |
| ⚔ Hunting | Killing mobs (zombie 3 XP, warden 50 XP, wither, etc.) | 1–50 XP |
| 🚶 Distance | Every 1000 blocks traveled | 5 XP |
| ⏱ Online time | Every 30 minutes of play | 10 XP |
| ☀ Daily bonus | Once per day via `/cubixsmp daily` | 50 XP |

> **Important:** XP is ONLY awarded for natural resources. Player-placed blocks, mobs from spawners and spawn eggs grant no XP.

---

## 🎮 Commands and permissions

### Commands

| Command | Aliases | Description | Permission |
|---------|---------|-------------|------------|
| `/cubixsmp` | `/cs`, `/cubix`, `/csmp` | Show stats (level, XP, progress) | `cubixsmp.user` |
| `/cubixsmp stats` | | Show stats | `cubixsmp.user` |
| `/cubixsmp daily` | | Claim the daily bonus | `cubixsmp.user` |
| `/cubixsmp sound` | | Toggle XP sound on/off | `cubixsmp.user` |
| `/cubixsmp leaders` | | Top players by level | `cubixsmp.user` |
| `/cubixsmp reload` | | Reload the configuration | `cubixsmp.reload` |
| `/cubixsmp admin` | | Admin commands | `cubixsmp.admin` |

### Admin commands

`/cubixsmp admin <subcommand> [args]`

| Subcommand | Description |
|------------|-------------|
| `info <player>` | Player info (level, XP, playtime) |
| `setlevel <player> <level>` | Set a level |
| `addxp <player> <amount>` | Add XP |
| `removexp <player> <amount>` | Remove XP |
| `reset <player>` | Reset a player's progress |

### Permissions

| Permission | Description | Default |
|------------|-------------|:-------:|
| `cubixsmp.user` | Basic commands | ✅ true |
| `cubixsmp.reload` | Reload the config | ❌ op |
| `cubixsmp.admin` | Admin access | ❌ op |
| `cubixsmp.admin.setlevel` | Set level | ❌ op |
| `cubixsmp.admin.addxp` | Add XP | ❌ op |
| `cubixsmp.admin.removexp` | Remove XP | ❌ op |
| `cubixsmp.admin.reset` | Reset progress | ❌ op |
| `cubixsmp.admin.info` | Player info | ❌ op |

---

## 🔌 Placeholders (PlaceholderAPI)

If PlaceholderAPI is installed on the server, the following placeholders are available:

| Placeholder | Description | Example |
|-------------|-------------|---------|
| `%cubixsmp_uid%` | Player's account number (1st player = 1, 2nd = 2, …) | `7` |
| `%cubixsmp_level%` | Player's current level | `42` |
| `%cubixsmp_xp%` | Current XP | `150` |
| `%cubixsmp_level_xp_needed%` | XP needed for the next level | `200` |
| `%cubixsmp_level_progress%` | Progress percentage | `42%` |
| `%cubixsmp_level_playtime%` | Total playtime | `3h 15m` |
| `%cubixsmp_action%` | Last action | `Mining` |

### Usage examples

**In TAB (Nametag):**
```
%cubixsmp_level% §7Level
```
→ `§e42 §7Level`

**In a Scoreboard:**
```
§7XP: %cubixsmp_xp%§7/§a%cubixsmp_level_xp_needed%
```
→ `§7XP: §e150§7/§a200`

**In chat:**
```
§7[§6⚡%cubixsmp_level%§7] §f%player_name%
```
→ `§7[§6⚡42§7] §frizer001`

---

## 📦 Installation

1. Download `CubixSMP-1.2.1.jar` from the [releases page](https://github.com/rizer001/CubixSMP/releases)
2. Place the JAR in your server's `plugins/` folder
3. (Optional) Install **PlaceholderAPI** for placeholder support
4. Restart the server or run `/reload`
5. Configure the XP values in `plugins/CubixSMP/config.yml`
6. Run `/cubixsmp reload` to apply the changes

---

## ⚙ Configuration

All XP settings are in `config.yml`. File structure:

```yaml
settings:
  xp-base: 100                # XP for level 0→1
  xp-multiplier: 1.5          # Each level requires (base + level × multiplier) XP
  min-level: 1                # Starting level
  min-level-xp: 100           # XP for the first level-up (min-level → min-level+1)
  max-level: 100              # Maximum level
  distance-interval: 1000     # Blocks per XP tick
  xp-per-distance-interval: 5 # XP per interval
  playtime-interval: 1800     # Seconds per XP tick
  xp-per-playtime-interval: 10
  daily-bonus-xp: 50          # XP for the daily bonus
  use-actionbar: true         # true = actionbar, false = chat
  leaders-limit: 10           # Players in the leaderboard

mining:
  enabled: true
  blocks:
    COAL_ORE: 1.0
    IRON_ORE: 3.0
    DIAMOND_ORE: 5.0
    ANCIENT_DEBRIS: 10.0

farming:
  enabled: true
  crops:
    WHEAT: 0.5
    PUMPKIN: 1.0
    MELON: 1.0

woodcutting:
  enabled: true
  logs:
    OAK_LOG: 0.2
    SPRUCE_LOG: 0.2

fishing:
  enabled: true
  xp-per-catch: 5.0

hunting:
  enabled: true
  mobs:
    ZOMBIE: 3.0
    CREEPER: 4.0
    WARDEN: 50.0

anvil:
  enabled: true               # Cap enchant levels at the vanilla max in the anvil

region-actionbar:
  enabled: true               # Show the current WorldGuard region in the ActionBar
  message-with-access: "§7Region §a{region}"        # {region} — region name
  message-without-access: "§7Region §c{region}"

uid:
  enabled: true               # Assign a sequential account number to every new player
  starting-number: 1          # First assigned number (e.g. 2 → 2, 3, 4, …)
```

**XP formula per level:**
```
XP_needed = xp-base + (current_level × xp-multiplier)

Level 0→1:  100 + (0 × 1.5)  = 100  XP
Level 1→2:  100 + (1 × 1.5)  = 101.5 XP
Level 99→100: 100 + (99 × 1.5) = 248.5 XP
```

---

## 🔨 Building from source

```bash
git clone https://github.com/rizer001/CubixSMP.git
cd CubixSMP
./gradlew shadowJar
```

Result: `build/libs/CubixSMP-1.2.1.jar` (also copied to `Jar/CubixSMP-1.2.1.jar`)

---

## 📋 Requirements

- **Server:** Paper 1.21.4 (or its forks: Purpur, Pufferfish, etc.)
- **Java:** 21+
- **Optional:** PlaceholderAPI 2.11+ — placeholders
- **Optional:** WorldGuard 7.0.13+ — region ActionBar

---

## 📁 Project structure

```
CubixSMP/
├── build.gradle              — Build system (Gradle + Shadow)
├── settings.gradle
├── gradlew / gradlew.bat     — Gradle Wrapper
├── src/main/java/com/cubixsmp/
│   ├── CubixSMP.java              — Main class
│   ├── CubixSMPCommand.java       — Command handler
│   ├── CubixSMPTabCompleter.java  — Tab completer
│   ├── CubixSMPPlaceholderExpansion.java — PAPI placeholders
│   ├── LevelManager.java          — Level/XP manager
│   ├── PlayerDataManager.java     — Player data storage (YAML)
│   ├── NaturalCheck.java          — Natural block/mob checks
│   ├── MessagesManager.java       — Message management
│   ├── ConfigGuideManager.java    — plugin-guide.txt management
│   ├── PlacedBlockTracker.java    — Placed block tracker (chunk PDC)
│   ├── PingSettingsManager.java   — Ping sound settings
│   ├── PlaytimeTracker.java       — Daily playtime tracker
│   └── listeners/
│       ├── MiningListener.java        — Mining
│       ├── FarmingListener.java       — Farming
│       ├── WoodcuttingListener.java   — Woodcutting
│       ├── FishingListener.java       — Fishing
│       ├── HuntingListener.java       — Hunting
│       ├── DistanceListener.java      — Distance
│       ├── PlaytimeListener.java      — Online time
│       ├── DailyBonusListener.java    — Daily bonus
│       ├── ChatMentionListener.java   — @ping in chat
│       ├── ChatPlaceholderListener.java — Per-player chat placeholders
│       ├── LeafDurabilityListener.java — Axes don't lose durability on leaves
│       ├── FarmlandTrampleListener.java — No farmland trampling
│       ├── AnvilEnchantListener.java  — Vanilla enchant cap in the anvil
│       └── RegionActionBarListener.java — WorldGuard region in the ActionBar
├── src/main/resources/
│   ├── plugin.yml              — Plugin description
│   ├── config.yml              — XP configuration and messages
│   └── plugin-guide.txt        — Link to the README
└── Jar/                        — Ready-to-use builds
```

### Dependencies

- `io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT` (compileOnly)
- `me.clip:placeholderapi:2.11.6` (compileOnly, optional)
- `com.sk89q.worldguard:worldguard-bukkit:7.0.14` (compileOnly, optional)

---

## ❓ FAQ

**Q: Why am I not getting XP for ore?**
A: Check that the section is enabled (`enabled: true`). Make sure the ore is natural (surrounded by stone, not air). Deepslate ore variants must be listed in `config.yml` separately.

**Q: Can I add XP for new blocks/mobs?**
A: Yes! Just add `MATERIAL_NAME: XP` to the corresponding `config.yml` section and run `/cubixsmp reload`.

**Q: Does the plugin work on Spigot/CraftBukkit?**
A: No, Paper 1.21.4 or a fork is required. On Spigot the mob naturalness check will work with limitations.

**Q: How do I reset a player's progress?**
A: Delete the file `playerdata/<player UUID>.yml` and reload the plugin.

**Q: Why aren't the placeholders working?**
A: Make sure PlaceholderAPI is installed. Run `/papi info CubixSMP`. If the expansion isn't registered — restart the server.

**Q: How do I change XP for a specific activity?**
A: Edit `config.yml`. For ores — `mining.blocks.MATERIAL: XP`. For mobs — `hunting.mobs.ENTITY_TYPE: XP`. Then run `/cubixsmp reload`.

**Q: Is the plugin cheater-proof?**
A: The plugin uses several checks: a placed-block tracker (chunk PDC), static environment analysis (natural stone/leaves), the Paper API for mob spawn reasons, plus a fallback that searches for spawners in nearby chunks.

**Q: Why don't I see the region name in the ActionBar?**
A: The region ActionBar requires **WorldGuard** to be installed. Make sure it's present in `plugins/` and that `region-actionbar.enabled` is `true` in `config.yml`.

**Q: Why can't I combine two Efficiency V books into Efficiency VI?**
A: That's intended — the anvil is capped at the vanilla enchantment limit. Enchantments above the vanilla max (e.g. Efficiency VI) can only be obtained by buying them with donate currency.

**Q: What is the player's account number (UID) and how do I change the starting one?**
A: Every new player gets a sequential account number shown by `%cubixsmp_uid%` — it is not their Minecraft UUID. Set the starting number in `config.yml` (`uid.starting-number`); numbers are saved permanently in the player's `playerdata/<UUID>.yml` file and never change.

---

## 📄 License

This project is distributed under the **GNU Affero General Public License v3.0**.  
The full license text can be found in the [LICENSE](./LICENSE) file.

Copyright © 2026 rizer001

---

<p align="center">
  <b>⚡ Thank you for using CubixSMP! Have fun on your server! ⚡</b>
</p>
