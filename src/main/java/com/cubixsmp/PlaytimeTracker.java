package com.cubixsmp;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Трекер ежедневного онлайна игроков.
 *
 * Фиксирует время входа/выхода, сохраняет посекундно по дням.
 * Логи хранятся N дней (настраивается), после чего автоматически очищаются.
 * Команда: /checkonline [ник] — показывает статистику за неделю.
 *
 * Настройки: playtime-tracker в config.yml
 * Данные: playtimelogs/&lt;uuid&gt;.yml
 */
public class PlaytimeTracker implements Listener {

    private final CubixSMP plugin;
    private final File dataFolder;

    /** UUID → дата(yyyy-MM-dd) → секунды */
    private final Map<UUID, Map<String, Long>> dailyLogs = new ConcurrentHashMap<>();
    /** UUID → время входа (ms) */
    private final Map<UUID, Long> sessionStart = new HashMap<>();

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String CFG = "playtime-tracker.";

    public PlaytimeTracker(CubixSMP plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "playtimelogs");
        if (!dataFolder.exists()) dataFolder.mkdirs();
    }

    // ═══════════════════════════════════════════════════
    // ИНИЦИАЛИЗАЦИЯ
    // ═══════════════════════════════════════════════════

    /** Загружает все логи + запускает таймеры */
    public void init() {
        if (!enabled()) return;

        loadAll();
        cleanupOld();

        // Автосохранение (из конфига, по умолчанию 5 мин)
        int saveMinutes = plugin.getConfig().getInt(CFG + "auto-save-interval", 5);
        long saveTicks = saveMinutes * 1200L;
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveAll, saveTicks, saveTicks);

        // Автовычистка устаревших записей (из конфига, по умолчанию 60 мин)
        int cleanupMinutes = plugin.getConfig().getInt(CFG + "auto-cleanup-interval", 60);
        long cleanupTicks = cleanupMinutes * 1200L;
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanupOld, cleanupTicks, cleanupTicks);

        // Тик каждые 60 секунд — добавляем 60 сек к онлайну всех онлайн-игроков
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1200L, 1200L);

        plugin.getLogger().info("[PlaytimeTracker] Initialized with " + dailyLogs.size() + " player log(s).");
    }

    /** Выгружает все данные на диск */
    public void shutdown() {
        if (!enabled()) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            flushSession(player.getUniqueId());
        }
        saveAll();
        plugin.getLogger().info("[PlaytimeTracker] Shutdown complete.");
    }

    // ═══════════════════════════════════════════════════
    // СОБЫТИЯ
    // ═══════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled()) return;
        sessionStart.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (!enabled()) return;
        flushSession(event.getPlayer().getUniqueId());
    }

    /** Фоновая задача — раз в 60 секунд добавляет время всем онлайн-игрокам */
    private void tick() {
        if (!enabled()) return;

        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            addTime(uuid, 60);
            // Сбрасываем время сессии, чтобы flushSession не задвоил эти 60 секунд
            sessionStart.put(uuid, now);
        }
    }

    // ═══════════════════════════════════════════════════
    // СЕССИЯ
    // ═══════════════════════════════════════════════════

    private void flushSession(UUID uuid) {
        Long joinTime = sessionStart.remove(uuid);
        if (joinTime == null) return;

        int elapsed = (int) ((System.currentTimeMillis() - joinTime) / 1000);
        if (elapsed > 0) {
            addTime(uuid, elapsed);
        }
    }

    // ═══════════════════════════════════════════════════
    // ДОБАВЛЕНИЕ ВРЕМЕНИ
    // ═══════════════════════════════════════════════════

    public void addTime(UUID uuid, int seconds) {
        String today = LocalDate.now().format(DATE_FMT);
        dailyLogs.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        dailyLogs.get(uuid).merge(today, (long) seconds, Long::sum);
    }

    // ═══════════════════════════════════════════════════
    // ЗАПРОСЫ
    // ═══════════════════════════════════════════════════

    public Map<String, Long> getLastDays(UUID uuid, int days) {
        Map<String, Long> result = new LinkedHashMap<>();
        Map<String, Long> playerLog = dailyLogs.get(uuid);
        if (playerLog == null) return result;

        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            String date = today.minusDays(i).format(DATE_FMT);
            result.put(date, playerLog.getOrDefault(date, 0L));
        }
        return result;
    }

    public long getWeeklyTotal(UUID uuid) {
        Map<String, Long> logs = dailyLogs.get(uuid);
        if (logs == null) return 0;

        LocalDate weekAgo = LocalDate.now().minusDays(7);
        long total = 0;
        for (Map.Entry<String, Long> entry : logs.entrySet()) {
            try {
                LocalDate date = LocalDate.parse(entry.getKey(), DATE_FMT);
                if (!date.isBefore(weekAgo)) {
                    total += entry.getValue();
                }
            } catch (Exception ignored) {}
        }
        return total;
    }

    // ═══════════════════════════════════════════════════
    // СОХРАНЕНИЕ / ЗАГРУЗКА
    // ═══════════════════════════════════════════════════

    private synchronized void loadAll() {
        File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            String name = file.getName().replace(".yml", "");
            try {
                UUID uuid = UUID.fromString(name);
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                Map<String, Long> playerLog = new ConcurrentHashMap<>();
                for (String key : config.getKeys(false)) {
                    playerLog.put(key, config.getLong(key, 0));
                }
                if (!playerLog.isEmpty()) {
                    dailyLogs.put(uuid, playerLog);
                }
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private synchronized void saveAll() {
        for (Map.Entry<UUID, Map<String, Long>> entry : dailyLogs.entrySet()) {
            UUID uuid = entry.getKey();
            Map<String, Long> log = entry.getValue();
            if (log.isEmpty()) continue;

            File file = new File(dataFolder, uuid.toString() + ".yml");
            YamlConfiguration config = new YamlConfiguration();
            for (Map.Entry<String, Long> logEntry : log.entrySet()) {
                config.set(logEntry.getKey(), logEntry.getValue());
            }
            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "[PlaytimeTracker] Failed to save " + uuid, e);
            }
        }
    }

    // ═══════════════════════════════════════════════════
    // АВТОВЫЧИСТКА
    // ═══════════════════════════════════════════════════

    private synchronized void cleanupOld() {
        int retention = plugin.getConfig().getInt(CFG + "log-retention-days", 7);
        LocalDate cutoff = LocalDate.now().minusDays(retention);
        int removed = 0;

        for (Map.Entry<UUID, Map<String, Long>> entry : dailyLogs.entrySet()) {
            UUID uuid = entry.getKey();
            Map<String, Long> log = entry.getValue();

            Iterator<Map.Entry<String, Long>> it = log.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Long> logEntry = it.next();
                try {
                    LocalDate date = LocalDate.parse(logEntry.getKey(), DATE_FMT);
                    if (date.isBefore(cutoff)) {
                        it.remove();
                        removed++;
                    }
                } catch (Exception ignored) {}
            }

            if (log.isEmpty()) {
                dailyLogs.remove(uuid);
                File file = new File(dataFolder, uuid.toString() + ".yml");
                if (file.exists()) file.delete();
            }
        }

        if (removed > 0) {
            plugin.getLogger().info("[PlaytimeTracker] Cleaned up " + removed + " old log entries.");
        }
    }

    // ═══════════════════════════════════════════════════
    // КОМАНДА /checkonline
    // ═══════════════════════════════════════════════════

    public boolean handleCheckOnline(org.bukkit.command.CommandSender sender, String[] args) {
        if (!sender.hasPermission("cubixsmp.checkonline")) {
            sender.sendMessage(MessagesManager.getString("general.no_permission",
                    "§c❌ У вас нет прав на это действие!"));
            return true;
        }

        UUID targetUuid;
        String targetName;

        if (args.length == 0) {
            // Своя статистика
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MessagesManager.getString("general.player_only",
                        "§c❌ Только игрок может использовать эту команду!"));
                return true;
            }
            targetUuid = player.getUniqueId();
            targetName = player.getName();
        } else {
            // Статистика по нику — требуется доп. право
            if (!sender.hasPermission("cubixsmp.checkonline.other")) {
                sender.sendMessage(MessagesManager.getString("general.no_permission",
                        "§c❌ У вас нет прав смотреть онлайн других игроков!"));
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[0]);
            if (target != null) {
                targetUuid = target.getUniqueId();
                targetName = target.getName();
            } else {
                sender.sendMessage(MessagesManager.format("general.player_not_found",
                        "§c❌ Игрок §e{player} §cне найден!",
                        "player", args[0]));
                return true;
            }
        }

        Map<String, Long> last7Days = getLastDays(targetUuid, 7);
        long weeklyTotal = getWeeklyTotal(targetUuid);

        sender.sendMessage(MessagesManager.getString("checkonline.header",
                "§6╔═══════════════════════════════╗"));
        sender.sendMessage(MessagesManager.format("checkonline.title",
                "§6║ §lOnline: §e{player}",
                "player", targetName));
        sender.sendMessage(MessagesManager.getString("checkonline.footer",
                "§6╚═══════════════════════════════╝"));

        for (Map.Entry<String, Long> entry : last7Days.entrySet()) {
            sender.sendMessage(MessagesManager.format("checkonline.entry",
                    "§e{date} §7=> §f{time}",
                    "date", entry.getKey(),
                    "time", formatDuration(entry.getValue())));
        }

        sender.sendMessage(MessagesManager.format("checkonline.weekly_total",
                "§6За неделю: §e{total}",
                "total", formatDuration(weeklyTotal)));

        return true;
    }

    // ═══════════════════════════════════════════════════
    // УТИЛИТЫ
    // ═══════════════════════════════════════════════════

    public static String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean(CFG + "enabled", true);
    }
}
