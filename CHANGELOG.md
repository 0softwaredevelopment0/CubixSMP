# Changes

CubixSMP ported to Minecraft/Paper **26.3** and the modern Paper plugin loader.
Channel: **Alpha** — the platform change has not been verified on a live server yet.

## Added

### Modern Paper plugin loader (`paper-plugin.yml`)
- The `plugin.yml` manifest is replaced by `paper-plugin.yml` with `api-version: '26.3'`.
- `softdepend` is replaced by `dependencies.server`: PlaceholderAPI and WorldGuard are declared as
  `load: BEFORE`, `required: false`, `join-classpath: true`, so the plugin still runs without them.
- The plugin version is injected from Gradle (`version: '${project.version}'`) instead of being
  hardcoded, so the manifest can no longer drift from `project.version`.

## Changed

### Platform and build
- `paperweight.userdev` `2.0.0-beta.21` → `2.0.0-beta.24`, dev bundle `26.2.build.+` → `26.3.build.+`.
- Gradle wrapper `9.6.1` → `9.8.2` (paperweight `2.0.0-beta.24` requires Gradle 9.7.1 or newer).
- Java 26 toolchain unchanged; compilation now uses explicit UTF-8 and `-Xlint:deprecation`.
- Project version is `2.0.0-alpha.1` (workspace scheme `X.X.X-channel.number`).

### Code — modern Paper API
- `Sound.valueOf(...)` → `Registry.SOUND_EVENT` lookup; legacy names from `ping-sound`
  (`BLOCK_NOTE_BLOCK_PLING`) still resolve, unknown values fall back to the default sound.
- `ItemMeta#setDisplayName` / `setLore` / `getLore` → Adventure `displayName()` / `lore()`.
- `ItemStack#getDurability` / `setDurability` → `Damageable#getDamage` / `setDamage`
  (the "Drill" custom enchantment).
- `InventoryClickEvent#setCursor` → `Player#setItemOnCursor` (special book crafting).
- `JavaPlugin#getDescription()` → `getPluginMeta()` (`/csmp` help header and the enable log line).

### UI
- Cancel button icon in the action dialog: `✖ Отмена` → `❌ Отмена` (dialog defaults and `config.yml`).

### Documentation
- README and `plugin-guide.txt` updated for Paper 26.3 and the new loader.

## Removed

- Stale `build/libs/CubixSMP-1.2.3.jar`; builds live only in `build/libs/`.
- Legacy branch and tag names unified to the workspace scheme: branches `<version>`, tags `v<version>`;
  same-commit duplicates (`v1.0`, `Patch-1.0.1/2/3`, `v1.2`, `Release-1.2`) were dropped.

## Known issues

- Chat still uses `AsyncPlayerChatEvent` (3 `-Xlint:deprecation` warnings); the move to
  `AsyncChatEvent` + `ChatRenderer` needs live-server verification.
- The action dialogs (`ActionMenu`) use NMS `net.minecraft.server.dialog.*` — the most fragile spot
  after a platform bump, verify it first.
- Verified by build only: `gradlew build` → `BUILD SUCCESSFUL`. Not run on a server yet.

**Requirements:** Paper 26.3 (or a fork: Purpur, Pufferfish), Java 26.
