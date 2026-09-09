package com.cubixsmp.listeners;

import com.cubixsmp.ChatChannel;
import com.cubixsmp.CubixSMP;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Единый обработчик чата CubixSMP. Берёт чат под свой контроль и делает:
 *
 * <ul>
 *   <li><b>Формат чата</b> — вся строка чата собирается по шаблону
 *       {@code chat-format.format} (MiniMessage). Внутренние плейсхолдеры:
 *       {@code {player}} (ник отправителя) и {@code {message}} (сообщение).
 *       В формате работают и любые %плейсхолдеры% PlaceholderAPI — их значение
 *       принадлежит ОТПРАВИТЕЛЮ и одинаково для всех получателей.</li>
 *   <li><b>miniMessage</b> — игроки с пермишеном {@code cubixsmp.chat.format} могут
 *       использовать теги форматирования в тексте сообщения: цвета, градиенты,
 *       жирный и т.д. Без права теги показываются как обычный текст.</li>
 *   <li><b>[item_hand]</b> — вместо текста показывается название предмета в руке,
 *       при наведении — тултип с зачарованиями и лором (как в инвентаре).</li>
 *   <li><b>Клик по нику</b> — клик именно по нику отправителя (не по всему
 *       сообщению) открывает нативное диалоговое окно (Minecraft Dialog API)
 *       «Что вы хотите сделать?» с кнопками {@code /m <ник>}, {@code /tpa <ник>}
 *       и «Отмена» (анти-спам: КД 1 секунда).</li>
 *   <li><b>PlaceholderAPI</b> — %плейсхолдеры% в сообщении и в формате
 *       подставляются со значением ОТПРАВИТЕЛЯ (одинаковым для всех).</li>
 *   <li><b>Каналы чата</b> ({@link ChatChannel}) — локальный [L] / глобальный [G] /
 *       мировой [W] / админский [A]. Канал выбирается префиксом в начале сообщения
 *       (по умолчанию: без префикса / ! / $ / #), на отправку нужно право канала,
 *       локальный действует в радиусе {@code chat-channels.local-range} блоков,
 *       админский видят только игроки с его правом.</li>
 * </ul>
 *
 * Настройки: секция {@code chat-format} в config.yml.
 * Работает вместе с {@link ChatMentionListener} (@пнг) и заменяет собой
 * {@link ChatPlaceholderListener}, когда включён (fallback при выключенном).
 */
public class ChatFormatListener implements Listener {

    private static final String CFG = "chat-format.";
    private static final Pattern HAS_PLACEHOLDER = Pattern.compile("%[^%]+%");
    /** Служебный токен для [item_hand] — заменяется на Component после legacy-десериализации. */
    private static final String ITEM_TOKEN = "\u0001CUBIX_ITEM_HAND\u0001";
    // ВАЖНО: Pattern.quote() + Pattern.LITERAL вместе НЕ работают — с флагом LITERAL
    // обёртка \Q...\E из quote() становится буквальным текстом, и паттерн никогда
    // не находит токен. Нужен либо quote(), либо LITERAL — не оба.
    private static final Pattern ITEM_TOKEN_PATTERN = Pattern.compile(ITEM_TOKEN, Pattern.LITERAL);
    private static final Pattern ITEM_HAND_SOURCE = Pattern.compile("(?i)\\[item_hand\\]");
    /** Внутренние плейсхолдеры шаблона чата chat-format.format. */
    private static final Pattern PLAYER_TOKEN_PATTERN = Pattern.compile("\\{player\\}");
    private static final Pattern MESSAGE_TOKEN_PATTERN = Pattern.compile("\\{message\\}");
    /** Legacy-коды (§a, &l, §x§R§R§G§G§B§B) → miniMessage-теги, чтобы можно было смешивать стили. */
    private static final Pattern LEGACY_CODE = Pattern.compile(
            "(?i)(?:§|&)x(((?:§|&)[0-9a-fA-F]){6})|(?:§|&)([0-9a-fk-orx])");
    private static final Map<Character, String> LEGACY_MM = Map.ofEntries(
            Map.entry('0', "<black>"), Map.entry('1', "<dark_blue>"),
            Map.entry('2', "<dark_green>"), Map.entry('3', "<dark_aqua>"),
            Map.entry('4', "<dark_red>"), Map.entry('5', "<dark_purple>"),
            Map.entry('6', "<gold>"), Map.entry('7', "<gray>"),
            Map.entry('8', "<dark_gray>"), Map.entry('9', "<blue>"),
            Map.entry('a', "<green>"), Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"), Map.entry('d', "<light_purple>"),
            Map.entry('e', "<yellow>"), Map.entry('f', "<white>"),
            Map.entry('k', "<obfuscated>"), Map.entry('l', "<bold>"),
            Map.entry('m', "<strikethrough>"), Map.entry('n', "<underlined>"),
            Map.entry('o', "<italic>"), Map.entry('r', "<reset>"));

    /** MiniMessage с ограниченным набором тегов — только безопасное форматирование текста. */
    private static final MiniMessage CHAT_MM = MiniMessage.builder()
            .tags(TagResolver.resolver(
                    StandardTags.color(),
                    StandardTags.decorations(),
                    StandardTags.gradient(),
                    StandardTags.rainbow(),
                    StandardTags.reset(),
                    StandardTags.transition()
            ))
            .build();

    private final CubixSMP plugin;
    private final ChatMentionListener mentionListener;

    public ChatFormatListener(CubixSMP plugin, ChatMentionListener mentionListener) {
        this.plugin = plugin;
        this.mentionListener = mentionListener;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!plugin.getConfig().getBoolean(CFG + "enabled", true)) return;

        Player sender = event.getPlayer();

        // ─── Каналы чата (chat-channels): выбор по префиксу сообщения ───
        boolean channelsEnabled = plugin.getConfig().getBoolean("chat-channels.enabled", true);
        final ChatChannel channel;
        if (channelsEnabled) {
            channel = ChatChannel.detect(plugin.getConfig(), event.getMessage());
            String stripped = ChatChannel.strip(plugin.getConfig(), channel, event.getMessage());
            if (stripped.trim().isEmpty()) {
                event.setCancelled(true); // только префикс без текста — не отправляем
                return;
            }
            if (!sender.hasPermission(channel.permission(plugin.getConfig()))) {
                event.setCancelled(true);
                String noPerm = plugin.getConfig().getString(
                        "chat-channels.no-permission-message",
                        "<red>У вас нет прав чтобы отправлять сообщение в данный канал, попробуйте позже.</red>");
                sender.sendMessage(deserializeChatFormat(noPerm));
                return;
            }
            event.setMessage(stripped);
        } else {
            channel = ChatChannel.LOCAL;
        }

        String raw = event.getMessage();

        // 1. miniMessage (если есть право) → legacy-строка с §-кодами
        String mm = processMiniMessage(sender, raw);

        // 2. @пнг + звуки пинга (форматирование остаётся в legacy-строке)
        String mentioned = mentionListener.formatMessage(sender, mm);

        // 3. [item_hand] → токен (в Component превратим на главном потоке, из-за инвентаря)
        boolean hasItem = mentioned.toLowerCase(Locale.ROOT).contains("[item_hand]");
        String messageWithToken = hasItem ? replaceItemHand(mentioned) : mentioned;

        boolean hasPlaceholders = plugin.hasPlaceholderAPI() && HAS_PLACEHOLDER.matcher(raw).find();

        // 4. Отменяем стандартную рассылку — отправляем сами, чтобы работали компоненты
        event.setCancelled(true);

        // Шаблон чата: chat-format.format (MiniMessage + {player}/{message} + PAPI).
        // Если формат не задан/пустой — fallback на стандартный формат сервера <%1$s> %2$s
        String format = plugin.getConfig().getString(CFG + "format",
                "<dark_gray>[</dark_gray><aqua>{player}</aqua><dark_gray>]</dark_gray> <white>{message}</white>");
        if (format == null || format.isEmpty()) {
            format = event.getFormat() != null ? event.getFormat() : "<%1$s> %2$s";
        }
        final String chatFormat = format;
        Player[] recipients = event.getRecipients().toArray(new Player[0]);

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Component nickname = buildNicknameComponent(sender);
                Component itemComponent = hasItem ? buildItemComponent(sender) : null;

                // Значения %плейсхолдеров% — и в СООБЩЕНИИ, и в ФОРМАТИРОВАНИИ чата —
                // принадлежат ОТПРАВИТЕЛЮ: все получатели видят одно и то же
                String resolved = hasPlaceholders
                        ? PlaceholderAPI.setPlaceholders(sender, messageWithToken)
                        : messageWithToken;
                String resolvedFormat = plugin.hasPlaceholderAPI()
                        ? PlaceholderAPI.setPlaceholders(sender, chatFormat)
                        : chatFormat;

                Component message = LegacyComponentSerializer.legacySection().deserialize(resolved);
                if (hasItem) {
                    message = replaceItemToken(message, itemComponent);
                }
                Component line = buildChatLine(resolvedFormat, nickname, message);
                if (channelsEnabled) {
                    line = appendChannelPrefix(channel, line);
                }

                // Получатели по каналу: локальный — в радиусе, мировой — тот же мир,
                // админский — только игроки с правом, глобальный — все
                Player[] targets = channelsEnabled ? filterRecipients(sender, channel, recipients) : recipients;
                for (Player recipient : targets) {
                    recipient.sendMessage(line);
                }

                // Лог в консоль (со значениями отправителя, без служебных токенов)
                String consoleMsg = hasPlaceholders
                        ? PlaceholderAPI.setPlaceholders(sender, messageWithToken)
                        : messageWithToken;
                Component consoleMessage = LegacyComponentSerializer.legacySection().deserialize(consoleMsg);
                if (hasItem) {
                    consoleMessage = replaceItemToken(consoleMessage, itemComponent);
                }
                String consoleFormat = plugin.hasPlaceholderAPI()
                        ? PlaceholderAPI.setPlaceholders(sender, chatFormat)
                        : chatFormat;
                Component consoleLine = buildChatLine(consoleFormat, nickname, consoleMessage);
                if (channelsEnabled) {
                    consoleLine = appendChannelPrefix(channel, consoleLine);
                }
                Bukkit.getConsoleSender().sendMessage(consoleLine);
            } catch (Exception e) {
                // Внутренняя ошибка при сборке/отправке — уведомляем только отправителя
                plugin.getLogger().warning("[chat-format] Ошибка отправки сообщения от "
                        + sender.getName() + ": " + e.getMessage());
                String errorMsg = plugin.getConfig().getString(
                        CFG + "send-error-message",
                        "<red>Не удалось отправить сообщение в чат из-за внутренней ошибки сервера, попробуйте позже.</red>");
                sender.sendMessage(deserializeChatFormat(errorMsg));
            }
        });
    }

    // ─── Каналы чата ───────────────────────────────────────────────

    /**
     * Добавляет префикс канала (MiniMessage из конфига, дефолт [L]/[G]/[W]/[A])
     * в начало строки чата.
     */
    private Component appendChannelPrefix(ChatChannel channel, Component line) {
        String prefix = channel.prefix(plugin.getConfig());
        if (prefix == null || prefix.isEmpty()) return line;
        return deserializeChatFormat(prefix).append(line);
    }

    /**
     * Фильтрует получателей по каналу:
     * LOCAL — игроки в радиусе {@code chat-channels.local-range} блоков (и тот же мир);
     * WORLD — игроки в том же мире; ADMIN — игроки с правом канала; GLOBAL — все.
     * Отправитель видит своё сообщение всегда.
     */
    private Player[] filterRecipients(Player sender, ChatChannel channel, Player[] recipients) {
        double range = plugin.getConfig().getDouble("chat-channels.local-range", 100);
        double rangeSq = range * range;
        String adminPerm = channel.permission(plugin.getConfig());
        org.bukkit.World senderWorld = sender.getWorld();
        org.bukkit.Location senderLoc = sender.getLocation();

        List<Player> filtered = new ArrayList<>(recipients.length);
        for (Player recipient : recipients) {
            if (recipient.equals(sender)) {
                filtered.add(recipient);
                continue;
            }
            switch (channel) {
                case LOCAL -> {
                    if (recipient.getWorld().equals(senderWorld)
                            && senderLoc.distanceSquared(recipient.getLocation()) <= rangeSq) {
                        filtered.add(recipient);
                    }
                }
                case WORLD -> {
                    if (recipient.getWorld().equals(senderWorld)) {
                        filtered.add(recipient);
                    }
                }
                case ADMIN -> {
                    if (recipient.hasPermission(adminPerm)) {
                        filtered.add(recipient);
                    }
                }
                default -> filtered.add(recipient); // GLOBAL — все
            }
        }
        return filtered.toArray(new Player[0]);
    }

    // ─── miniMessage ───────────────────────────────────────────────

    /**
     * Если у игрока есть право на форматирование — парсит miniMessage-теги
     * и возвращает legacy-строку. Иначе возвращает сообщение как есть
     * (теги будут видны как обычный текст).
     */
    private String processMiniMessage(Player sender, String raw) {
        String permission = plugin.getConfig().getString(CFG + "permission", "cubixsmp.chat.format");
        if (!sender.hasPermission(permission)) return raw;

        try {
            Component parsed = CHAT_MM.deserialize(raw);
            return LegacyComponentSerializer.legacySection().serialize(parsed);
        } catch (Exception e) {
            return raw; // битые/неизвестные теги — показываем как есть
        }
    }

    // ─── [item_hand] ───────────────────────────────────────────────

    private String replaceItemHand(String message) {
        return ITEM_HAND_SOURCE.matcher(message).replaceAll(ITEM_TOKEN);
    }

    /**
     * Компонент предмета в руке: в чате — название, при наведении — тултип
     * (зачарования + лор), как в инвентаре.
     */
    private Component buildItemComponent(Player sender) {
        ItemStack item = sender.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            return LegacyComponentSerializer.legacySection().deserialize(
                    plugin.getConfig().getString(CFG + "item-empty", "§7пустая рука"));
        }

        ItemMeta meta = item.getItemMeta();
        Component name = (meta != null && meta.hasDisplayName())
                ? meta.displayName()
                : Component.translatable(item.getType().translationKey());

        List<Component> lines = new ArrayList<>();
        lines.add(name);

        if (meta != null) {
            Map<Enchantment, Integer> enchants;
            if (meta instanceof EnchantmentStorageMeta storageMeta) {
                enchants = storageMeta.getStoredEnchants();
            } else {
                enchants = meta.getEnchants();
            }
            for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                lines.add(entry.getKey().displayName(entry.getValue()));
            }
            if (meta.hasLore()) {
                for (String loreLine : meta.getLore()) {
                    lines.add(LegacyComponentSerializer.legacySection().deserialize(loreLine));
                }
            }
        }

        Component hover = Component.empty();
        boolean first = true;
        for (Component line : lines) {
            if (first) {
                hover = line;
                first = false;
            } else {
                hover = hover.append(Component.newline()).append(line);
            }
        }
        return name.hoverEvent(HoverEvent.showText(hover));
    }

    /**
     * Заменяет токен [item_hand] во всём дереве компонентов на предмет из руки.
     * Используется встроенный {@code replaceText} — стили окружающего текста
     * (цвета, форматирование) сохраняются автоматически.
     */
    private Component replaceItemToken(Component component, Component replacement) {
        return component.replaceText(config -> config
                .match(ITEM_TOKEN_PATTERN)
                .replacement(replacement));
    }

    // ─── Сборка строки чата по шаблону ────────────────────────────

    /**
     * Собирает полную строку чата по шаблону из конфига.
     * <p>
     * Новый формат (MiniMessage): {@code {player}} заменяется на компонент ника,
     * {@code {message}} — на компонент сообщения; %плейсхолдеры% PAPI уже
     * подставлены в {@code format} перед вызовом. Стили шаблона вокруг
     * плейсхолдеров сохраняются автоматически.
     * <p>
     * Старый формат ({@code %1$s} / {@code %2$s}) поддерживается как fallback.
     */
    private Component buildChatLine(String format, Component nickname, Component message) {
        if (format.indexOf("{player}") < 0 && format.indexOf("{message}") < 0) {
            return assemble(format, nickname, message);
        }
        Component template = deserializeChatFormat(format);
        return template
                .replaceText(cfg -> cfg.match(PLAYER_TOKEN_PATTERN).replacement(nickname))
                .replaceText(cfg -> cfg.match(MESSAGE_TOKEN_PATTERN).replacement(message));
    }

    /**
     * Парсит шаблон чата. Поддерживаются и miniMessage-теги, и legacy-коды
     * (§/&), и их смесь в одной строке: legacy-коды конвертируются в теги
     * до разбора. Если разобрать не вышло — fallback на legacy-разбор.
     */
    private Component deserializeChatFormat(String format) {
        try {
            return MiniMessage.miniMessage().deserialize(legacyToMiniMessage(format));
        } catch (Exception e) {
            plugin.getLogger().warning("[chat-format] Не удалось разобрать format, использую legacy-разбор: " + format);
            return LegacyComponentSerializer.legacySection().deserialize(format);
        }
    }

    /**
     * Конвертирует legacy-коды цвета/форматирования (§a, &l, §xRRGGBB-формат)
     * в miniMessage-теги. Строки без legacy-кодов возвращаются без изменений.
     */
    private String legacyToMiniMessage(String input) {
        Matcher matcher = LEGACY_CODE.matcher(input);
        StringBuilder parsed = new StringBuilder();
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                // §x§R§R§G§G§B§B — hex-цвет: каждый байт записан как §+цифра
                String hex = matcher.group(1);
                StringBuilder color = new StringBuilder("#");
                for (int i = 0; i < hex.length(); i += 2) {
                    color.append(hex.charAt(i + 1));
                }
                matcher.appendReplacement(parsed, Matcher.quoteReplacement("<" + color + ">"));
            } else {
                char code = Character.toLowerCase(matcher.group(3).charAt(0));
                String tag = LEGACY_MM.get(code);
                matcher.appendReplacement(parsed, Matcher.quoteReplacement(tag != null ? tag : ""));
            }
        }
        matcher.appendTail(parsed);
        return parsed.toString();
    }

    // ─── Кликабельный ник ─────────────────────────────────────────

    private Component buildNicknameComponent(Player sender) {
        String display = sender.getDisplayName() != null && !sender.getDisplayName().isEmpty()
                ? sender.getDisplayName()
                : sender.getName();

        Component text = LegacyComponentSerializer.legacySection().deserialize(display);

        String hover = plugin.getConfig().getString(CFG + "nickname-hover", "§7Клик — действия с игроком");
        Component nick = text.hoverEvent(HoverEvent.showText(LegacyComponentSerializer.legacySection().deserialize(hover)));

        // Клик ТОЛЬКО по нику (не по всему сообщению) открывает GUI-диалог действий
        if (plugin.getConfig().getBoolean(CFG + "click-menu-enabled", true)) {
            nick = nick.clickEvent(ClickEvent.runCommand("/csmp action " + sender.getName()));
        }
        return nick;
    }

    // ─── Сборка строки чата ───────────────────────────────────────

    /**
     * Собирает полную строку чата по формату сервера (например {@code <%1$s> %2$s}),
     * подставляя вместо %1$s кликабельный ник, вместо %2$s — сообщение.
     */
    private Component assemble(String format, Component nickname, Component message) {
        int i1 = format.indexOf("%1$s");
        int i2 = format.indexOf("%2$s");
        if (i1 < 0 || i2 < 0 || i2 < i1) {
            // Нестандартный формат — просто склеиваем
            return Component.text().append(nickname).append(Component.text(": ")).append(message).build();
        }
        String pre = format.substring(0, i1);
        String mid = format.substring(i1 + 4, i2);
        String post = format.substring(i2 + 4);

        return Component.text(pre)
                .append(nickname)
                .append(Component.text(mid))
                .append(message)
                .append(Component.text(post));
    }
}
