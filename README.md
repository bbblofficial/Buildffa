# BuildFFA Plugin

![Version](https://img.shields.io/badge/version-3.1-blue)
![Minecraft](https://img.shields.io/badge/minecraft-1.13+-green)
![License](https://img.shields.io/badge/license-MIT-orange)

A comprehensive BuildFFA plugin for Minecraft servers with Kit Editor support.

**Created by Muvixo**
**Plugin author: VanSaMa**
**Made by PixelValley**

## Features

- **Kit Editor** - Players can customize their own kits
- **Void Kill** - Players die when falling below a configurable Y level
- **High Limit** - Building and PvP disabled above a configurable Y level
- **Auto Kit** - Automatic kit giving on join/respawn
- **Kill Tracking** - Kill counter with rewards
- **Block Decay** - Placed blocks disappear after a short time
- **No Fall Damage** - Fall damage disabled
- **No Item Pickup** - Items cannot be picked up
- **No Mob Spawning** - Mobs are prevented from spawning
- **Welcome Messages** - Customizable join/quit messages
- **Fully Configurable** - Everything configurable via config.yml

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/buildffa help` | Show help menu | - |
| `/buildffa kiteditor` | Open the Kit Editor | `buildffa.kiteditor` |
| `/buildffa kiteditor reset` | Reset your kit | `buildffa.kiteditor` |
| `/buildffa setvoid` | Set void kill height to current Y | `buildffa.setvoid` |
| `/buildffa sethighlimit` | Set high limit to current Y | `buildffa.sethighlimit` |
| `/buildffa creator` | Show plugin credits | - |
| `/buildffa reload` | Reload configuration | `buildffa.reload` |

## Kit Editor

The Kit Editor allows players to customize their own kit:

1. Use `/buildffa kiteditor` to open the editor
2. Arrange your inventory how you want your kit
3. Type `/buildffa kiteditor save` to save (or add a save button)
4. Type `/buildffa kiteditor cancel` to cancel

## Installation

1. Download the latest release
2. Place the JAR file in your server's `plugins` folder
3. Restart your server
4. Configure `config.yml` as needed

## Building

This project uses Maven and GitHub Actions for automated builds.

### Local Build

```bash
mvn clean package
```

The compiled JAR will be in the `target/` folder.

### GitHub Actions

Push to the `main` or `master` branch and GitHub Actions will automatically build the plugin and create a release.

## Configuration

```yaml
# The Y level at which players die (void kill height)
kill-height: 0.0

# The Y level above which building and PvP is disabled
high-limit: 100.0

# Message sent when a player kills another player
kill: "&e%killer% &7killed &e%loser% &7(&e%killcount% &7kills)"

# ... and more
```

## Credits

- **Muvixo** - Creator
- **VanSaMa** - Plugin author
- **PixelValley** - Development team

## License

MIT License - See LICENSE file for details
