# CloverBadges 1.14.4 validation

Date: 2026-11-18, Europe/Moscow.

Production JAR SHA-256: `4904772d81b28637cd93182a9b352a41e24006f480043f857657cd57368a5191`.

## Local runtime matrix

The same production JAR was copied into isolated test servers. Every successful run includes a full shutdown and second startup to verify persisted ownership, selected cosmetics and temporary-grant expiry.

| Runtime | Optional plugins | Result |
| --- | --- | --- |
| Paper 26.2 build 110 | None | Passed |
| Paper 26.3 build 253 | None | Passed |
| Paper 26.3 build 253 | PlaceholderAPI 2.12.3, TAB 6.2.1 | Passed |
| Fabric 26.3, Loader 0.20.1, Fabric API 0.166.2+26.3, Cardboard 26.3.0 | PlaceholderAPI 2.12.3, TAB 6.2.1 | Passed with the existing AUTO → ArmorStand nametag fallback |

The Cardboard release was downloaded from `slyphmp4/Cardboard` tag `v26.3.0`. Its SHA-256 is `93518ff08cf09afc1344bb78456e99f55566fd16230f93c40be8f5f2d6149231`.

## Checks

- Plugin startup, author `slyph`, retained `api-version: '1.16'`, Bukkit service registration and clean plugin shutdown.
- Permanent and temporary badge, nickname-color and message-color grants; selection; revocation; cleanup; reload; persistence after restart.
- Creation of all three GUI pages, configurable titles, item materials, quantities, Adventure names and lore, and item-byte serialization round trips.
- Custom-head texture inputs as a texture hash, texture URL and Base64 profile; profile texture survives item serialization.
- Direct TextDisplay attachment/sync and ArmorStand movement on Paper; AUTO renderer creation, fallback and movement on Cardboard; removal of temporary nametag entities.
- Permission-filtered command completion and rejection of unauthorized command execution.
- Adventure HEX formatting, nickname text preservation and reset boundaries.
- PlaceholderAPI badge, nickname and message-color placeholders and unknown-placeholder behavior when the full PlaceholderAPI release is installed.
- Loading without optional plugins and startup with full PlaceholderAPI and TAB releases.
- Production JAR CRC validation, required resources, Java 25 class version, and exclusion of test classes and third-party dependencies.
- Gradle wrapper checksum, default 26.3 build, source compilation against the previous 26.2 API, and configuration-cache compatibility.

## Scope and limits

GUI interactions use a synthetic Bukkit player. Entity-renderer checks use a server-side player without a real network client. TAB startup and API availability were exercised, while per-player TAB hiding, packet delivery, client visuals, vanish behavior and movement across dimensions still require an in-game check.

Cardboard rejected TextDisplay attachment to the test player. The production AUTO path caught that failure, removed the unsuccessful display and successfully used the existing ArmorStand fallback. This validates the fallback path; it does not establish that Cardboard's TextDisplay mounting works with a connected player.

Cardboard's item wrappers did not compare equal after byte serialization. The tests instead verify the item material, count, Adventure name, lore and head-texture URL that the plugin actually uses. Those observable values were preserved.

The isolated servers used localhost and offline mode. Mojang service-discovery and version-check requests produced network warnings in this environment. CloverBadges checks and shutdown completed successfully. No production server or existing player data was modified.

GitHub Actions covers the Paper matrix. The additional Fabric/Cardboard check was performed locally on a disposable server.
