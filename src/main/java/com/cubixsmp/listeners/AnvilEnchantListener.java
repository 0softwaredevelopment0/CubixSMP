package com.cubixsmp.listeners;

import com.cubixsmp.CubixSMP;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;

/**
 * Запрещает получать зачарования выше ванильного максимума при объединении
 * предметов в наковальне.
 *
 * <p>Например, две книги «Эффективность V» не дадут «Эффективность VI» —
 * такие зачарования можно получить только за донат-валюту.</p>
 *
 * <p>Настройки: секция {@code anvil} в config.yml.</p>
 */
public class AnvilEnchantListener implements Listener {

    private final CubixSMP plugin;

    public AnvilEnchantListener(CubixSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!plugin.getConfig().getBoolean("anvil.enabled", true)) return;

        ItemStack result = event.getResult();
        if (result == null || result.getType() == Material.AIR) return;

        // Ограничение пропускается только тогда, когда один из входов несёт
        // зачарование ВЫШЕ ванильного максимума — такие предметы можно получить
        // только за донат-валюту, их применение/объединение не трогаем.
        // Во всех остальных случаях (обычные книги и/или предметы) результат
        // обрезается до ванильного максимума:
        //   • две книги «Удача III» не дадут книгу с «Удачей IV»;
        //   • две кирки с «Удачей III» не дадут кирку с «Удачей IV»;
        //   • книга «Удача III» + кирка с «Удачей III» не дадут «Удачу IV».
        ItemStack first = event.getView().getItem(0);
        ItemStack second = event.getView().getItem(1);
        if (first == null || second == null) return;
        if (hasAboveVanillaMax(first) || hasAboveVanillaMax(second)) return;

        ItemStack capped = capEnchantments(result);
        if (!capped.equals(result)) {
            event.setResult(capped);
        }
    }

    /**
     * Возвращает копию предмета, у которой все зачарования обрезаны
     * до ванильного максимального уровня.
     */
    private ItemStack capEnchantments(ItemStack item) {
        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) return copy;

        Map<Enchantment, Integer> enchants;
        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            enchants = storageMeta.getStoredEnchants();
        } else {
            enchants = meta.getEnchants();
        }

        Map<Enchantment, Integer> capped = new HashMap<>();
        boolean changed = false;
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            int max = getVanillaMaxLevel(entry.getKey());
            int level = Math.min(entry.getValue(), max);
            if (level != entry.getValue()) changed = true;
            capped.put(entry.getKey(), level);
        }

        if (!changed) return copy;

        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            for (Enchantment enchantment : storageMeta.getStoredEnchants().keySet()) {
                storageMeta.removeStoredEnchant(enchantment);
            }
            for (Map.Entry<Enchantment, Integer> entry : capped.entrySet()) {
                storageMeta.addStoredEnchant(entry.getKey(), entry.getValue(), true);
            }
        } else {
            for (Enchantment enchantment : meta.getEnchants().keySet()) {
                meta.removeEnchant(enchantment);
            }
            for (Map.Entry<Enchantment, Integer> entry : capped.entrySet()) {
                meta.addEnchant(entry.getKey(), entry.getValue(), true);
            }
        }
        copy.setItemMeta(meta);
        return copy;
    }

    /**
     * Есть ли у предмета зачарование выше ванильного максимума
     * (признак донат-предмета).
     */
    private boolean hasAboveVanillaMax(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        Map<Enchantment, Integer> enchants;
        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            enchants = storageMeta.getStoredEnchants();
        } else {
            enchants = meta.getEnchants();
        }

        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            if (entry.getValue() > getVanillaMaxLevel(entry.getKey())) return true;
        }
        return false;
    }

    private int getVanillaMaxLevel(Enchantment enchantment) {
        return VANILLA_MAX_LEVELS.getOrDefault(enchantment.getKey().getKey(), enchantment.getMaxLevel());
    }

    /** Ванильные максимальные уровни зачарований (ключ — id зачарования). */
    private static final Map<String, Integer> VANILLA_MAX_LEVELS = new HashMap<>();
    static {
        // Защита
        VANILLA_MAX_LEVELS.put("protection", 4);
        VANILLA_MAX_LEVELS.put("fire_protection", 4);
        VANILLA_MAX_LEVELS.put("feather_falling", 4);
        VANILLA_MAX_LEVELS.put("blast_protection", 4);
        VANILLA_MAX_LEVELS.put("projectile_protection", 4);
        VANILLA_MAX_LEVELS.put("respiration", 3);
        VANILLA_MAX_LEVELS.put("aqua_affinity", 1);
        VANILLA_MAX_LEVELS.put("thorns", 3);
        VANILLA_MAX_LEVELS.put("depth_strider", 3);
        VANILLA_MAX_LEVELS.put("frost_walker", 2);
        VANILLA_MAX_LEVELS.put("binding_curse", 1);
        VANILLA_MAX_LEVELS.put("soul_speed", 3);
        VANILLA_MAX_LEVELS.put("swift_sneak", 3);

        // Оружие
        VANILLA_MAX_LEVELS.put("sharpness", 5);
        VANILLA_MAX_LEVELS.put("smite", 5);
        VANILLA_MAX_LEVELS.put("bane_of_arthropods", 5);
        VANILLA_MAX_LEVELS.put("knockback", 2);
        VANILLA_MAX_LEVELS.put("fire_aspect", 2);
        VANILLA_MAX_LEVELS.put("looting", 3);
        VANILLA_MAX_LEVELS.put("sweeping_edge", 3);

        // Инструменты
        VANILLA_MAX_LEVELS.put("efficiency", 5);
        VANILLA_MAX_LEVELS.put("silk_touch", 1);
        VANILLA_MAX_LEVELS.put("unbreaking", 3);
        VANILLA_MAX_LEVELS.put("fortune", 3);

        // Лук
        VANILLA_MAX_LEVELS.put("power", 5);
        VANILLA_MAX_LEVELS.put("punch", 2);
        VANILLA_MAX_LEVELS.put("flame", 1);
        VANILLA_MAX_LEVELS.put("infinity", 1);

        // Удочка
        VANILLA_MAX_LEVELS.put("luck_of_the_sea", 3);
        VANILLA_MAX_LEVELS.put("lure", 3);

        // Трезубец
        VANILLA_MAX_LEVELS.put("loyalty", 3);
        VANILLA_MAX_LEVELS.put("impaling", 5);
        VANILLA_MAX_LEVELS.put("riptide", 3);
        VANILLA_MAX_LEVELS.put("channeling", 1);

        // Арбалет
        VANILLA_MAX_LEVELS.put("multishot", 1);
        VANILLA_MAX_LEVELS.put("quick_charge", 3);
        VANILLA_MAX_LEVELS.put("piercing", 4);

        // Особые
        VANILLA_MAX_LEVELS.put("mending", 1);
        VANILLA_MAX_LEVELS.put("vanishing_curse", 1);

        // 1.21+
        VANILLA_MAX_LEVELS.put("wind_burst", 3);
        VANILLA_MAX_LEVELS.put("density", 5);
        VANILLA_MAX_LEVELS.put("breach", 4);
    }
}
