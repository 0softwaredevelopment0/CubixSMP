package com.cubixsmp.listeners;

import com.cubixsmp.CubixSMP;
import com.cubixsmp.MessagesManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * 💀 После смерти игрока ему в чат автоматически отправляется настраиваемое
 * сообщение с координатами места смерти: «Вы умерли на координатах x y z
 * в мире world».
 *
 * <p>Сообщение получают только игроки с правом из конфига
 * (по умолчанию {@code cubixsmp.deathmessage}).</p>
 *
 * <p>Настройки: секция {@code death-message} в config.yml,
 * текст сообщения — {@code messages.death.death_coords}.</p>
 */
public class DeathMessageListener implements Listener {

    private final CubixSMP plugin;

    public DeathMessageListener(CubixSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!plugin.getConfig().getBoolean("death-message.enabled", true)) return;

        Player player = event.getEntity();
        String permission = plugin.getConfig().getString("death-message.permission", "cubixsmp.deathmessage");
        if (permission != null && !permission.isEmpty() && !player.hasPermission(permission)) return;

        Location loc = player.getLocation();
        String world = loc.getWorld() != null ? loc.getWorld().getName() : "?";

        String message = MessagesManager.format(
                "death.death_coords",
                "§cВы умерли на координатах §e{x} {y} {z} §cв мире §e{world}",
                "x", String.valueOf(loc.getBlockX()),
                "y", String.valueOf(loc.getBlockY()),
                "z", String.valueOf(loc.getBlockZ()),
                "world", world
        );
        player.sendMessage(message);
    }
}
