package com.cubixsmp;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

public class PlayerDataManager {

    private final CubixSMP plugin;
    private final Map<UUID, PlayerData> dataMap = new ConcurrentHashMap<>();

    private static class PlayerData {
        int level;
        double xp;
        int totalPlaytimeSeconds;
        long lastDailyBonusDay;
        boolean soundEnabled;
        int uid;
        /** Ник игрока (для файла списка UID). */
        String name = "";
        /** Выбранный партикл-трейл ("" = выключен). */
        String particle = "";

        PlayerData(CubixSMP plugin) {
            this.level = plugin.getLevelManager().getMinLevel();
            this.xp = 0;
            this.totalPlaytimeSeconds = 0;
            this.lastDailyBonusDay = 0;
            this.soundEnabled = true;
            this.uid = 0;
        }

        PlayerData(int level, double xp, int totalPlaytimeSeconds, long lastDailyBonusDay, boolean soundEnabled, int uid) {
            this.level = level;
            this.xp = xp;
            this.totalPlaytimeSeconds = totalPlaytimeSeconds;
            this.lastDailyBonusDay = lastDailyBonusDay;
            this.soundEnabled = soundEnabled;
            this.uid = uid;
        }
    }

    /** Следующий свободный номер аккаунта (UID). */
    private int nextUid;

