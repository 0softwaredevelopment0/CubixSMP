package com.cubixsmp.listeners;

import com.cubixsmp.CubixSMP;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сообщает игроку в ЧАТ о входе/выходе из региона WorldGuard:
 * «Вы вошли в регион &lt;название&gt;» / «Вы покидаете регион &lt;название&gt;».
 *
 * <p>Сообщение отправляется ровно ОДИН раз на каждое изменение региона — без спама:
 * карта {@code currentRegion} хранит текущий регион игрока, и сообщение шлётся
 * только при его смене. Таймер не нужен — в отличие от ActionBar, чат не гаснет.</p>
 *
 * <p>Тексты и вкл/выкл: секция {@code region-chat} в config.yml (MiniMessage,
 * плейсхолдер {@code {region}} — название региона).</p>
 */
public class RegionChatListener implements Listener {

    private static final String GLOBAL_REGION_ID = "__global__";
    private static final String CFG = "region-chat.";

    private final CubixSMP plugin;
    /** Текущий регион игрока (id) — для отправки ровно одного сообщения на переход. */
    private final Map<UUID, String> currentRegion = new ConcurrentHashMap<>();
    private final RegionQuery query;

    public RegionChatListener(CubixSMP plugin) {
        this.plugin = plugin;
        // Создаём один экземпляр RegionQuery (внутри него свой кэш запросов),
        // чтобы не создавать новый на каждый шаг игрока.
        this.query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        if (!enabled()) return;
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return; // поворот головы / микро-движения — не считаем переходом
        }
        update(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!enabled()) return;
        update(event.getPlayer());
    }

    // Сообщение при ВХОДЕ НА СЕРВЕР не шлём — игрок, залогинившийся внутри региона,
    // не совершил «переход», и повторное сообщение на каждый заход выглядело бы спамом.

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        currentRegion.remove(event.getPlayer().getUniqueId());
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean(CFG + "enabled", true);
    }

    /**
     * Проверяет регион в точке игрока и шлёт сообщение ТОЛЬКО при изменении региона.
     */
    private void update(Player player) {
        UUID uuid = player.getUniqueId();
        ProtectedRegion region = getRegionAt(player);
        String newId = region == null ? null : region.getId();
        String previous = currentRegion.get(uuid);

        if (Objects.equals(previous, newId)) return; // регион не изменился — не спамим

        if (newId == null) {
            // Игрок вышел из всех регионов — сообщаем о выходе.
            // ВАЖНО: null в ConcurrentHashMap класть нельзя (NPE на put) — запись удаляем.
            currentRegion.remove(uuid);
            send(player, "exit-message", previous,
                    "<white>Вы покидаете регион <yellow>{region}</yellow></white>");
            return;
        }

        currentRegion.put(uuid, newId);
        if (previous != null) {
            // Прямой переход A → B: сообщаем и о выходе из A, и о входе в B
            send(player, "exit-message", previous,
                    "<white>Вы покидаете регион <yellow>{region}</yellow></white>");
        }
        send(player, "enter-message", newId,
                "<white>Вы вошли в регион <yellow>{region}</yellow></white>");
    }

    /** Отправляет сообщение из конфига (MiniMessage или legacy; {region} — название региона). */
    private void send(Player player, String messageKey, String regionId, String defaultTemplate) {
        String template = plugin.getConfig().getString(CFG + messageKey, defaultTemplate)
                .replace("{region}", regionId);
        player.sendMessage(deserialize(template));
    }

    private Component deserialize(String template) {
        try {
            return MiniMessage.miniMessage().deserialize(template);
        } catch (Exception e) {
            // Битый MiniMessage-шаблон — логируем и показываем как legacy (§)
            plugin.getLogger().warning("[region-chat] Не удалось разобрать шаблон, использую legacy-разбор: " + template);
            return LegacyComponentSerializer.legacySection().deserialize(template);
        }
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
