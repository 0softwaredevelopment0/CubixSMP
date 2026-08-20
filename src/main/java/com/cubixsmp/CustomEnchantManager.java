package com.cubixsmp;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Менеджер спец. зачарований CubixSMP («Бур», «Автопереплавка»).
 *
 * <p>Зачарование — это метка в {@code PersistentDataContainer} предмета
 * (PDC) + название чара в описании (лоре). Никаких ванильных зачарований
 * не добавляется, поэтому наковальня ({@code AnvilEnchantListener}) их не трогает.</p>
 *
 * <p>Спец. книга создаётся {@link #createBook(CustomEnchantType)} (выдача командой),
 * перенос чара на инструмент — {@link #apply(ItemStack, CustomEnchantType)}
 * (крафт на верстаке, см. {@code CustomEnchantCraftListener}).</p>
 */
public final class CustomEnchantManager {

    private final CubixSMP plugin;
    private final Map<CustomEnchantType, NamespacedKey> keys = new EnumMap<>(CustomEnchantType.class);

    public CustomEnchantManager(CubixSMP plugin) {
        this.plugin = plugin;
        for (CustomEnchantType type : CustomEnchantType.values()) {
            keys.put(type, new NamespacedKey(plugin, "enchant_" + type.getId()));
        }
    }

    /** Есть ли зачарование на предмете (уровень ≥ 1). */
    public boolean has(ItemStack item, CustomEnchantType type) {
        return getLevel(item, type) >= 1;
    }

    /** Уровень зачарования (0 — нет). */
    public int getLevel(ItemStack item, CustomEnchantType type) {
        if (item == null || item.getItemMeta() == null) return 0;
        Integer level = item.getItemMeta().getPersistentDataContainer()
                .get(keys.get(type), PersistentDataType.INTEGER);
        return level == null ? 0 : level;
    }

    /** Какое из наших зачарований несёт предмет (или null). */
    public CustomEnchantType getAppliedType(ItemStack item) {
        if (item == null) return null;
        for (CustomEnchantType type : CustomEnchantType.values()) {
            if (has(item, type)) return type;
        }
        return null;
    }

    /**
     * Спец. книга с этим зачарованием (материал — зачарованная книга).
     * Название чара подставляется в лор книги ({enchant}).
     */
    public ItemStack createBook(CustomEnchantType type) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        if (meta == null) return book;

        meta.setDisplayName(plugin.getConfig().getString("custom-enchants.book.name", "§d§lЗачарованная книга"));

        List<String> lore = new ArrayList<>(plugin.getConfig().getStringList("custom-enchants.book.lore"));
        if (lore.isEmpty()) {
            lore.add("§7Содержит зачарование: §f" + displayName(type));
        }
        for (int i = 0; i < lore.size(); i++) {
            lore.set(i, lore.get(i)
                    .replace("{enchant}", displayName(type))
                    .replace("{description}", description(type)));
        }
        meta.setLore(lore);

        meta.getPersistentDataContainer().set(keys.get(type), PersistentDataType.INTEGER, 1);
        book.setItemMeta(meta);
        return book;
    }

    /**
     * Применяет зачарование к предмету: PDC + название чара в описании (лор).
     * Если строка чара уже есть — не дублируется.
     */
    public void apply(ItemStack item, CustomEnchantType type) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        meta.getPersistentDataContainer().set(keys.get(type), PersistentDataType.INTEGER, 1);

        String line = loreLine(type);
        List<String> lore = meta.getLore();
        if (lore == null) {
            lore = new ArrayList<>();
        }
        if (!lore.contains(line)) {
            lore.add(line);
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    /** Название чара (из config.yml, fallback — дефолт enum'а). */
    public String displayName(CustomEnchantType type) {
        return plugin.getConfig().getString("custom-enchants." + type.getId() + ".name",
                type.getDisplayName());
    }

    /** Строка в лоре инструмента (название чара), по умолчанию «§d<Название>». */
    public String loreLine(CustomEnchantType type) {
        return plugin.getConfig().getString("custom-enchants." + type.getId() + ".lore-line",
                "§d" + type.getDisplayName());
    }

    /** Описание чара (для лора книги). */
    public String description(CustomEnchantType type) {
        return plugin.getConfig().getString("custom-enchants." + type.getId() + ".description", "");
    }
}