    public PlayerDataManager(CubixSMP plugin) {
        this.plugin = plugin;
        loadUidCounter();
        plugin.getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
                UUID uuid = e.getPlayer().getUniqueId();
                load(uuid);
                PlayerData d = dataMap.get(uuid);
                if (d != null && (d.name == null || d.name.isEmpty())) {
                    d.name = e.getPlayer().getName();
                    save(uuid);
                    // Переписываем файл списка UID ТОЛЬКО когда появилось новое имя
                    // (при каждом входе это дорого — полный скан папки playerdata)
                    writeUidListFile();
                }
            }
            @org.bukkit.event.EventHandler
            public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
                save(e.getPlayer().getUniqueId());
                dataMap.remove(e.getPlayer().getUniqueId());
                // Очищаем кэш последнего действия (placeholder)
                CubixSMPPlaceholderExpansion.clearPlayer(e.getPlayer().getUniqueId());
            }
        }, plugin);

        // ⏱ Автосохранение каждые 5 минут — чтобы данные не терялись при краше/рестарте
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::saveAll, 6000L, 6000L);
    }

    public synchronized void loadAll() {
        File folder = plugin.getPlayerDataFolder();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        // 🛡 Восстановление счётчика ДО выдачи новых номеров: если uid-counter.yml
        // потерян/удалён, продолжаем с максимального уже выданного UID (по файлам),
        // чтобы не выдавать дубликаты даже при наличии старых файлов без UID.
        int maxUid = 0;
        for (File f : files) {
            String name = f.getName().replace(".yml", "");
            try {
                UUID.fromString(name); // пропускаем файлы с не-UUID именами
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            YamlConfiguration config = YamlConfiguration.loadConfiguration(f);
            maxUid = Math.max(maxUid, config.getInt("uid", 0));
        }
        if (nextUid <= maxUid) {
            nextUid = maxUid + 1;
            saveUidCounter();
        }

        for (File f : files) {
            String name = f.getName().replace(".yml", "");
            try {
                UUID uuid = UUID.fromString(name);
                load(uuid);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public synchronized void load(UUID uuid) {
        File file = getFile(uuid);
        YamlConfiguration config = file.exists() ? YamlConfiguration.loadConfiguration(file) : null;
        PlayerData data;
        if (config == null) {
            data = new PlayerData(plugin);
        } else {
            data = new PlayerData(
                    config.getInt("level", plugin.getLevelManager().getMinLevel()),
                    config.getDouble("xp", 0),
                    config.getInt("playtime", 0),
                    config.getLong("daily-bonus-day", 0),
                    config.getBoolean("sound-enabled", true),
                    config.getInt("uid", 0)
            );
            data.name = config.getString("name", "");
            data.particle = config.getString("particle", "");
        }
        dataMap.put(uuid, data);
        ensureUid(uuid, data);
        syncToManagers(uuid);
    }

    public synchronized void save(UUID uuid) {
        PlayerData data = dataMap.get(uuid);
        if (data == null) return;
        File file = getFile(uuid);
        YamlConfiguration config = new YamlConfiguration();
        config.set("level", data.level);
        config.set("xp", data.xp);
        config.set("playtime", data.totalPlaytimeSeconds);
        config.set("daily-bonus-day", data.lastDailyBonusDay);
        config.set("sound-enabled", data.soundEnabled);
        config.set("uid", data.uid);
        if (data.name != null && !data.name.isEmpty()) {
            config.set("name", data.name);
        }
        if (data.particle != null && !data.particle.isEmpty()) {
            config.set("particle", data.particle);
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning(MessagesManager.format("errors.data_save", "§c⚠ Error saving player data: {error}",
                    "error", e.getMessage()));
        }
    }

    public synchronized void saveAll() {
        for (UUID uuid : dataMap.keySet()) {
            save(uuid);
        }
    }

    // ─── UID: номер аккаунта игрока ────────────────────────────────────

    /**
     * Присваивает игроку порядковый номер аккаунта (UID), если его ещё нет.
     * Первый игрок получит номер из конфига {@code uid.starting-number} (по умолчанию 1),
     * следующий — на единицу больше и т.д. Номер сохраняется сразу, чтобы не потеряться.
     */
    private void ensureUid(UUID uuid, PlayerData data) {
        if (!plugin.getConfig().getBoolean("uid.enabled", true)) return;
        if (data.uid != 0) return;
        data.uid = nextUid++;
        saveUidCounter();
        save(uuid);
    }

    /** Возвращает номер аккаунта игрока (0, если ещё не присвоен). */
    public int getUid(UUID uuid) {
        PlayerData data = dataMap.get(uuid);
        return data == null ? 0 : data.uid;
    }

    /** Загружает счётчик UID из файла (или стартовый номер из конфига). */
    private void loadUidCounter() {
        File file = getUidCounterFile();
        if (file.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            nextUid = config.getInt("next-uid", plugin.getConfig().getInt("uid.starting-number", 1));
        } else {
            nextUid = plugin.getConfig().getInt("uid.starting-number", 1);
        }
    }

    /** Сохраняет счётчик UID. */
    private void saveUidCounter() {
        File file = getUidCounterFile();
        YamlConfiguration config = new YamlConfiguration();
        config.set("next-uid", nextUid);
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning(MessagesManager.format("errors.data_save", "§c⚠ Error saving UID counter: {error}",
                    "error", e.getMessage()));
        }
    }

    private File getUidCounterFile() {
        return new File(plugin.getDataFolder(), "uid-counter.yml");
    }

    /**
     * Пишет в файл (по умолчанию {@code players.txt} в папке плагина) список всех
     * игроков с UID в формате:
     * <pre>
     * 1. Игрок
     * 2. ДругойИгрок
     * </pre>
     * Вызывается при старте сервера и при входе игрока, чтобы файл всегда был актуален.
     */
    public synchronized void writeUidListFile() {
        if (!plugin.getConfig().getBoolean("uid.list-enabled", true)) return;

        String fileName = plugin.getConfig().getString("uid.list-file", "players.txt");
        File target = new File(plugin.getDataFolder(), fileName);

        java.util.List<UidEntry> entries = new java.util.ArrayList<>();
        File folder = plugin.getPlayerDataFolder();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File f : files) {
                String name = f.getName().replace(".yml", "");
                final UUID uuid;
                try {
                    uuid = UUID.fromString(name);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }

                // Для онлайн-игроков берём свежие данные из памяти, для офлайн — из файла
                int uid;
                String playerName;
                PlayerData live = dataMap.get(uuid);
                if (live != null) {
                    uid = live.uid;
                    playerName = live.name;
                } else {
                    YamlConfiguration config = YamlConfiguration.loadConfiguration(f);
                    uid = config.getInt("uid", 0);
                    playerName = config.getString("name", "");
                }

                if (uid <= 0) continue;
                if (playerName == null || playerName.isEmpty()) {
                    org.bukkit.OfflinePlayer offline = plugin.getServer().getOfflinePlayer(uuid);
                    playerName = offline.getName();
                    if (playerName == null) playerName = uuid.toString().substring(0, 8);
                }
                entries.add(new UidEntry(uid, playerName));
            }
        }

        entries.sort(java.util.Comparator.comparingInt(e -> e.uid));

        StringBuilder sb = new StringBuilder();
        for (UidEntry entry : entries) {
            sb.append(entry.uid).append(". ").append(entry.name).append("\n");
        }

        try {
            java.nio.file.Files.write(target.toPath(), sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            plugin.getLogger().info("UID list written: " + target.getName() + " (" + entries.size() + " players)");
        } catch (IOException e) {
            plugin.getLogger().warning(MessagesManager.format("errors.data_save",
                    "§c⚠ Error writing UID list: {error}", "error", e.getMessage()));
        }
    }

    /** Вспомогательный класс для сортировки списка UID */
    private static class UidEntry {
        final int uid;
        final String name;
        UidEntry(int uid, String name) {
            this.uid = uid;
            this.name = name;
        }
    }

    public int getLevel(UUID uuid) {
        PlayerData data = dataMap.get(uuid);
        return data == null ? plugin.getLevelManager().getMinLevel() : data.level;
    }

    public double getXp(UUID uuid) {
        PlayerData data = dataMap.get(uuid);
        return data == null ? 0 : data.xp;
    }

    public void setLevel(UUID uuid, int level) {
        PlayerData data = dataMap.get(uuid);
        if (data == null) {
            load(uuid);
            data = dataMap.get(uuid);
            if (data == null) return;
        }
        data.level = level;
        plugin.getLevelManager().setLevel(uuid, level);
    }

    public void setXp(UUID uuid, double xp) {
        PlayerData data = dataMap.get(uuid);
        if (data == null) {
            load(uuid);
            data = dataMap.get(uuid);
            if (data == null) return;
        }
        data.xp = xp;
        plugin.getLevelManager().setXp(uuid, xp);
    }

    public void addXp(UUID uuid, double amount, Player player) {
        PlayerData data = dataMap.get(uuid);
        if (data == null) {
            load(uuid);
            data = dataMap.get(uuid);
            if (data == null) return;
        }
        data.xp += amount;
        plugin.getLevelManager().setXp(uuid, data.xp);

        // 🔔 Звук получения опыта (с возможностью отключения)
        if (amount > 0 && data.soundEnabled && player.isOnline()) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
        }

        int levelsGained = 0;
        while (data.xp >= plugin.getLevelManager().getXpForNextLevel(data.level)
                && data.level < plugin.getLevelManager().getMaxLevel()) {
            data.xp -= plugin.getLevelManager().getXpForNextLevel(data.level);
            data.level++;
            levelsGained++;
        }

        if (levelsGained > 0) {
            setLevel(uuid, data.level);
            setXp(uuid, data.xp);
            String msg = MessagesManager.format("xp.level_up", "§a✦ §eLevel Up! §7Now you are §e{level} §7level! §a✦",
                    "level", String.valueOf(data.level));
            // 🐛 Фикс: level-up ВСЕГДА в чат, не в actionbar (чтобы не перекрывался XP-сообщениями)
            player.sendMessage(msg);
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }
    }

    public int getPlaytimeSeconds(UUID uuid) {
        PlayerData data = dataMap.get(uuid);
        return data == null ? 0 : data.totalPlaytimeSeconds;
    }

    public void addPlaytime(UUID uuid, int seconds) {
        PlayerData data = dataMap.get(uuid);
        if (data == null) {
            load(uuid);
            data = dataMap.get(uuid);
            if (data == null) return;
        }
        data.totalPlaytimeSeconds += seconds;

        int interval = plugin.getConfig().getInt("settings.playtime-interval", 1800);
        int xpAmount = plugin.getConfig().getInt("settings.xp-per-playtime-interval", 10);

        if (data.totalPlaytimeSeconds % interval < seconds) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                addXp(uuid, xpAmount, player);
                plugin.setLastAction(uuid, "Playtime");
                String msg = MessagesManager.format("xp.playtime", "§7⏱ §a+{amount} XP §7за игру ({minutes} мин)",
                        "amount", String.valueOf(xpAmount), "minutes", String.valueOf(interval / 60));
                if (plugin.getConfig().getBoolean("settings.use-actionbar", true)) {
                    player.sendActionBar(net.kyori.adventure.text.Component.text(msg));
                } else {
                    player.sendMessage(msg);
                }
            }
        }
    }

    public boolean canClaimDailyBonus(UUID uuid) {
        PlayerData data = dataMap.get(uuid);
        if (data == null) return true; // игрок ещё не загружен — бонус доступен, без перезаписи данных
        long today = java.time.LocalDate.now().toEpochDay();
        return data.lastDailyBonusDay != today;
    }

    public void claimDailyBonus(UUID uuid, Player player) {
        PlayerData data = dataMap.get(uuid);
        if (data == null) {
            load(uuid); // подгружаем, если по какой-то причине нет в памяти
            data = dataMap.get(uuid);
            if (data == null) return; // аварийный выход
        }
        data.lastDailyBonusDay = java.time.LocalDate.now().toEpochDay();
        save(uuid); // 💾 сразу, чтобы при краше сервера бонус не выдался повторно
        int xp = plugin.getConfig().getInt("settings.daily-bonus-xp", 50);
        addXp(uuid, xp, player);
        plugin.setLastAction(uuid, "Daily");
        String msg = MessagesManager.format("xp.daily_bonus_claim", "§6☀ §eЕжедневный бонус: §a+{amount} XP",
                "amount", String.valueOf(xp));
        if (plugin.getConfig().getBoolean("settings.use-actionbar", true)) {
            player.sendActionBar(net.kyori.adventure.text.Component.text(msg));
        } else {
            player.sendMessage(msg);
        }
    }

    /**
     * Переключает звук XP для игрока. Возвращает новое состояние (true = звук включён).
     */
    public boolean toggleSound(UUID uuid) {
        PlayerData data = dataMap.get(uuid);
        if (data == null) {
            load(uuid);
            data = dataMap.get(uuid);
            if (data == null) return true;
        }
        data.soundEnabled = !data.soundEnabled;
        save(uuid);
        return data.soundEnabled;
    }

    /**
     * Проверяет, включён ли звук XP для игрока.
     */
    public boolean isSoundEnabled(UUID uuid) {
        PlayerData data = dataMap.get(uuid);
        return data == null || data.soundEnabled; // по умолчанию true
    }

    /**
     * Возвращает выбранный игроком партикл-трейл ("" = выключен).
     */
    public String getParticle(UUID uuid) {
        PlayerData data = dataMap.get(uuid);
        return data == null || data.particle == null ? "" : data.particle;
    }

    /**
     * Устанавливает партикл-трейл игрока ("" — выключить) и сразу сохраняет.
     */
    public void setParticle(UUID uuid, String particle) {
        PlayerData data = dataMap.get(uuid);
        if (data == null) {
            load(uuid);
            data = dataMap.get(uuid);
            if (data == null) return;
        }
        data.particle = particle == null ? "" : particle;
        save(uuid);
    }

    /**
     * Возвращает топ-N игроков по уровню (а если уровень равен — по XP).
     * Читает данные из файлов playerdata/ для офлайн-игроков,
     * а для онлайн-игроков берёт актуальные данные из dataMap.
     *
     * @param limit количество записей в топе
     * @return список массивов [name, level, xp]
     */
    public synchronized java.util.List<String[]> getTopPlayers(int limit) {
        java.util.List<String[]> result = new java.util.ArrayList<>();
        File folder = plugin.getPlayerDataFolder();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return result;

        java.util.List<PlayerSnapshot> snapshots = new java.util.ArrayList<>();

        for (File f : files) {
            String name = f.getName().replace(".yml", "");
            try {
                UUID uuid = UUID.fromString(name);
                // 🐛 Фикс: для онлайн-игроков берём данные из dataMap (они актуальнее файла)
                PlayerData liveData = dataMap.get(uuid);
                int level;
                double xp;
                if (liveData != null) {
                    level = liveData.level;
                    xp = liveData.xp;
                } else {
                    YamlConfiguration config = YamlConfiguration.loadConfiguration(f);
                    level = config.getInt("level", 0);
                    xp = config.getDouble("xp", 0);
                }
                if (level > 0 || xp > 0) {
                    snapshots.add(new PlayerSnapshot(uuid, level, xp));
                }
            } catch (IllegalArgumentException ignored) {}
        }

        // Сортируем: по убыванию level, затем по убыванию xp
        snapshots.sort((a, b) -> {
            if (a.level != b.level) return b.level - a.level;
            return Double.compare(b.xp, a.xp);
        });

        // Берём топ-N
        int count = Math.min(limit, snapshots.size());
        for (int i = 0; i < count; i++) {
            PlayerSnapshot ps = snapshots.get(i);
            org.bukkit.OfflinePlayer offline = plugin.getServer().getOfflinePlayer(ps.uuid);
            String displayName = offline.getName() != null ? offline.getName() : ps.uuid.toString().substring(0, 8);
            result.add(new String[]{displayName, String.valueOf(ps.level), formatXp(ps.xp)});
        }

        return result;
    }

    /** Вспомогательный статический класс для сортировки */
    private static class PlayerSnapshot {
        final UUID uuid;
        final int level;
        final double xp;
        PlayerSnapshot(UUID uuid, int level, double xp) {
            this.uuid = uuid;
            this.level = level;
            this.xp = xp;
        }
    }

    /** Пакетный утилитарный метод: красивое форматирование XP */
    static String formatXp(double xp) {
        if (xp == (long) xp) return String.valueOf((long) xp);
        return String.format("%.1f", xp);
    }

    public void syncToManagers(UUID uuid) {
        PlayerData data = dataMap.get(uuid);
        if (data == null) return;
        plugin.getLevelManager().setLevel(uuid, data.level);
        plugin.getLevelManager().setXp(uuid, data.xp);
    }

    private File getFile(UUID uuid) {
        return new File(plugin.getPlayerDataFolder(), uuid.toString() + ".yml");
    }

    /**
     * Удаляет файл данных игрока (используется админ-командой сброса).
     * Синхронизирован, чтобы не конфликтовать с фоновым автосохранением.
     */
    public synchronized void deleteDataFile(UUID uuid) {
        File dataFile = getFile(uuid);
        if (dataFile.exists()) dataFile.delete();
    }
}
