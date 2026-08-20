package com.cubixsmp.listeners;

import com.cubixsmp.CubixSMP;
import com.cubixsmp.MessagesManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

public class MiningListener implements Listener {

    private final CubixSMP plugin;

    public MiningListener(CubixSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        // 🛡️ Древние обломки: всегда выпадают незеритовым скрапом, а не блоком.
        // В ваниле обломки падают блоком даже без шёлка — их можно собрать,
        // поставить и сломать снова для фарма опыта. С крапом блок собрать
        // невозможно, поэтому абуз исключён. (Работает и вне mining.enabled.)
        if (plugin.getConfig().getBoolean("mining.debris-always-scrap", true)
                && event.getPlayer().getGameMode() != org.bukkit.GameMode.CREATIVE
                && event.getBlock().getType() == Material.ANCIENT_DEBRIS) {
            event.setDropItems(false);
            event.getBlock().getWorld().dropItemNaturally(
                    event.getBlock().getLocation().add(0.5, 0.5, 0.5),
                    new ItemStack(Material.NETHERITE_SCRAP, 1));
        }

        if (!plugin.getConfig().getBoolean("mining.enabled", true)) return;

        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material type = block.getType();

        double xp = getXpForBlock(type);
        if (xp <= 0) return;

        // 🛡️ Шёлковое касание: добыча руды шёлком XP не даёт (настраивается).
        // Иначе руду можно собрать шёлком, поставить и сломать снова — фарм опыта.
        if (plugin.getConfig().getBoolean("mining.silk-touch-no-xp", true)
                && player.getInventory().getItemInMainHand().containsEnchantment(Enchantment.SILK_TOUCH)) {
            return;
        }

        // Сначала проверяем трекер: если блок поставлен игроком — XP не начисляется
        if (plugin.getPlacedBlockTracker().wasPlacedByPlayer(block)) {
            return;
        }

        // Fallback: статический анализ для блоков, поставленных до установки плагина
        if (!plugin.getNaturalCheck().isNaturalOre(block)) return;

        plugin.getPlayerDataManager().addXp(player.getUniqueId(), xp, player);
        plugin.setLastAction(player.getUniqueId(), "Mining");
        String msg = MessagesManager.format("xp.mining", "§7⛏ §a+{amount} XP §7(Шахтёрство)",
                "amount", formatXp(xp));
        if (plugin.getConfig().getBoolean("settings.use-actionbar", true)) {
            player.sendActionBar(net.kyori.adventure.text.Component.text(msg));
        } else {
            player.sendMessage(msg);
        }
    }

    private double getXpForBlock(Material mat) {
        return plugin.getConfig().getDouble("mining.blocks." + mat.name(), 0);
    }

    private String formatXp(double xp) {
        if (xp == (long) xp) return String.valueOf((long) xp);
        return String.format("%.1f", xp);
    }
}
