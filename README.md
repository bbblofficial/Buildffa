# BuildFFA Plugin

A comprehensive BuildFFA plugin for Minecraft servers with Kit Editor and Infinite Food/Blocks.

**Created by Muvixo**
**Plugin author: VanSaMa**
**Made by PixelValley**

## Features

- **Infinite Food** - Food level never decreases, saturation stays full
- **Infinite Blocks** - Placed blocks are never consumed from inventory
- **Kit Editor** - Players can customize their own kits
- **Void Kill** - Players die when falling below a configurable Y level
- **High Limit** - Building and PvP disabled above a configurable Y level
- **Auto Kit** - Automatic kit giving on join/respawn
- **Kill Tracking** - Kill counter with rewards
- **Block Decay** - Placed blocks disappear after a short time
- **No Fall Damage** - Fall damage disabled
- **No Mob Spawning** - Mobs are prevented from spawning
- **Welcome Messages** - Customizable join/quit messages
- **Fully Configurable** - Everything configurable via config.yml

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/buildffa help` | Show help menu | - |
| `/buildffa kiteditor` | Open the Kit Editor | `buildffa.kiteditor` |
| `/buildffa kiteditor save` | Save your kit | `buildffa.kiteditor` |
| `/buildffa kiteditor cancel` | Cancel editing | `buildffa.kiteditor` |
| `/buildffa kiteditor reset` | Reset your kit | `buildffa.kiteditor` |
| `/buildffa setvoid` | Set void kill height to current Y | `buildffa.setvoid` |
| `/buildffa sethighlimit` | Set high limit to current Y | `buildffa.sethighlimit` |
| `/buildffa creator` | Show plugin credits | - |
| `/buildffa reload` | Reload configuration | `buildffa.reload` |

## Building

### Local Build

```bash
mvn clean package
```

### GitHub Actions

Push to the `main` or `master` branch and GitHub Actions will automatically build the plugin and create a release.

## Credits

- **Muvixo** - Creator
- **VanSaMa** - Plugin author
- **PixelValley** - Development team

## License

MIT License
