package com.cubixsmp.listeners;

import com.cubixsmp.CubixSMP;
import com.cubixsmp.MessagesManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DistanceListener implements Listener {

    private final CubixSMP plugin;
    private final Map<UUID, Location> lastPosition = new ConcurrentHashMap<>();
    private final Map<UUID, Double> cumulativeDistance = new ConcurrentHashMap<>();

    /**
     * Максимальное расстояние за один PlayerMoveEvent, которое считается
     * «пешим перемещением». Всё, что больше — телепортация (/tpa, /home, /spawn),
     * и опыт за такую дистанцию не начисляется.
     * Игрок может бежать со спринтом ~0.7 блоков/тик, лететь на элитрах ~2 блока/тик.
     * 50 блоков за тик — гарантированная телепортация.
     */
    private static final double MAX_LEGIT_MOVE = 50.0;

    public DistanceListener(CubixSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastPosition.remove(uuid);
        cumulativeDistance.remove(uuid);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Location last = lastPosition.get(uuid);
        if (last == null) {
            lastPosition.put(uuid, event.getFrom().clone());
            return;
        }

        double dx = event.getTo().getX() - last.getX();
        double dz = event.getTo().getZ() - last.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        // ─── Анти-ТП: если перемещение больше MAX_LEGIT_MOVE блоков за один тик,
        // это телепортация. Не начисляем опыт, просто обновляем позицию и выходим.
        if (distance > MAX_LEGIT_MOVE) {
            lastPosition.put(uuid, event.getTo().clone());
            return;
        }

        if (distance < 1.0) return;

        double cumulative = cumulativeDistance.getOrDefault(uuid, 0.0);
        cumulative += distance;
        cumulativeDistance.put(uuid, cumulative);

        int interval = plugin.getConfig().getInt("settings.distance-interval", 1000);
        int xpAmount = plugin.getConfig().getInt("settings.xp-per-distance-interval", 5);

        while (cumulative >= interval) {
            cumulative -= interval;
            cumulativeDistance.put(uuid, cumulative);
            plugin.getPlayerDataManager().addXp(uuid, xpAmount, player);
            plugin.setLastAction(uuid, "Distance");
            sendMessage(player, MessagesManager.format("xp.distance", "§7🚶 §a+{amount} XP §7(§7{blocks} blocks)",
                    "amount", String.valueOf(xpAmount), "blocks", String.valueOf(interval)));
        }

        lastPosition.put(uuid, event.getTo().clone());
    }

    private void sendMessage(Player player, String message) {
        if (plugin.getConfig().getBoolean("settings.use-actionbar", true)) {
            player.sendActionBar(net.kyori.adventure.text.Component.text(message));
        } else {
            player.sendMessage(message);
        }
    }
}
