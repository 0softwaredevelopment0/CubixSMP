package com.cubixsmp.listeners;

import com.cubixsmp.CubixSMP;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Показывает в ActionBar название региона WorldGuard, в котором находится игрок.
 *
 * <p>Формат: «Регион &lt;название&gt;». Если игрок имеет доступ к региону
 * (владелец или участник) — название зелёное {@code &a}, если нет — красное {@code &c}.</p>
 *
 * <p>WorldGuard 7 не имеет событий входа/выхода из региона, поэтому регион
 * определяется по местоположению игрока через {@link RegionQuery}.</p>
 *
 * <p>Настройки: секция {@code region-actionbar} в config.yml.</p>
 */
public class RegionActionBarListener implements Listener {

    private static final String GLOBAL_REGION_ID = "__global__";

    private final CubixSMP plugin;
    private final Map<UUID, String> currentRegion = new HashMap<>();
    private final RegionQuery query;

    public RegionActionBarListener(CubixSMP plugin) {
        this.plugin = plugin;
        // Создаём один экземпляр RegionQuery (внутри него свой кэш запросов),
        // чтобы не создавать новый на каждый шаг игрока.
        this.query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.getConfig().getBoolean("region-actionbar.enabled", true)) return;
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        update(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!plugin.getConfig().getBoolean("region-actionbar.enabled", true)) return;
        update(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("region-actionbar.enabled", true)) return;
        update(event.getPlayer());
    }

    private void update(Player player) {
        UUID uuid = player.getUniqueId();
        ProtectedRegion region = getRegionAt(player);

        if (region == null) {
            if (currentRegion.remove(uuid) != null) {
                player.sendActionBar(Component.empty());
            }
            return;
        }

        String id = region.getId();
        if (id.equals(currentRegion.get(uuid))) return;
        currentRegion.put(uuid, id);

        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
        boolean hasAccess = region.isOwner(localPlayer) || region.isMember(localPlayer);

        String template = hasAccess
                ? plugin.getConfig().getString("region-actionbar.message-with-access", "§7Регион §a{region}")
                : plugin.getConfig().getString("region-actionbar.message-without-access", "§7Регион §c{region}");
        String text = template.replace("{region}", id);

        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(text));
    }

    /**
     * Возвращает самый маленький (самый конкретный) регион в точке игрока,
     * игнорируя глобальный регион. {@code null}, если регионов нет.
     */
    private ProtectedRegion getRegionAt(Player player) {
        Location location = BukkitAdapter.adapt(player.getLocation());
        ApplicableRegionSet set = query.getApplicableRegions(location);

        Set<ProtectedRegion> regions = set.getRegions();
        ProtectedRegion best = null;
        int bestVolume = Integer.MAX_VALUE;
        for (ProtectedRegion region : regions) {
            if (GLOBAL_REGION_ID.equals(region.getId())) continue;
            int volume = region.volume();
            if (volume < bestVolume) {
                bestVolume = volume;
                best = region;
            }
        }
        return best;
    }
}
