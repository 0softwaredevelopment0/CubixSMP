package com.cubixsmp.listeners;

import com.cubixsmp.CubixSMP;
import com.cubixsmp.MessagesManager;
import com.cubixsmp.WorldSettings;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public class HuntingListener implements Listener {

    private final CubixSMP plugin;

    public HuntingListener(CubixSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!plugin.getConfig().getBoolean("hunting.enabled", true)) return;

        Player player = event.getEntity().getKiller();
        if (player == null) return;

        if (!plugin.getNaturalCheck().isNaturalMob(event.getEntity())) return;

        // 🌍 Пер-мировая настройка: в этом мире Cubix-XP не начисляется
        if (WorldSettings.isXpDisabled(plugin, player.getWorld())) return;

        EntityType type = event.getEntityType();
        double xp = plugin.getConfig().getDouble("hunting.mobs." + type.name(), 0);
        if (xp <= 0) return;

        plugin.getPlayerDataManager().addXp(player.getUniqueId(), xp, player);
        plugin.setLastAction(player.getUniqueId(), "Hunting");
        String msg = MessagesManager.replace(
                MessagesManager.getString("xp.hunting", "§7⚔ §a+{amount} XP §7({action})"),
                "amount", formatXp(xp), "action", getMobName(type));
        if (plugin.getConfig().getBoolean("settings.use-actionbar", true)) {
            player.sendActionBar(net.kyori.adventure.text.Component.text(msg));
        } else {
            player.sendMessage(msg);
        }
    }

    private String getMobName(EntityType type) {
        String name = type.name().toLowerCase().replace('_', ' ');
        String[] parts = name.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private String formatXp(double xp) {
        if (xp == (long) xp) return String.valueOf((long) xp);
        return String.format("%.1f", xp);
    }
}
