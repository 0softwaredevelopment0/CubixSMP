package com.cubixsmp.listeners;

import com.cubixsmp.CubixSMP;
import com.cubixsmp.MessagesManager;
import com.cubixsmp.WorldSettings;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;

public class FishingListener implements Listener {

    private final CubixSMP plugin;

    public FishingListener(CubixSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (!plugin.getConfig().getBoolean("fishing.enabled", true)) return;
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;

        Player player = event.getPlayer();

        if (event.getCaught() instanceof Item item) {
            var type = item.getItemStack().getType();

            if (type.name().contains("COD") || type.name().contains("SALMON")
                    || type.name().contains("PUFFERFISH") || type.name().contains("TROPICAL_FISH")) {

                // 🌍 Пер-мировая настройка: в этом мире Cubix-XP не начисляется
                if (WorldSettings.isXpDisabled(plugin, player.getWorld())) return;

                double xp = plugin.getConfig().getDouble("fishing.xp-per-catch", 5.0);
                plugin.getPlayerDataManager().addXp(player.getUniqueId(), xp, player);
                plugin.setLastAction(player.getUniqueId(), "Fishing");
                String msg = MessagesManager.format("xp.fishing", "§7🎣 §a+{amount} XP §7(Рыбалка)",
                        "amount", formatXp(xp));
                if (plugin.getConfig().getBoolean("settings.use-actionbar", true)) {
                    player.sendActionBar(net.kyori.adventure.text.Component.text(msg));
                } else {
                    player.sendMessage(msg);
                }
            }
        }
    }

    private String formatXp(double xp) {
        if (xp == (long) xp) return String.valueOf((long) xp);
        return String.format("%.1f", xp);
    }
}
