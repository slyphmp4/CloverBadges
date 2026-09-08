<div align="center">

# CloverBadges

**Badges, nickname gradients, message colors and configurable player personalization for Minecraft servers.**

[![Build](https://github.com/slyphmp4/CloverBadges/actions/workflows/build.yml/badge.svg)](https://github.com/slyphmp4/CloverBadges/actions/workflows/build.yml)
[![Latest Release](https://img.shields.io/github/v/release/slyphmp4/CloverBadges?include_prereleases&style=flat-square)](https://github.com/slyphmp4/CloverBadges/releases)
[![Java](https://img.shields.io/badge/Java-25-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Paper API](https://img.shields.io/badge/Paper_API-26.2-2C2C2C?style=flat-square)](https://papermc.io/)
[![PlaceholderAPI](https://img.shields.io/badge/PlaceholderAPI-optional-4B8BBE?style=flat-square)](https://www.spigotmc.org/resources/placeholderapi.6245/)

[Releases](https://github.com/slyphmp4/CloverBadges/releases) · [Builds](https://github.com/slyphmp4/CloverBadges/actions) · [Issues](https://github.com/slyphmp4/CloverBadges/issues)

</div>

---

## Overview

CloverBadges is a Minecraft server plugin focused on player identity and cosmetic personalization.

It started as a badge system, but now combines several related features in one configurable plugin:

- multiple selectable player badges;
- automatic newcomer badges;
- nickname color gradients;
- message color gradients/styles for chat integrations;
- a configurable inventory GUI;
- PlaceholderAPI support;
- gradient nametags with TAB integration;
- temporary and permanent cosmetic grants;
- permission-aware commands and tab completion;
- YAML-based persistent player data;
- a small Bukkit service API for other plugins.

The default configuration allows up to **2 active badges** and **10 owned badges** per player. Both values can be changed.

---

## Table of contents

- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick start](#quick-start)
- [Commands](#commands)
- [Permissions](#permissions)
- [Duration format](#duration-format)
- [Configuration](#configuration)
- [Badges](#badges)
- [Nickname colors](#nickname-colors)
- [Message colors](#message-colors)
- [GUI](#gui)
- [Newcomer system](#newcomer-system)
- [First-join message color](#first-join-message-color)
- [Nametags](#nametags)
- [PlaceholderAPI](#placeholderapi)
- [Java API](#java-api)
- [Data storage](#data-storage)
- [Project structure](#project-structure)
- [Building from source](#building-from-source)
- [Troubleshooting](#troubleshooting)
- [License](#license)

---

## Features

### Badges

Players can own several badges and display up to the configured active limit at the same time.

Each badge can define:

- an internal ID;
- a colored display name;
- badge text/symbol;
- an optional permission requirement;
- priority;
- multi-line hover text.

Badges may be permanent or expire automatically.

### Nickname colors

CloverBadges includes a separate nickname color system with selectable gradients.

Nickname colors support:

- two or more gradient stops;
- priority sorting;
- optional permission requirements;
- temporary or permanent ownership;
- a configurable starter color;
- PlaceholderAPI output for external chat/tab systems;
- gradient nametag rendering.

### Message colors

Players can also own and select message color styles.

A message color may use either a legacy formatting string or a multi-stop gradient. CloverBadges stores the selected style and exposes it to integrations through PlaceholderAPI.

CloverBadges itself does **not** register a chat event listener that rewrites messages. A chat plugin can consume the provided message-color placeholders and apply the selected style.

### Configurable GUI

The main `/badge` command opens a GUI for players with `cloverbadges.menu`.

The GUI is configured through `gui.yml` and is designed to handle:

- badge selection;
- nickname color selection;
- message color selection;
- navigation between sections;
- item materials;
- custom heads;
- item names;
- lore;
- menu titles;
- slots and layout elements.

### Automatic newcomer badge

New players can automatically receive a temporary newcomer badge. The default configuration grants the `newcomer` badge for **7 days** and displays it automatically.

### Permission-aware tab completion

Administrative subcommands are only suggested when the command sender has the corresponding permission.

### HEX colors

CloverBadges supports standard legacy color codes and simple HEX codes.

Examples:

```text
&6Gold
&aGreen
&FF0000Red
&55FF88Mint green
```

The `&RRGGBB` format is normalized internally and parsed through Adventure serializers.

---

## Requirements

| Component | Version / status |
| --- | --- |
| Java | **25** |
| Minecraft / Paper API | **26.2** (`26.2.build.110-stable`) |
| PlaceholderAPI | Optional, recommended |
| TAB | Optional, used by the nametag bridge |
| UltraPermissions | Optional soft dependency / permission manager |

The plugin is compiled against Paper API 26.2 and uses Adventure components where appropriate.

`plugin.yml` currently declares `api-version: 1.16`.

---

## Installation

1. Download the latest `CloverBadges-<version>.jar` from [GitHub Releases](https://github.com/slyphmp4/CloverBadges/releases).
2. Place the JAR into your server's `plugins` directory.
3. Install PlaceholderAPI if you want to use CloverBadges placeholders in chat, TAB or other plugins.
4. Install TAB if you want CloverBadges to use the TAB nametag bridge.
5. Start or restart the server.
6. Configure the generated files in `plugins/CloverBadges/`.
7. Run `/badge reload` after configuration changes that do not require a full restart.

---

## Quick start

Open the player menu:

```text
/badge
```

Give a permanent badge:

```text
/badge give Steve badge newcomer permanent
```

Give a badge for seven days:

```text
/badge give Steve badge newcomer 7d
```

Give a nickname color:

```text
/badge give Steve paint spicy_apple permanent
```

Give a message color for two weeks:

```text
/badge give Steve messagepaint cotton_candy 2w
```

Remove an owned cosmetic:

```text
/badge remove Steve badge newcomer
/badge remove Steve paint spicy_apple
/badge remove Steve messagepaint cotton_candy
```

Reload CloverBadges:

```text
/badge reload
```

---

## Commands

Main command:

```text
/badge
```

Aliases:

```text
/badges
/cloverbadges
```

| Command | Description | Permission |
| --- | --- | --- |
| `/badge` | Opens the personalization GUI for a player | `cloverbadges.use`, `cloverbadges.menu` |
| `/badge give <player> <category> <id> [duration]` | Gives a badge, nickname color or message color | `cloverbadges.admin.give` |
| `/badge remove <player> <category> <id>` | Removes a badge, nickname color or message color | `cloverbadges.admin.remove` |
| `/badge reload` | Reloads plugin configuration and registries | `cloverbadges.admin.reload` |

Supported categories:

| Canonical category | Accepted command value |
| --- | --- |
| Badge | `badge` |
| Nickname color | `paint` |
| Message color | `messagepaint` |

Message color aliases accepted internally are `messagecolor`, `chatpaint` and `chatcolor`.

---

## Permissions

| Permission | Default | Purpose |
| --- | --- | --- |
| `cloverbadges.*` | OP | Grants normal usage and every admin permission |
| `cloverbadges.use` | Everyone | Allows use of the main command |
| `cloverbadges.menu` | Everyone | Allows opening the GUI |
| `cloverbadges.admin.*` | OP | Grants all CloverBadges administrative actions |
| `cloverbadges.admin.give` | OP | Allows `/badge give` |
| `cloverbadges.admin.remove` | OP | Allows `/badge remove` |
| `cloverbadges.admin.reload` | OP | Allows `/badge reload` |

Individual badge, nickname-color and message-color definitions can also contain their own custom permission nodes.

---

## Duration format

Grants can be permanent or temporary.

Permanent values:

```text
permanent
perm
forever
```

Temporary units:

| Unit | Meaning |
| --- | --- |
| `s` | seconds |
| `m` | minutes |
| `h` | hours |
| `d` | days |
| `w` | weeks |

Examples:

```text
30m
12h
7d
2w
1w2d3h30m
```

Duration parts can be combined without separators.

---

## Configuration

CloverBadges creates several configuration files.

| File | Purpose |
| --- | --- |
| `config.yml` | Global behavior, newcomer settings, limits, nametags, storage and external API settings |
| `badges.yml` | Badge definitions |
| `nickname-colors.yml` | Nickname gradient definitions and starter settings |
| `message-colors.yml` | Message gradient/style definitions |
| `gui.yml` | Inventory GUI layout, titles, items, lore and navigation |
| `messages.yml` | Plugin chat messages and command feedback |

### Important global options

Default `config.yml` behavior includes:

```yaml
newcomer:
  enabled: true
  badge-id: "newcomer"
  duration: "7d"
  auto-display: true

selection:
  clear-invalid-selection: true

display:
  max-badges: 2
  separator: " "

limits:
  max-owned-badges: 10

storage:
  save-on-change: true
  cleanup-interval-ticks: 6000
```

---

## Badges

Badge definitions live in `badges.yml`.

Example:

```yaml
badges:
  developer:
    name: "&55FF88Developer"
    text: "&55FF88◆"
    permission: "cloverbadges.badge.developer"
    priority: 100
    hover:
      - "&55FF88◆ &E0E0E0— &55FF88Developer"
      - "&7"
      - "&C2C2C2Part of the development team."
```

Fields:

| Field | Description |
| --- | --- |
| `name` | Human-readable colored name |
| `text` | The actual badge text/symbol displayed by placeholders |
| `permission` | Optional permission required for availability; leave empty for no additional permission |
| `priority` | Controls ordering; higher values sort before lower values |
| `hover` | Multi-line hover content available through the API/placeholders |

The default configuration contains a `newcomer` badge using the `✦` symbol.

---

## Nickname colors

Nickname colors live in `nickname-colors.yml`.

Example:

```yaml
colors:
  sunset:
    name: "&FF7A59Sunset"
    gradient:
      - "#FF4D6D"
      - "#FF9E64"
      - "#FFD166"
    permission: ""
    priority: 100
```

A gradient should contain at least two colors.

The default file also contains starter settings:

```yaml
starter:
  enabled: true
  color-id: spicy_apple
  auto-select: false
```

Ownership and selection are separate. A player may own several colors while having only one selected at a time.

---

## Message colors

Message colors live in `message-colors.yml`.

Gradient example:

```yaml
colors:
  cotton_candy:
    name: "&FFB7D5Cotton Candy"
    gradient:
      - "#FFB7D5"
      - "#D8C4FF"
      - "#BDE0FE"
    permission: ""
    priority: 200
```

Definitions support both a `format` value and a `gradient` list. When a valid gradient is present, CloverBadges uses the gradient as the selected style.

Useful integration placeholders include:

```text
%cloverbadges_message_color%
%cloverbadges_message_color_id%
%cloverbadges_message_color_gradient%
%cloverbadges_message_color_format%
```

A chat plugin can use these values to apply the player's selected style.

---

## GUI

`gui.yml` controls the inventory interface.

The GUI can be customized without recompiling the plugin, including:

- menu title;
- inventory size/layout;
- section navigation;
- item slots;
- item materials;
- player/custom heads;
- item display names;
- item lore;
- selected/unselected states;
- informational items;
- clear/reset actions;
- badge, nickname-color and message-color sections.

CloverBadges also contains optional Minecraft-Heads API settings in `config.yml`:

```yaml
minecraft-heads:
  api:
    enabled: true
    app-uuid: ""
    api-key: ""
    demo: false
    connect-timeout-ms: 5000
    read-timeout-ms: 10000
```

If you use external Minecraft-Heads API credentials, keep them private and do not commit real keys to a public repository.

---

## Newcomer system

The newcomer system is controlled from `config.yml`:

```yaml
newcomer:
  enabled: true
  badge-id: "newcomer"
  duration: "7d"
  auto-display: true
```

With the default configuration, a player's newcomer state is based on their first-seen data and lasts seven days.

Useful placeholders:

```text
%cloverbadges_newcomer%
%cloverbadges_newcomer_remaining%
```

---

## First-join message color

CloverBadges can grant a message color when a player joins for the first time.

Default configuration:

```yaml
first-join-message-color:
  enabled: true
  duration: "permanent"
  auto-select: false
  colors: []
```

If `colors` is empty, CloverBadges builds the candidate list from all available message colors and randomly chooses one that the player is allowed to use.

Set `auto-select: true` if the granted color should immediately become the player's selected message color.

---

## Nametags

CloverBadges includes a custom nametag system, primarily for gradient nicknames.

Default settings:

```yaml
nametag:
  enabled: true
  only-for-gradients: true
  renderer: "AUTO"
  hide-vanilla-with-tab: true
  use-tab-prefix-suffix: true
  armor-stand-fallback: true
  refresh-interval-ticks: 10
```

The implementation contains both TextDisplay and ArmorStand renderers. `AUTO` allows CloverBadges to choose the appropriate rendering path, while the ArmorStand renderer can be used as a fallback.

When TAB is installed, CloverBadges can use its API bridge to hide the vanilla nametag and preserve TAB prefix/suffix information around the custom display.

---

## PlaceholderAPI

PlaceholderAPI support is registered automatically when PlaceholderAPI is enabled on the server.

Identifier:

```text
cloverbadges
```

### Badge placeholders

| Placeholder | Output |
| --- | --- |
| `%cloverbadges_badge%` | Active badge text, including configured formatting |
| `%cloverbadges_badges%` | Alias of `badge` |
| `%cloverbadges_badge_spaced%` | Active badge output with a trailing space when non-empty |
| `%cloverbadges_badges_spaced%` | Alias of `badge_spaced` |
| `%cloverbadges_badge_id%` | Primary active badge ID |
| `%cloverbadges_badge_ids%` | Comma-separated active badge IDs |
| `%cloverbadges_badge_name%` | Primary active badge name |
| `%cloverbadges_badge_plain%` | Active badges without color formatting |
| `%cloverbadges_badges_plain%` | Alias of `badge_plain` |
| `%cloverbadges_badge_name_plain%` | Primary active badge name without formatting |
| `%cloverbadges_badge_1%` | First active badge text |
| `%cloverbadges_badge_2%` | Second active badge text |
| `%cloverbadges_badge_1_id%` | First active badge ID |
| `%cloverbadges_badge_2_id%` | Second active badge ID |
| `%cloverbadges_badge_1_name%` | First active badge name |
| `%cloverbadges_badge_2_name%` | Second active badge name |
| `%cloverbadges_owned_count%` | Number of owned badges |
| `%cloverbadges_active_count%` | Number of active badges |
| `%cloverbadges_separator%` | Configured badge separator |

### Nickname color placeholders

| Placeholder | Output |
| --- | --- |
| `%cloverbadges_colored_nickname%` | Player nickname rendered with the selected nickname color |
| `%cloverbadges_nickname_colored%` | Alias of `colored_nickname` |
| `%cloverbadges_nickname_color_id%` | Selected nickname color ID |
| `%cloverbadges_nickname_color_name%` | Selected nickname color display name |

### Message color placeholders

| Placeholder | Output |
| --- | --- |
| `%cloverbadges_message_color%` | Selected message style: gradient list or legacy format |
| `%cloverbadges_message_color_style%` | Alias of `message_color` |
| `%cloverbadges_chat_color%` | Alias of `message_color` |
| `%cloverbadges_chat_color_style%` | Alias of `message_color` |
| `%cloverbadges_message_color_id%` | Selected message color ID |
| `%cloverbadges_chat_color_id%` | Alias of `message_color_id` |
| `%cloverbadges_message_color_name%` | Selected message color display name |
| `%cloverbadges_chat_color_name%` | Alias of `message_color_name` |
| `%cloverbadges_message_color_gradient%` | Comma-separated selected gradient stops |
| `%cloverbadges_chat_color_gradient%` | Alias of `message_color_gradient` |
| `%cloverbadges_message_color_format%` | Selected legacy format string |
| `%cloverbadges_chat_color_format%` | Alias of `message_color_format` |
| `%cloverbadges_message_color_remaining%` | Remaining time for the selected message color |
| `%cloverbadges_chat_color_remaining%` | Alias of `message_color_remaining` |

### Newcomer placeholders

| Placeholder | Output |
| --- | --- |
| `%cloverbadges_newcomer%` | `true` or `false` |
| `%cloverbadges_newcomer_remaining%` | Remaining newcomer duration |

### Dynamic badge placeholders

Replace `<id>` with a badge ID.

| Placeholder | Output |
| --- | --- |
| `%cloverbadges_has_<id>%` | Whether the player owns the badge |
| `%cloverbadges_expires_<id>%` | Remaining duration for the badge |
| `%cloverbadges_priority_<id>%` | Badge priority |
| `%cloverbadges_name_<id>%` | Badge display name |
| `%cloverbadges_text_<id>%` | Badge text |
| `%cloverbadges_hover_<id>%` | Badge hover lines joined with newlines |

Examples:

```text
%cloverbadges_has_newcomer%
%cloverbadges_expires_newcomer%
%cloverbadges_text_newcomer%
```

### Dynamic message-color placeholders

Replace `<id>` with a message color ID.

```text
%cloverbadges_has_message_color_<id>%
%cloverbadges_message_color_expires_<id>%
```

Examples:

```text
%cloverbadges_has_message_color_cotton_candy%
%cloverbadges_message_color_expires_cotton_candy%
```

---

## Java API

CloverBadges registers `BadgeApi` in Bukkit's `ServicesManager`.

Other plugins can retrieve it without depending on CloverBadges' main class instance:

```java
import com.slyph.cloverbadges.api.BadgeApi;
import org.bukkit.Bukkit;

BadgeApi badgeApi = Bukkit.getServicesManager().load(BadgeApi.class);

if (badgeApi != null) {
    var activeBadges = badgeApi.getActiveBadgeIds(player);
    var component = badgeApi.getActiveBadgeComponent(player);
    boolean newcomer = badgeApi.isNewcomer(player);
}
```

Currently exposed API methods include:

```text
getActiveBadgeIds(...)
getActiveBadgeId(...)
getActiveBadgeComponent(...)
getActiveBadgeLegacy(...)
hasBadge(...)
getOwnedBadgeIds(...)
getRemainingMillis(...)
isNewcomer(...)
getNewcomerRemainingMillis(...)
```

If your plugin depends on the API at runtime, add CloverBadges as a dependency or soft dependency in your own `plugin.yml` as appropriate.

---

## Data storage

CloverBadges uses YAML files for persistent player state.

Runtime data files include:

| File | Stored data |
| --- | --- |
| `players.yml` | First-seen timestamps, badge grants and selected badges |
| `nickname-colors-data.yml` | Owned/selected nickname colors and starter initialization state |
| `message-colors-data.yml` | Owned/selected message colors |

The plugin uses asynchronous YAML writers for persistence and saves state on changes when `storage.save-on-change` is enabled.

Expired grants are periodically cleaned according to `storage.cleanup-interval-ticks`.

Avoid manually editing runtime data files while the server is running.

---

## Project structure

```text
CloverBadges/
├── .github/
│   └── workflows/
│       └── build.yml
├── build.gradle
├── gradle.properties
├── settings.gradle
└── src/
    └── main/
        ├── java/com/slyph/cloverbadges/
        │   ├── api/
        │   ├── badge/
        │   ├── command/
        │   ├── config/
        │   ├── gui/
        │   │   └── action/
        │   ├── head/
        │   ├── listener/
        │   ├── message/
        │   ├── messagecolor/
        │   │   ├── render/
        │   │   └── storage/
        │   ├── nametag/
        │   │   ├── integration/
        │   │   └── render/
        │   ├── nicknamecolor/
        │   │   ├── preview/
        │   │   ├── render/
        │   │   └── storage/
        │   ├── placeholder/
        │   ├── player/
        │   ├── storage/
        │   └── util/
        └── resources/
            ├── badges.yml
            ├── config.yml
            ├── gui.yml
            ├── message-colors.yml
            ├── messages.yml
            ├── nickname-colors.yml
            └── plugin.yml
```

Main class:

```text
com.slyph.cloverbadges.CloverBadges
```

Author:

```text
slyph
```

---

## Building from source

CloverBadges uses Gradle.

### Requirements

- JDK 25
- Gradle 9.x
- Git

Clone the repository:

```bash
git clone https://github.com/slyphmp4/CloverBadges.git
cd CloverBadges
```

Build:

```bash
gradle clean build
```

The compiled JAR will be created in:

```text
build/libs/CloverBadges-<version>.jar
```

Current build dependencies are compile-only:

```gradle
compileOnly 'io.papermc.paper:paper-api:26.2.build.110-stable'
compileOnly 'me.clip:placeholderapi:2.12.3'
compileOnly 'com.github.NEZNAMY:TAB-API:6.1.2'
```

GitHub Actions automatically builds the project on pushes and pull requests to `main`.

Release commits whose message begins with:

```text
Release CloverBadges v
```

trigger the release steps in the current workflow, including JAR creation, SHA-256 generation and GitHub Release publishing.

---

## Troubleshooting

### `/badge` does not open the GUI

Check that the player has both:

```text
cloverbadges.use
cloverbadges.menu
```

### Admin subcommands are missing from tab completion

This is expected when the sender does not have the corresponding admin permission.

### PlaceholderAPI placeholders return nothing

Make sure PlaceholderAPI is installed and enabled before CloverBadges registers its expansion. Then verify the placeholder identifier is `cloverbadges`.

### Gradient nickname is not visible in the nametag

Check:

- `nametag.enabled`;
- whether the player actually has a selected gradient nickname color;
- `nametag.only-for-gradients`;
- TAB availability when `hide-vanilla-with-tab` is enabled;
- the configured renderer and ArmorStand fallback settings.

### Message color is selected but chat text is unchanged

CloverBadges stores and exposes the selected message style, but it does not currently rewrite chat events directly. Your chat system needs to consume the CloverBadges message-color placeholders or otherwise integrate with the selected style.

### Changes are not visible after editing YAML files

Run:

```text
/badge reload
```

For dependency changes or server-level integration changes, restart the server.

---

## License

This repository currently does not include an open-source license file. Public source availability alone does not automatically grant permission to redistribute or reuse the project under an open-source license.

---

<div align="center">

Developed by **slyph** · `com.slyph.cloverbadges`

</div>
