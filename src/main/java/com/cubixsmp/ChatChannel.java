package com.cubixsmp;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Каналы чата CubixSMP: локальный, глобальный, мировой и админский.
 * <p>
 * Настройки — секция {@code chat-channels} в config.yml:
 * <ul>
 *   <li>{@code prefix} — префикс канала, показываемый в чате (MiniMessage), дефолт [L]/[G]/[W]/[A];</li>
 *   <li>{@code message-prefix} — префикс в начале сообщения для переключения на канал
 *       (у локального пустой — это дефолтный канал);</li>
 *   <li>{@code permission} — право на отправку сообщений в канал.</li>
 * </ul>
 * <p>
 * Админский канал дополнительно видят только игроки с его правом. Локальный канал
 * действует в радиусе {@code chat-channels.local-range} блоков.
 */
public enum ChatChannel {

    LOCAL("local", "[L]", "", "cubixsmp.chat.channel.local"),
    GLOBAL("global", "[G]", "!", "cubixsmp.chat.channel.global"),
    WORLD("world", "[W]", "$", "cubixsmp.chat.channel.world"),
    ADMIN("admin", "[A]", "#", "cubixsmp.chat.channel.admin");

    private static final String CFG = "chat-channels.channels.";

    private final String key;
    private final String defaultPrefix;
    private final String defaultMessagePrefix;
    private final String defaultPermission;

    ChatChannel(String key, String defaultPrefix, String defaultMessagePrefix, String defaultPermission) {
        this.key = key;
        this.defaultPrefix = defaultPrefix;
        this.defaultMessagePrefix = defaultMessagePrefix;
        this.defaultPermission = defaultPermission;
    }

    /** Путь в конфиге: chat-channels.channels.<key>. */
    public String cfg() {
        return CFG + key;
    }

    /** Префикс канала в чате (MiniMessage-строка). */
    public String prefix(FileConfiguration config) {
        return config.getString(cfg() + ".prefix", defaultPrefix);
    }

    /** Префикс сообщения для переключения на канал. */
    public String messagePrefix(FileConfiguration config) {
        return config.getString(cfg() + ".message-prefix", defaultMessagePrefix);
    }

    /**
     * Право на отправку сообщений в канал.
     * Если в конфиге право явно пустое/null — используется дефолт (иначе
     * {@code hasPermission(null)} у игрока бросил бы исключение).
     */
    public String permission(FileConfiguration config) {
        String p = config.getString(cfg() + ".permission", defaultPermission);
        return (p == null || p.isEmpty()) ? defaultPermission : p;
    }

    /**
     * Определяет канал по префиксу в начале сообщения.
     * Проверяем в порядке ADMIN → WORLD → GLOBAL → LOCAL (если у локального задан
     * префикс); сообщение без совпавшего префикса — локальный канал (дефолт).
     */
    public static ChatChannel detect(FileConfiguration config, String message) {
        for (ChatChannel ch : values()) {
            String p = ch.messagePrefix(config);
            if (p != null && !p.isEmpty() && message.startsWith(p)) {
                return ch;
            }
        }
        return LOCAL;
    }

    /**
     * Убирает префикс канала из начала сообщения (и лишние пробелы после него:
     * {@code "! hello"} → {@code "hello"}). Если канал локальный или префикс не совпал —
     * возвращает сообщение без изменений.
     */
    public static String strip(FileConfiguration config, ChatChannel channel, String message) {
        String p = channel.messagePrefix(config);
        if (p == null || p.isEmpty() || !message.startsWith(p)) {
            return message;
        }
        String stripped = message.substring(p.length());
        int i = 0;
        while (i < stripped.length() && Character.isWhitespace(stripped.charAt(i))) {
            i++;
        }
        return stripped.substring(i);
    }
}
