package com.cubixsmp.listeners;

import com.cubixsmp.CubixSMP;
import com.cubixsmp.WorldSettings;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.regex.Pattern;

/**
 * Заменяет ВСЕ PAPI плейсхолдеры (%any_placeholder%) в чате на значения
 * ОТПРАВИТЕЛЯ — все получатели видят одно и то же.
 *
 * Например, игрок пишет "Мой уровень %cubixsmp_level%, баланс %vault_eco_balance%"
 * — каждый, кто видит это сообщение, увидит уровень и баланс АВТОРА сообщения.
 *
 * ⚠ Fallback-путь: когда включён {@code chat-format.enabled} (по умолчанию true),
 * чат полностью обрабатывает {@link ChatFormatListener} (он первым отменяет событие),
 * поэтому этот слушатель фактически не срабатывает. Он остаётся как fallback на случай,
 * если chat-format выключат.
 *
 * Настройка: chat-placeholders в config.yml
 */
public class ChatPlaceholderListener implements Listener {

    private final CubixSMP plugin;
    /** Любой %текст% — стандартный формат PAPI */
    private static final Pattern HAS_PLACEHOLDER = Pattern.compile("%[^%]+%");
    private static final String CFG = "chat-placeholders.";

    public ChatPlaceholderListener(CubixSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!plugin.getConfig().getBoolean(CFG + "enabled", true)) return;

        // 🌍 Пер-мировая настройка: обработка чата CubixSMP в этом мире отключена —
        // выходим, НЕ трогая событие (его обработают другие плагины/ванильный чат).
        if (WorldSettings.isChatDisabled(plugin, event.getPlayer().getWorld())) return;

        String raw = event.getMessage();
        if (!HAS_PLACEHOLDER.matcher(raw).find()) return;

        // Если PAPI не установлен — не трогаем событие
        if (!plugin.hasPlaceholderAPI()) return;

        // Отменяем стандартную рассылку — будем отправлять сами
        event.setCancelled(true);

        Player sender = event.getPlayer();
        String format = event.getFormat();
        Player[] recipients = Bukkit.getOnlinePlayers().toArray(new Player[0]);

        // Переключаемся на главный поток — PlaceholderAPI.setPlaceholders() и sendMessage() там
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                // Значения плейсхолдеров — ОТПРАВИТЕЛЯ, одинаковые для всех получателей
                String resolved = PlaceholderAPI.setPlaceholders(sender, raw);
                String msg = format.replace("%1$s", sender.getDisplayName())
                                   .replace("%2$s", resolved);
                for (Player recipient : recipients) {
                    recipient.sendMessage(msg);
                }

                // Лог в консоль (с значениями отправителя)
                String logMsg = format.replace("%1$s", sender.getDisplayName())
                                      .replace("%2$s", resolved);
                Bukkit.getConsoleSender().sendMessage(logMsg);
            } catch (Exception e) {
                // Внутренняя ошибка при отправке — уведомляем только отправителя
                plugin.getLogger().warning("[chat-placeholders] Ошибка отправки сообщения от "
                        + sender.getName() + ": " + e.getMessage());
                String errorMsg = plugin.getConfig().getString(
                        "chat-format.send-error-message",
                        "<red>Не удалось отправить сообщение в чат из-за внутренней ошибки сервера, попробуйте позже.</red>");
                sender.sendMessage(deserialize(errorMsg));
            }
        });
    }

    /** MiniMessage-строка → Component с fallback на legacy-разбор (битые теги не падают). */
    private static net.kyori.adventure.text.Component deserialize(String str) {
        try {
            return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(str);
        } catch (Exception e) {
            return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(str);
        }
    }
}
