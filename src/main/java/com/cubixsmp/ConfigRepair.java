package com.cubixsmp;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Проверка и починка {@code config.yml} при загрузке плагина и при {@code /cubixsmp reload}:
 * <ul>
 *   <li><b>Дубликаты ключей</b> — удаляются, остаётся ПОСЛЕДНЕЕ значение (как его
 *       трактует YAML-парсер). Раннее вхождение удаляется вместе со своим блоком
 *       (детьми), чтобы не оставалось «осиротевших» строк.</li>
 *   <li><b>Недостающие ключи</b> — берутся из дефолтного {@code config.yml} (внутри jar)
 *       и добавляются: новые секции — в конец файла, ключи внутри существующей
 *       секции — в конец этой секции. Комментарии в файле сохраняются.</li>
 * </ul>
 * Итог дополнительно проверяется на валидность YAML — при ошибке файл не перезаписывается.
 */
public final class ConfigRepair {

    private static final int INDENT = 2; // конфиг использует 2 пробела на уровень
    /** Строка ключа: отступ + имя + ':' + (необязательно значение/комментарий). */
    private static final Pattern KEY_LINE = Pattern.compile("^(\\s*)([A-Za-z0-9_\\-.]+)\\s*:(.*)$");
    private static final String MARKER = "# ─── Добавлено автоматически (недостающие ключи) ───";

    private ConfigRepair() {
    }

    /** Имя ключа из строки. */
    private static final class KeyName {
        final String name;
        final int indent;
        final boolean section;

        KeyName(String name, int indent, boolean section) {
            this.name = name;
            this.indent = indent;
            this.section = section;
        }
    }

    /** Ключ с полным путём (a.b.c) и позицией в файле. */
    private static final class KeyInfo {
        final String path;
        final int indent;
        final boolean section;
        final int lineIndex;

        KeyInfo(String path, int indent, boolean section, int lineIndex) {
            this.path = path;
            this.indent = indent;
            this.section = section;
            this.lineIndex = lineIndex;
        }
    }

    /**
     * Проверяет и чинит config.yml. Безопасен: при любой ошибке только пишет warning.
     */
    public static void repair(CubixSMP plugin) {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) return; // saveDefaultConfig() ещё не создал — нечего чинить

