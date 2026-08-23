package com.cubixsmp;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LevelManager {

    private final CubixSMP plugin;
    private final Map<UUID, Double> xpCache = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> levelCache = new ConcurrentHashMap<>();

    // XP needed per level formula: base + (level * multiplier)
    private double xpBase;
    private double xpMultiplier;
    private int minLevel;
    private int maxLevel;
    private double minLevelXp;

    public LevelManager(CubixSMP plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        xpBase = plugin.getConfig().getDouble("settings.xp-base", 100);
        xpMultiplier = plugin.getConfig().getDouble("settings.xp-multiplier", 1.5);
        minLevel = plugin.getConfig().getInt("settings.min-level", 1);
        maxLevel = plugin.getConfig().getInt("settings.max-level", 100);
        minLevelXp = plugin.getConfig().getDouble("settings.min-level-xp", 100);
    }

    public double getXpForNextLevel(int currentLevel) {
        // Для первого левелапа (minLevel → minLevel+1) используем отдельную настройку
        if (currentLevel == minLevel) {
            return minLevelXp;
        }
        return xpBase + (currentLevel * xpMultiplier);
    }

    public int getLevel(UUID uuid) {
        return levelCache.getOrDefault(uuid, minLevel);
    }

    public double getXp(UUID uuid) {
        return xpCache.getOrDefault(uuid, 0.0);
    }

    public void setLevel(UUID uuid, int level) {
        levelCache.put(uuid, Math.max(minLevel, Math.min(level, maxLevel)));
    }

    public void setXp(UUID uuid, double xp) {
        xpCache.put(uuid, Math.max(0, xp));
    }

    /**
     * Add XP to a player. Returns true if the player leveled up.
     */
    public boolean addXp(UUID uuid, double amount) {
        double currentXp = getXp(uuid);
        int currentLevel = getLevel(uuid);

        if (currentLevel >= maxLevel) return false;

        currentXp += amount;
        boolean leveledUp = false;

        while (currentXp >= getXpForNextLevel(currentLevel) && currentLevel < maxLevel) {
            currentXp -= getXpForNextLevel(currentLevel);
            currentLevel++;
            leveledUp = true;
        }

        setXp(uuid, currentXp);
        setLevel(uuid, currentLevel);

        return leveledUp;
    }

    public int getMinLevel() {
        return minLevel;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public double getTotalXpForLevel(int level) {
        double total = 0;
        for (int i = 0; i < level; i++) {
            total += xpBase + (i * xpMultiplier);
        }
        return total;
    }
}
