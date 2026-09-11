package com.cubixsmp.listeners;

import com.cubixsmp.CubixSMP;
import com.cubixsmp.MessagesManager;
import com.cubixsmp.WorldSettings;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class WoodcuttingListener implements Listener {

    private final CubixSMP plugin;

    public WoodcuttingListener(CubixSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("woodcutting.enabled", true)) return;

        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material type = block.getType();

        // 🌍 Пер-мировая настройка: в этом мире Cubix-XP не начисляется
        if (WorldSettings.isXpDisabled(plugin, player.getWorld())) return;

        double xp = plugin.getConfig().getDouble("woodcutting.logs." + type.name(), 0);
        if (xp <= 0) return;

        // Трекер: поставленные игроком брёвна не дают XP
        if (plugin.getPlacedBlockTracker().wasPlacedByPlayer(block)) {
            return;
        }

        // Fallback: статический анализ для блоков, поставленных до установки плагина
        if (!plugin.getNaturalCheck().isNaturalLog(block)) return;

        plugin.getPlayerDataManager().addXp(player.getUniqueId(), xp, player);
        plugin.setLastAction(player.getUniqueId(), "Woodcutting");
        String msg = MessagesManager.format("xp.woodcutting", "§7🌲 §a+{amount} XP §7(Рубка)",
                "amount", formatXp(xp));
        if (plugin.getConfig().getBoolean("settings.use-actionbar", true)) {
            player.sendActionBar(net.kyori.adventure.text.Component.text(msg));
        } else {
            player.sendMessage(msg);
        }
    }

    private String formatXp(double xp) {
        if (xp == (long) xp) return String.valueOf((long) xp);
        return String.format("%.1f", xp);
    }
}
