package com.cubixsmp;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CubixSMPPlaceholderExpansion extends PlaceholderExpansion {

    private final CubixSMP plugin;
    private static final Map<UUID, String> lastActions = new ConcurrentHashMap<>();

    /** Очищает все данные (вызывается из onDisable). */
    public static void clearAll() {
        lastActions.clear();
    }

    /** Очищает данные конкретного игрока при выходе. */
    public static void clearPlayer(UUID uuid) {
        lastActions.remove(uuid);
    }

    public CubixSMPPlaceholderExpansion(CubixSMP plugin) {
        this.plugin = plugin;
    }

    /**
     * Сохраняет последнее действие игрока для плейсхолдера %cubixsmp_action%.
     */
    public static void setLastAction(UUID uuid, String action) {
        lastActions.put(uuid, action);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "cubixsmp";
    }

    @Override
    public @NotNull String getAuthor() {
        return "rizer001";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "0";

        return switch (params.toLowerCase()) {
            // %cubixsmp_player% — имя игрока
            case "player" -> player.getName();

            // %cubixsmp_level% — текущий уровень
            case "level" -> String.valueOf(plugin.getLevelManager().getLevel(player.getUniqueId()));

            // %cubixsmp_xp% — текущий опыт
            case "xp" -> formatXp(plugin.getLevelManager().getXp(player.getUniqueId()));

            // %cubixsmp_amount% — сколько XP нужно до следующего уровня
            case "amount" -> formatXp(plugin.getLevelManager().getXpForNextLevel(
                    plugin.getLevelManager().getLevel(player.getUniqueId())));

            // %cubixsmp_action% — последнее действие игрока (Mining, Farming, Hunting, Fishing, Woodcutting, Daily, Distance, Playtime)
            case "action" -> lastActions.getOrDefault(player.getUniqueId(), "—");

            // Дополнительные (от прошлой версии)
            case "level_xp_needed" -> formatXp(plugin.getLevelManager().getXpForNextLevel(
                    plugin.getLevelManager().getLevel(player.getUniqueId())));
            case "level_progress" -> {
                double xp = plugin.getLevelManager().getXp(player.getUniqueId());
                double needed = plugin.getLevelManager().getXpForNextLevel(
                        plugin.getLevelManager().getLevel(player.getUniqueId()));
                if (needed <= 0) yield "100%";
                int percent = (int) ((xp / needed) * 100);
                yield Math.min(percent, 100) + "%";
            }
            case "level_playtime" -> {
                int seconds = plugin.getPlayerDataManager().getPlaytimeSeconds(player.getUniqueId());
                yield formatPlaytime(seconds);
            }
            default -> null;
        };
    }

    private String formatXp(double xp) {
        if (xp == (long) xp) {
            return String.valueOf((long) xp);
        }
        return String.format("%.1f", xp);
    }

    private String formatPlaytime(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        if (hours > 0) {
            return hours + "ч " + minutes + "мин";
        }
        return minutes + "мин";
    }
}
