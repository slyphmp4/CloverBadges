# Changelog

## 1.14.4

- Add Minecraft 26.3 support using the pinned Paper API `26.3.build.253-stable` and retain source compatibility with `26.2.build.110-stable`.
- Create inventory titles through the Adventure component API.
- Add the checksum-verified Gradle 9.1.0 wrapper and reproducible JAR ordering and timestamps.
- Remove the Gradle execution-time project access warning and track the expanded resource version as a task input.
- Add a separate disposable-server test plugin and runner covering startup, menu metadata, custom heads, renderers, permissions, optional integrations, reload, persistence and expiry after a restart.
- Run the compatibility checks in GitHub Actions and generate release notes from the actual release changes.
- Keep author `slyph`, the `com.slyph.cloverbadges` package, `api-version: '1.16'`, HEX support, editable GUI configuration and existing player-data formats.
