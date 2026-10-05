# Minecraft Report Plugin

**Advanced player reporting system for Spigot / Paper servers** with beautiful Discord embeds, player avatars, smart cooldowns, and weekly same-player report limits.

![Minecraft](https://img.shields.io/badge/Minecraft-1.21+-green)
![Paper](https://img.shields.io/badge/Paper-Supported-blue)
![License](https://img.shields.io/badge/License-MIT-yellow)

---

## ✨ Features

- **`/report <player> <reason>`** — Simple and clean reporting command
- **Fancy Discord Embeds** with:
  - Reporter avatar (author icon)
  - Reported player avatar (thumbnail)
  - Reason, server name, world & exact location
  - Timestamp and professional styling
- **Anti-Spam Protection**
  - Global cooldown (default 60 seconds between any reports)
  - **Same-player limit**: A player can only report the same person **once per week**
- Persistent cooldown storage (`cooldowns.yml`)
- Offline player support
- Tab-completion for online players
- Fully configurable messages, colors, and timings
- Asynchronous webhook sending (no lag)

---

## 📥 Installation

1. Download the latest release JAR (or build it yourself — see below)
2. Place the JAR into your server's `plugins/` folder
3. Start / restart the server
4. Edit `plugins/MinecraftReportPlugin/config.yml` and paste your Discord webhook URL
5. Reload or restart again

---

## ⚙️ Configuration

```yaml
webhook-url: "https://discord.com/api/webhooks/..."

server-name: "My Minecraft Server"

embed:
  color: 15158332          # Red
  title: "🚨 New Player Report"
  footer: "Minecraft Report System • Anti-Spam Protected"

cooldowns:
  global-seconds: 60       # Cooldown between any reports
  same-player-seconds: 604800  # 1 week (7 * 24 * 60 * 60)
```

All messages support `&` color codes.

---

## 🛠️ Building from Source

Requirements:
- Java 17+
- Maven

```bash
git clone https://github.com/someone405-ship-it/MinecraftReportPlugin.git
cd MinecraftReportPlugin
mvn clean package
```

The compiled JAR will be in `target/MinecraftReportPlugin-1.0.0.jar`

---

## 📋 Commands & Permissions

| Command              | Description                          | Permission            |
|----------------------|--------------------------------------|-----------------------|
| `/report <player> <reason>` | Report a player                 | `reportplugin.report` |
| (admin bypass)       | Bypass all cooldowns                 | `reportplugin.admin`  |

---

## 🖼️ Discord Embed Preview

The embed includes:

- **Author**: Reporter name + their Minecraft head
- **Thumbnail**: Reported player's Minecraft head
- **Fields**:
  - Reporter (name + UUID)
  - Reported Player (name + UUID)
  - Reason
  - Server name
  - World + exact coordinates of the reporter
- Clean red color + timestamp + footer

---

## 🔒 Safety Features

- Players cannot report themselves
- Minimum reason length (configurable)
- Global anti-spam cooldown
- One report per target per week per reporter
- Admins can bypass all limits

---

## License

MIT License – free to use and modify.

Made with ❤️ for Minecraft server owners.
