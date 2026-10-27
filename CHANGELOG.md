# Changes

Порт CubixSMP на Minecraft/Paper **26.3** и новый Paper-лоадер.
Канал — **Alpha**: изменение платформы на живом сервере ещё не проверялось.

## Added

### Новый Paper-лоадер (`paper-plugin.yml`)
- Манифест `plugin.yml` заменён на `paper-plugin.yml`, `api-version: '26.3'`.
- `softdepend` заменён на `dependencies.server`: PlaceholderAPI и WorldGuard объявлены как
  `load: BEFORE`, `required: false`, `join-classpath: true` — плагин работает и без них.
- Версия плагина подставляется из Gradle (`version: '${project.version}'`) вместо
  захардкоженного значения, поэтому манифест больше не расходится с `project.version`.

## Changed

### Платформа и сборка
- `paperweight.userdev` `2.0.0-beta.21` → `2.0.0-beta.24`, dev-bundle `26.2.build.+` → `26.3.build.+`.
- Gradle wrapper `9.6.1` → `9.8.2` (paperweight `2.0.0-beta.24` требует Gradle ≥ 9.7.1).
- Java 26 toolchain без изменений; компиляция явно в UTF-8 с `-Xlint:deprecation`.
- Версия проекта — `2.0.0-alpha.1` (схема воркспейса `X.X.X-channel.number`).

### Код — переход на современный Paper API
- `Sound.valueOf(...)` → поиск в `Registry.SOUND_EVENT`; старые имена из `ping-sound`
  (`BLOCK_NOTE_BLOCK_PLING`) по-прежнему резолвятся, нерезолвленное падает в дефолт.
- `ItemMeta#setDisplayName` / `setLore` / `getLore` → Adventure `displayName()` / `lore()`.
- `ItemStack#getDurability` / `setDurability` → `Damageable#getDamage` / `setDamage` (зачарование «Бур»).
- `InventoryClickEvent#setCursor` → `Player#setItemOnCursor` (крафт спец-книги).
- `JavaPlugin#getDescription()` → `getPluginMeta()` (команда `/csmp` и лог включения).

### Интерфейс
- Иконка кнопки отмены в диалоге действий: `✖ Отмена` → `❌ Отмена` (дефолты диалога и `config.yml`).

### Документация
- README и `plugin-guide.txt` обновлены на Paper 26.3 и новый лоадер.

## Removed

- Устаревший `build/libs/CubixSMP-1.2.3.jar` из репозитория — сборка только в `build/libs/`.
- Ветки и теги с легаси-именами приведены к схеме воркспейса: ветки `<версия>`, теги `v<версия>`;
  дубли одного коммита (`v1.0`, `Patch-1.0.1/2/3`, `v1.2`, `Release-1.2`) удалены.

## Known issues

- Чат всё ещё на `AsyncPlayerChatEvent` (3 предупреждения `-Xlint:deprecation`) — миграция на
  `AsyncChatEvent` + `ChatRenderer` требует проверки на живом сервере.
- Диалоги (`ActionMenu`) используют NMS `net.minecraft.server.dialog.*` — при смене версии
  платформы это самое хрупкое место, проверять в первую очередь.
- Проверено только сборкой: `gradlew build` → `BUILD SUCCESSFUL`. На сервере не запускалось.

**Требования:** Paper 26.3 (или форк: Purpur, Pufferfish), Java 26.
