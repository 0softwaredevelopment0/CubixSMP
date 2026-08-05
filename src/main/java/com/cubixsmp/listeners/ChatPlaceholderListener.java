package com.cubixsmp.listeners;

import com.cubixsmp.CubixSMP;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.regex.Pattern;

/**
 * Заменяет ВСЕ PAPI плейсхолдеры (%any_placeholder%) в чате на значения,
 * соответствующие КАЖДОМУ получателю сообщения.
 *
 * Например, игрок пишет "Мой уровень %cubixsmp_level%, баланс %vault_eco_balance%"
 * — каждый, кто видит это сообщение, увидит СВОИ значения.
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
            for (Player recipient : recipients) {
                // PlaceholderAPI.setPlaceholders() resolves ВСЕ зарегистрированные плейсхолдеры
                String resolved = PlaceholderAPI.setPlaceholders(recipient, raw);
                String msg = format.replace("%1$s", sender.getDisplayName())
                                   .replace("%2$s", resolved);
                recipient.sendMessage(msg);
            }

            // Лог в консоль (с значениями отправителя)
            String consoleMsg = PlaceholderAPI.setPlaceholders(sender, raw);
            String logMsg = format.replace("%1$s", sender.getDisplayName())
                                  .replace("%2$s", consoleMsg);
            Bukkit.getConsoleSender().sendMessage(logMsg);
        });
    }
}
