package com.cubixsmp.listeners;

import com.cubixsmp.CubixSMP;
import com.cubixsmp.CustomEnchantManager;
import com.cubixsmp.CustomEnchantType;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Крафт спец. зачарований на верстаке:
 * {@code инструмент + спец. книга (PDC) = инструмент с PDC и названием чара в описании}.
 *
 * <p>Ванильного рецепта нет: результат подставляется в
 * {@link PrepareItemCraftEvent}, а в {@link CraftItemEvent} расход предметов
 * выполняется вручную (сервер не знает рецепт и сам ничего не спишет).</p>
 */
public class CustomEnchantCraftListener implements Listener {

    private final CubixSMP plugin;

    public CustomEnchantCraftListener(CubixSMP plugin) {
        this.plugin = plugin;
    }

    private CustomEnchantManager enchants() {
        return plugin.getCustomEnchantManager();
    }

    /**
     * Показываем результат «крафта» в слоте результата верстака.
     * Ванильные рецепты не трогаем; пустые (устаревшие) результаты чистим.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!plugin.getConfig().getBoolean("custom-enchants.enabled", true)) return;
        if (event.getRecipe() != null) return; // есть ванильный рецепт — не трогаем
        event.getInventory().setResult(computeResult(event.getInventory().getMatrix()));
    }

    /**
     * Забираем результат. Ванильного рецепта нет, поэтому расход предметов
     * матрицы выполняем вручную.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraft(CraftItemEvent event) {
        if (!plugin.getConfig().getBoolean("custom-enchants.enabled", true)) return;
        if (event.getRecipe() != null) return; // ванильный рецепт — мимо
        if (event.isCancelled()) return;

        CraftingInventory inv = event.getInventory();
        ItemStack result = inv.getResult();
        if (result == null || enchants().getAppliedType(result) == null) return;

        Player player = (Player) event.getWhoClicked();
        int crafted = 0;

        if (event.isShiftClick()) {
            // Shift-клик: крафтим максимально возможное количество.
            // Отменяем только если реально крафтим — иначе клик просто игнорируется
            // (ванильного рецепта нет, сервер ничего не сделает).
            while (computeResult(inv.getMatrix()) != null && canAdd(player.getInventory(), result)) {
                event.setCancelled(true);
                consumeIngredients(inv);
                player.getInventory().addItem(result.clone());
                crafted++;
            }
        } else {
            event.setCancelled(true);
            if (canAdd(player.getInventory(), result)) {
                consumeIngredients(inv);
                player.getInventory().addItem(result.clone());
                crafted = 1;
            } else if (event.getCursor() == null || event.getCursor().getType().isAir()) {
                // Инвентарь полон, но курсор пуст — кладём результат в курсор (как в ванили),
                // чтобы книга не дублировалась на сервере без рецепта
                consumeIngredients(inv);
                event.setCursor(result.clone());
                inv.setResult(null);
                player.updateInventory();
                return;
            } else {
                // Не влезает и курсор занят — ничего не забираем
                return;
            }
        }

        if (crafted > 0) {
            player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);
            inv.setResult(null);
            player.updateInventory();
        }
    }

    /**
     * Результат крафта по матрице: ровно одна спец. книга + ровно один предмет
     * (не книга). Иначе — null.
     */
    private ItemStack computeResult(ItemStack[] matrix) {
        ItemStack book = null;
        ItemStack tool = null;
        CustomEnchantType type = null;

        for (ItemStack slot : matrix) {
            if (slot == null || slot.getType().isAir()) continue;
            if (isEnchantBook(slot)) {
                if (book != null) return null; // две книги — нельзя
                book = slot;
                type = enchants().getAppliedType(slot);
            } else {
                if (tool != null) return null; // больше одного предмета — нельзя
                tool = slot;
            }
        }
        if (book == null || tool == null || type == null) return null;
        if (tool.getType() == Material.ENCHANTED_BOOK) return null; // книгу в книгу — нельзя
        if (enchants().has(tool, type)) return null; // чар уже есть — не дублируем

        ItemStack result = tool.clone();
        enchants().apply(result, type);
        return result;
    }

    /** Спец. книга: материал ENCHANTED_BOOK + PDC с нашим зачарованием. */
    private boolean isEnchantBook(ItemStack item) {
        return item.getType() == Material.ENCHANTED_BOOK && enchants().getAppliedType(item) != null;
    }

    /** Списываем по одному предмету из каждой непустой ячейки матрицы. */
    private void consumeIngredients(CraftingInventory inv) {
        for (ItemStack slot : inv.getMatrix()) {
            if (slot != null && !slot.getType().isAir()) {
                slot.setAmount(slot.getAmount() - 1);
            }
        }
    }

    /** Поместится ли предмет в инвентарь (пустая ячейка или неполный стак). */
    private boolean canAdd(PlayerInventory inv, ItemStack item) {
        for (ItemStack slot : inv.getStorageContents()) {
            if (slot == null || slot.getType().isAir()) return true;
            if (slot.isSimilar(item) && slot.getAmount() < slot.getMaxStackSize()) return true;
        }
        return false;
    }
}