        String originalText;
        List<String> lines;
        try {
            originalText = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            String[] parts = originalText.split("\r\n|\r|\n");
            lines = new ArrayList<>(Arrays.asList(parts));
            while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
                lines.remove(lines.size() - 1); // убираем хвостовую пустую строку
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[config-repair] Не удалось прочитать config.yml: " + e.getMessage());
            return;
        }

        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in == null) return;
            defaults.loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            plugin.getLogger().warning("[config-repair] Не удалось прочитать дефолтный config.yml: " + e.getMessage());
            return;
        }

        try {
            List<String> deduped = removeDuplicates(lines);
            int removedLines = lines.size() - deduped.size();

            List<String> result = new ArrayList<>(deduped);
            int addedKeys = addMissingKeys(result, defaults);

            if (removedLines > 0 || addedKeys > 0) {
                // Страховка: итог обязан быть валидным YAML, иначе файл не трогаем
                YamlConfiguration check = new YamlConfiguration();
                check.loadFromString(String.join("\n", result));

                String eol = originalText.contains("\r\n") ? "\r\n" : "\n";
                Files.write(file.toPath(),
                        (String.join(eol, result) + eol).getBytes(StandardCharsets.UTF_8));

                plugin.getLogger().info("[config-repair] config.yml исправлен: добавлено "
                        + addedKeys + " недостающих ключей, удалено дубликатов на " + removedLines + " строк.");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[config-repair] Ошибка проверки config.yml (файл не изменён): " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════
    // ДУБЛИКАТЫ
    // ════════════════════════════════════════════════════════════════

    /**
     * Удаляет ранние вхождения дублирующихся ключей (по полному пути), оставляя последнее.
     * Блок раннего вхождения (ключ + его дети) удаляется целиком.
     */
    private static List<String> removeDuplicates(List<String> lines) {
        List<KeyInfo> keys = scanKeys(lines);
        boolean[] remove = new boolean[lines.size()];
        Map<String, Integer> seen = new HashMap<>(); // path → индекс в списке ключей первого вхождения
        for (int i = 0; i < keys.size(); i++) {
            KeyInfo ki = keys.get(i);
            Integer first = seen.put(ki.path, i);
            if (first == null) continue; // первое вхождение

            // Дубликат: удаляем блок первого вхождения — до следующего ключа
            // с отступом <= отступа первого вхождения
            int start = keys.get(first).lineIndex;
            int end = nextKeyLE(keys, first + 1, keys.get(first).indent, lines.size());
            for (int j = start; j < end; j++) {
                remove[j] = true;
            }
        }

        List<String> out = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            if (!remove[i]) out.add(lines.get(i));
        }
        return out;
    }

    /** Индекс строки следующего ключа (в списке ключей начиная с {@code from}) с отступом {@code <= maxIndent}. */
    private static int nextKeyLE(List<KeyInfo> keys, int from, int maxIndent, int fallback) {
        for (int i = from; i < keys.size(); i++) {
            if (keys.get(i).indent <= maxIndent) {
                return keys.get(i).lineIndex;
            }
        }
        return fallback;
    }

    // ════════════════════════════════════════════════════════════════
    // НЕДОСТАЮЩИЕ КЛЮЧИ
    // ════════════════════════════════════════════════════════════════

    /**
     * Добавляет в {@code lines} недостающие ключи из дефолтов.
     *
     * @return количество добавленных ключей
     */
    private static int addMissingKeys(List<String> lines, YamlConfiguration defaults) {
        // Существующие пути в (дедуплицированном) файле
        List<KeyInfo> keys = scanKeys(lines);
        Map<String, KeyInfo> existing = new LinkedHashMap<>();
        for (KeyInfo ki : keys) {
            existing.put(ki.path, ki);
        }

        // Недостающие листья (скаляры/списки) из дефолтов, в порядке дефолтного файла
        List<String> missingLeaves = new ArrayList<>();
        for (String key : defaults.getKeys(true)) {
            if (defaults.get(key) instanceof ConfigurationSection) continue; // секции добавятся через детей
            if (!existing.containsKey(key)) {
                missingLeaves.add(key);
            }
        }
        if (missingLeaves.isEmpty()) return 0;

        // Группируем по самому глубокому существующему разделу-предку ("" = корень/конец файла)
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (String leaf : missingLeaves) {
            String group = deepestExistingSection(leaf, existing);
            groups.computeIfAbsent(group, k -> new ArrayList<>()).add(leaf);
        }

        int added = 0;

        // 1) Недостающие ключи внутри СУЩЕСТВУЮЩИХ секций — в конец своей секции.
        //    Индексы вставки вычислены до вставок; применяем с конца, чтобы они не сдвигались.
        TreeMap<Integer, List<String>> insertions = new TreeMap<>();
        for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
            String group = entry.getKey();
            if (group.isEmpty()) continue; // корневые — отдельно, в п.2
            List<String> block = buildBlock(defaults, group, entry.getValue(), existing);
            if (block.isEmpty()) continue;

            KeyInfo ki = existing.get(group);
            // Ищем следующий ключ с отступом <= отступа секции (конец её блока),
            // начиная ПОСЛЕ самой секции
            int index = nextKeyLE(keys, indexOfKey(keys, ki) + 1, ki.indent, lines.size());
            insertions.computeIfAbsent(index, k -> new ArrayList<>()).addAll(block);
        }
        for (Integer index : insertions.descendingKeySet()) {
            List<String> block = insertions.get(index);
            lines.addAll(index, block);
            added += countKeys(block);
        }

        // 2) Недостающие КОРНЕВЫЕ ключи/секции — в самый конец файла (после секционных вставок)
        List<String> rootLeaves = groups.get("");
        if (rootLeaves != null && !rootLeaves.isEmpty()) {
            List<String> block = buildBlock(defaults, "", rootLeaves, existing);
            if (!block.isEmpty()) {
                lines.addAll(lines.size(), block);
                added += countKeys(block);
            }
        }
        return added;
    }

    /** Сколько строк-ключей в блоке (пустая строка-отступ и маркер не считаются). */
    private static int countKeys(List<String> block) {
        int n = 0;
        for (String line : block) {
            if (KEY_LINE.matcher(line).matches()) n++;
        }
        return n;
    }

    private static int indexOfKey(List<KeyInfo> keys, KeyInfo target) {
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i) == target) return i;
        }
        return keys.size();
    }

    /** Самый глубокий существующий РАЗДЕЛ-предок пути (или "" — корень). */
    private static String deepestExistingSection(String path, Map<String, KeyInfo> existing) {
        int idx = path.length();
        while (true) {
            idx = path.lastIndexOf('.', idx - 1);
            if (idx < 0) return "";
            String ancestor = path.substring(0, idx);
            KeyInfo ki = existing.get(ancestor);
            if (ki != null && ki.section) {
                return ancestor;
            }
        }
    }

    /** Строит блок YAML-строк для группы недостающих ключей с нужным отступом. */
    private static List<String> buildBlock(YamlConfiguration defaults, String group,
                                           List<String> leaves, Map<String, KeyInfo> existing) {
        YamlConfiguration sub = new YamlConfiguration();
        for (String leaf : leaves) {
            String rel = group.isEmpty() ? leaf : leaf.substring(group.length() + 1);
            sub.set(rel, defaults.get(leaf));
        }
        String dumped = sub.saveToString().stripTrailing();

        int baseIndent = group.isEmpty() ? 0 : existing.get(group).indent + INDENT;

        List<String> out = new ArrayList<>();
        out.add(""); // пустая строка-отступ перед блоком
        out.add(MARKER);
        for (String line : dumped.split("\n")) {
            out.add(line.isEmpty() ? "" : " ".repeat(baseIndent) + line);
        }
        return out;
    }

    // ════════════════════════════════════════════════════════════════
    // РАЗБОР СТРОК
    // ════════════════════════════════════════════════════════════════

    /**
     * Сканирует строки и возвращает ключи с ПОЛНЫМИ путями (a.b.c), построенными
     * по отступам: путь ключа = путь ближайшего предыдущего ключа с меньшим отступом
     * + имя ключа.
     */
    private static List<KeyInfo> scanKeys(List<String> lines) {
        List<KeyInfo> result = new ArrayList<>();
        Deque<KeyInfo> stack = new ArrayDeque<>();
        for (int i = 0; i < lines.size(); i++) {
            KeyName kn = parseKey(lines.get(i));
            if (kn == null) continue;
            while (!stack.isEmpty() && stack.peek().indent >= kn.indent) {
                stack.pop();
            }
            String parent = stack.isEmpty() ? "" : stack.peek().path;
            String path = parent.isEmpty() ? kn.name : parent + "." + kn.name;
            KeyInfo ki = new KeyInfo(path, kn.indent, kn.section, i);
            result.add(ki);
            stack.push(ki);
        }
        return result;
    }

    /**
     * Разбирает строку как YAML-ключ. Возвращает null для комментариев, пустых строк
     * и элементов списков.
     */
    private static KeyName parseKey(String line) {
        Matcher m = KEY_LINE.matcher(line);
        if (!m.matches()) return null;
        String rest = m.group(3).trim();
        boolean section = rest.isEmpty() || rest.startsWith("#");
        return new KeyName(m.group(2), m.group(1).length(), section);
    }
}
