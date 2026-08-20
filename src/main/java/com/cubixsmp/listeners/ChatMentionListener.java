package com.cubixsmp.listeners;

import com.cubixsmp.CubixSMP;
import com.cubixsmp.MessagesManager;
import com.cubixsmp.PingSettingsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Слушатель чата, который обрабатывает @пнг.
 *
 * @PlayerName   — пинг конкретного игрока
 * @everyone     — пинг всех онлайн-игроков
 * @admins       — пинг всех, у кого есть право admins-target
 *
 * Настройки: chat-ping в config.yml
 */
public class ChatMentionListener implements Listener {

    private final CubixSMP plugin;
    private final PingSettingsManager pingSettings;

    private static final Pattern MENTION_PATTERN = Pattern.compile("@(\\S+)");
    private static final String CFG = "chat-ping.";

    public ChatMentionListener(CubixSMP plugin, PingSettingsManager pingSettings) {
        this.plugin = plugin;
        this.pingSettings = pingSettings;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        // Fallback-путь: если ChatFormatListener выключен (chat-format.enabled: false),
        // сообщение форматируется здесь. Когда ChatFormatListener активен, он вызывает
        // formatMessage() сам и отменяет событие раньше, чем сработает этот обработчик.
        event.setMessage(formatMessage(event.getPlayer(), event.getMessage()));
    }

    /**
     * Ищет @пнг в сообщении, выделяет их и запускает звуки пинга.
     * Возвращает строку с legacy-кодами (§), готовую для отправки в чат.
     * Если пингов нет (или фича выключена) — возвращает сообщение без изменений.
     */
    public String formatMessage(Player sender, String raw) {
        if (!plugin.getConfig().getBoolean(CFG + "enabled", true)) return raw;
        Matcher matcher = MENTION_PATTERN.matcher(raw);

        if (!matcher.find()) return raw;

        List<Player> targets = new ArrayList<>();
        StringBuilder parsed = new StringBuilder();
        matcher.reset();

        // Получаем MiniMessage шаблон из конфига
        String mmFormat = plugin.getConfig().getString(CFG + "mention-format",
                "<bold><underlined>@<mention></underlined></bold>");

        int lastEnd = 0;
        while (matcher.find()) {
            String mention = matcher.group(1);
            parsed.append(raw, lastEnd, matcher.start());

            List<Player> matched = resolveMention(sender, mention);
            targets.addAll(matched);

            // Парсим MiniMessage → Component → LEGACY (§) строка
            Component formatted = MiniMessage.miniMessage().deserialize(
                    mmFormat,
                    Placeholder.parsed("mention", mention)
            );
            parsed.append(LegacyComponentSerializer.legacySection().serialize(formatted));

            lastEnd = matcher.end();
        }
        parsed.append(raw.substring(lastEnd));

        if (!targets.isEmpty()) {
            Player[] targetArray = targets.toArray(new Player[0]);
            Bukkit.getScheduler().runTask(plugin, () -> playPingSounds(targetArray));
        }

        return parsed.toString();
    }

    private List<Player> resolveMention(Player sender, String mention) {
        List<Player> result = new ArrayList<>();
        String lower = mention.toLowerCase(Locale.ROOT);

        if (lower.equals("everyone")) {
            if (checkMentionPermission(sender, "everyone", CFG + "ping-types.everyone")) {
                result.addAll(Bukkit.getOnlinePlayers());
            }
            return result;
        }

        if (lower.equals("admins") || lower.equals("admin") || lower.equals("ops")) {
            if (checkMentionPermission(sender, "admins", CFG + "ping-types.admins")) {
                String targetPerm = plugin.getConfig().getString(
                    CFG + "ping-types.admins.target-permission",
                    "cubixsmp.ping.admins-target"
                );
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.hasPermission(targetPerm)) {
                        result.add(p);
                    }
                }
            }
            return result;
        }

        // @PlayerName
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase(mention)) {
                result.add(p);
                break;
            }
        }
        return result;
    }

    private boolean checkMentionPermission(Player sender, String type, String configPath) {
        if (!plugin.getConfig().getBoolean(configPath + ".enabled", true)) return false;

        String permission = plugin.getConfig().getString(configPath + ".permission",
                "cubixsmp.ping." + type);
        if (!sender.hasPermission(permission)) {
            String msg = MessagesManager.format("ping.no_permission",
                    "§c❌ У вас нет прав использовать @{type}!",
                    "type", type);
            sender.sendMessage(msg);
            return false;
        }
        return true;
    }

    private void playPingSounds(Player[] targets) {
        String soundName = plugin.getConfig().getString(CFG + "ping-sound", "BLOCK_NOTE_BLOCK_PLING");
        float volume = (float) plugin.getConfig().getDouble(CFG + "ping-sound-volume", 0.8);
        float pitch = (float) plugin.getConfig().getDouble(CFG + "ping-sound-pitch", 1.5);

        Sound sound;
        try {
            sound = Sound.valueOf(soundName);
        } catch (IllegalArgumentException e) {
            sound = Sound.BLOCK_NOTE_BLOCK_PLING;
        }

        for (Player target : targets) {
            if (pingSettings.isEnabled(target)) {
                target.playSound(target.getLocation(), sound, volume, pitch);
            }
        }
    }
}
