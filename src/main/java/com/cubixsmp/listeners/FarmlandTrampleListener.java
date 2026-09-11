package com.cubixsmp.listeners;

import com.cubixsmp.CubixSMP;
import com.cubixsmp.WorldSettings;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Слушатель, который отключает вытаптывание грядок (FARMLAND → DIRT)
 * игроками и мобами.
 *
 * Настройки:
 * <ul>
 *   <li>{@code farming.no-trampling} — глобальный запрет вытаптывания;</li>
 *   <li>{@code worlds.<мир>.no-trampling} — пер-мировое переопределение:
 *       {@code true} — запретить в мире, {@code false} — разрешить в мире
 *       (даже если глобально запрещено); не задано — берётся глобальная.</li>
 * </ul>
 */
public class FarmlandTrampleListener implements Listener {

    private final CubixSMP plugin;

    public FarmlandTrampleListener(CubixSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.FARMLAND) return;

        if (!checkEnabled(block.getWorld())) return;

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getBlock().getType() != Material.FARMLAND) return;

        // Отменяем только превращение в землю (само вытаптывание)
        if (event.getTo() != Material.DIRT) return;

        if (!checkEnabled(event.getBlock().getWorld())) return;

        event.setCancelled(true);
    }

    /** Проверка с учётом пер-мирового переопределения (worlds.<мир>.no-trampling). */
    private boolean checkEnabled(org.bukkit.World world) {
        return WorldSettings.isTramplingBlocked(plugin, world);
    }
}
