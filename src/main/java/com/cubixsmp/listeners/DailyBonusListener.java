package com.cubixsmp.listeners;

import com.cubixsmp.CubixSMP;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Автоматически выдаёт ежедневный бонус при ПЕРВОМ входе игрока за день.
 * Команда /cubixsmp daily больше не нужна — опыт начисляется сам при заходе.
 */
public class DailyBonusListener implements Listener {

    private final CubixSMP plugin;

    public DailyBonusListener(CubixSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Небольшая задержка, чтобы данные игрока гарантированно загрузились
        // (PlayerDataManager подгружает их в своём обработчике PlayerJoinEvent).
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && plugin.getPlayerDataManager().canClaimDailyBonus(player.getUniqueId())) {
                plugin.getPlayerDataManager().claimDailyBonus(player.getUniqueId(), player);
            }
        }, 40L);
    }
}
