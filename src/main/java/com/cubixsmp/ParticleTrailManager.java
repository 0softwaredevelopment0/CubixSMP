package com.cubixsmp;

import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Менеджер партикл-трейлов вокруг игроков.
 * <p>
 * Игрок включает трейл командой {@code /csmp particle <имя>} (или {@code off}),
 * выбор сохраняется в playerdata (см. {@link PlayerDataManager}). Повторяющаяся
 * задача спавнит частицы вокруг игроков, у которых трейл включён.
 * <p>
 * Настройки: секция {@code particles} в config.yml — интервал спавна, список
 * разрешённых частиц ({@code allowed}) и индивидуальные настройки каждой
 * ({@code per-particle}: count/offset/extra).
 */
public class ParticleTrailManager {

    /** Настройки спавна одной частицы. */
    static final class TrailSettings {
        final int count;
        final double offsetX, offsetY, offsetZ, extra;

        TrailSettings(int count, double offsetX, double offsetY, double offsetZ, double extra) {
            this.count = count;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.extra = extra;
        }
    }

    private final CubixSMP plugin;
    private BukkitTask task;
    /** Разобранные per-particle настройки из config.yml (обновляются через {@link #reload()}). */
    private final Map<String, TrailSettings> perParticle = new HashMap<>();
    /** Кэш имён частиц → Particle (чтобы не резолвить каждый тик). */
    private final Map<String, Particle> particleCache = new HashMap<>();

    public ParticleTrailManager(CubixSMP plugin) {
        this.plugin = plugin;
    }

    /**
     * Перечитывает настройки из конфига и (пере)запускает задачу спавна.
     * Вызывается из onEnable и /csmp reload.
     */
    public void reload() {
        perParticle.clear();
        int defaultCount = plugin.getConfig().getInt("particles.count", 1);
        double defaultOx = plugin.getConfig().getDouble("particles.offset-x", 0.4);
        double defaultOy = plugin.getConfig().getDouble("particles.offset-y", 0.1);
        double defaultOz = plugin.getConfig().getDouble("particles.offset-z", 0.4);
        double defaultExtra = plugin.getConfig().getDouble("particles.extra", 0);

        for (Map<?, ?> entry : plugin.getConfig().getMapList("particles.per-particle")) {
            Object nameObj = entry.get("name");
            if (nameObj == null) continue;
            String name = normalize(String.valueOf(nameObj));
            if (name.isEmpty()) continue;
            perParticle.put(name, new TrailSettings(
                    Math.max(1, getInt(entry, "count", defaultCount)),
                    getDouble(entry, "offset-x", defaultOx),
                    getDouble(entry, "offset-y", defaultOy),
                    getDouble(entry, "offset-z", defaultOz),
                    getDouble(entry, "extra", defaultExtra)
            ));
        }

        // Перезапускаем задачу с актуальным интервалом и состоянием enabled
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (plugin.getConfig().getBoolean("particles.enabled", true)) {
            long interval = Math.max(1, plugin.getConfig().getLong("particles.interval-ticks", 4));
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, interval);
        }
    }

    public void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("particles.enabled", true)) return;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            spawnFor(player);
        }
    }

    private void spawnFor(Player player) {
        String name = plugin.getPlayerDataManager().getParticle(player.getUniqueId());
        if (name == null || name.isEmpty()) return;

        Particle particle = resolveParticle(name);
        if (particle == null) {
            // Частица больше не существует (удалена из конфига/API) — выключаем трейл
            plugin.getPlayerDataManager().setParticle(player.getUniqueId(), "");
            return;
        }

        TrailSettings s = perParticle.get(name);
        if (s == null) {
            s = new TrailSettings(
                    Math.max(1, plugin.getConfig().getInt("particles.count", 1)),
                    plugin.getConfig().getDouble("particles.offset-x", 0.4),
                    plugin.getConfig().getDouble("particles.offset-y", 0.1),
                    plugin.getConfig().getDouble("particles.offset-z", 0.4),
                    plugin.getConfig().getDouble("particles.extra", 0));
        }

        try {
            player.spawnParticle(particle, player.getLocation().add(0, 0.1, 0),
                    s.count, s.offsetX, s.offsetY, s.offsetZ, s.extra);
        } catch (IllegalArgumentException e) {
            // Частица требует данные (dust, block, item и т.п.) — трейлом быть не может
            plugin.getPlayerDataManager().setParticle(player.getUniqueId(), "");
            player.sendMessage(MessagesManager.getString("particle.error_requires_data",
                    "§c❌ Эта частица требует специальные данные и не может быть трейлом!"));
        }
    }

    // ─── Разрешение имён частиц ─────────────────────────────────

    /** Нормализует имя: нижний регистр, без префикса {@code minecraft:}. */
    public static String normalize(String name) {
        String n = name.trim().toLowerCase(Locale.ROOT);
        return n.startsWith("minecraft:") ? n.substring("minecraft:".length()) : n;
    }

    /**
     * Ищет частицу по имени: Bukkit-имя ({@code NOTE}, {@code note}), короткое
     * ({@code note}) или полный ключ ({@code minecraft:note}). Возвращает
     * {@code null}, если частица не найдена.
     */
    public static Particle resolve(String name) {
        String raw = name.trim().toLowerCase(Locale.ROOT);
        String n = raw.startsWith("minecraft:") ? raw.substring("minecraft:".length()) : raw;
        if (n.isEmpty()) return null;

        // 1) Bukkit-имя (без учёта регистра): NOTE / note / Rain
        try {
            return Particle.valueOf(n.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            // не Bukkit-имя — пробуем реестр
        }

        try {
            // 2) короткий ключ: note
            Particle p = Registry.PARTICLE_TYPE.get(NamespacedKey.minecraft(n));
            if (p != null) return p;
        } catch (IllegalArgumentException ignored) {
            // имя содержит ':' — пробуем полный ключ
        }

        try {
            // 3) полный ключ: minecraft:note, mod:something
            return Registry.PARTICLE_TYPE.get(NamespacedKey.fromString(raw));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Particle resolveParticle(String name) {
        Particle cached = particleCache.get(name);
        if (cached != null) return cached;
        Particle p = resolve(name);
        if (p != null) particleCache.put(name, p);
        return p;
    }

    /** Имена всех частиц, не требующих данных (пригодны для трейла), в нижнем регистре. */
    public static List<String> simpleParticleNames() {
        List<String> result = new ArrayList<>();
        for (Particle p : Particle.values()) {
            if (p.getDataType() == Void.class) {
                result.add(p.name().toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }

    private static int getInt(Map<?, ?> map, String key, int def) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.intValue();
        try {
            return v == null ? def : Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static double getDouble(Map<?, ?> map, String key, double def) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.doubleValue();
        try {
            return v == null ? def : Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
