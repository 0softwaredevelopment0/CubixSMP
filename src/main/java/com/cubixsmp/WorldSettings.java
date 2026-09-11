package com.cubixsmp;

import org.bukkit.World;

/**
 * 🌍 Пер-мировые настройки CubixSMP (секция {@code worlds} в config.yml).
 *
 * Для каждого мира (ключ — имя мира, {@link World#getName()}) можно задать:
 * <ul>
 *   <li>{@code xp-disabled: true} — НЕ начислять Cubix-XP за активность в мире
 *       (добыча, фермерство, рубка, рыбалка, охота, дистанция, онлайн).
 *       Ежедневный бонус при входе и админ-команды addxp/removexp работают как обычно.</li>
 *   <li>{@code no-trampling: true} — запретить вытаптывание грядок в мире;
 *       {@code false} — разрешить (переопределяет {@code farming.no-trampling});
 *       ключ не задан — берётся глобальная настройка.</li>
 *   <li>{@code chat-disabled: true} — чат CubixSMP отключён в мире
 *       (сообщения игроков этого мира не отправляются).
 *       Работает, когда чатом управляет CubixSMP ({@code chat-format.enabled: true}).</li>
 *   <li>{@code chat-format: "..."} — свой формат чата для сообщений ИЗ этого мира
 *       (переопределяет {@code chat-format.format}; синтаксис тот же:
 *       MiniMessage, {player}, {message}, %плейсхолдеры% PlaceholderAPI).</li>
 * </ul>
 * Миры, которых нет в секции, работают по глобальным настройкам.
 */
public final class WorldSettings {

    private WorldSettings() {
    }

    private static String base(String worldName) {
        return "worlds." + worldName + ".";
    }

    /** true — Cubix-XP за активность в этом мире не начисляется. */
    public static boolean isXpDisabled(CubixSMP plugin, World world) {
        if (world == null) return false;
        return plugin.getConfig().getBoolean(base(world.getName()) + "xp-disabled", false);
    }

    /**
     * true — вытаптывание грядок в этом мире запрещено.
     * Пер-мировой ключ {@code worlds.<мир>.no-trampling} переопределяет
     * глобальный {@code farming.no-trampling}.
     */
    public static boolean isTramplingBlocked(CubixSMP plugin, World world) {
        if (world == null) return plugin.getConfig().getBoolean("farming.no-trampling", true);
        String path = base(world.getName()) + "no-trampling";
        if (plugin.getConfig().contains(path)) {
            return plugin.getConfig().getBoolean(path);
        }
        return plugin.getConfig().getBoolean("farming.no-trampling", true);
    }

    /** true — чат CubixSMP в этом мире отключён (сообщения игроков не отправляются). */
    public static boolean isChatDisabled(CubixSMP plugin, World world) {
        if (world == null) return false;
        return plugin.getConfig().getBoolean(base(world.getName()) + "chat-disabled", false);
    }

    /**
     * Свой формат чата для этого мира или {@code null}, если не задан
     * (тогда используется глобальный {@code chat-format.format}).
     */
    public static String chatFormat(CubixSMP plugin, World world) {
        if (world == null) return null;
        return plugin.getConfig().getString(base(world.getName()) + "chat-format", null);
    }
}
