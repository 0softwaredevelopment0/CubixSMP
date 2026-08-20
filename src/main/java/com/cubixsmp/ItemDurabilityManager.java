package com.cubixsmp;

import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Система целостности предметов: настоящая прочность хранится в PDC
 * (метки {@code cubixsmp:durability} — текущая, {@code cubixsmp:durability_max} — макс.),
 * а ванильная прочность предмета является лишь производным отображением.
 * <p>
 * Как это работает:
 * <ul>
 *   <li>Любая ванильная трата прочности (ломка блоков, атаки, броня, элитры)
 *       приходит через {@link PlayerItemDamageEvent} — событие отменяется,
 *       а урон наносится именно в PDC;</li>
 *   <li>ванильная полоска прочности выставляется в тот % от максимума,
 *       который получается из PDC (сначала пишем PDC, потом прочность предмета);</li>
 *   <li>в описании предмета показывается «Прочность: X/100»;</li>
 *   <li>починка на наковальне/точиле/кузнице синхронизируется обратно в PDC
 *       (ванильный результат пересчитывается в PDC-значение);</li>
 *   <li>при достижении 0 предмет ломается (звук + {@link PlayerItemBreakEvent}).</li>
 * </ul>
 * Код, который меняет прочность сам (например, «Бур»), должен пользоваться
 * {@link #applyDamage(Player, ItemStack, int)}, чтобы не нарушать синхронизацию.
 */
public class ItemDurabilityManager implements Listener {

    private final CubixSMP plugin;
    private final NamespacedKey keyCurrent;
    private final NamespacedKey keyMax;
    private final NamespacedKey keyLore;
    private final NamespacedKey keyWarned;

    public ItemDurabilityManager(CubixSMP plugin) {
        this.plugin = plugin;
        this.keyCurrent = new NamespacedKey(plugin, "durability");
        this.keyMax = new NamespacedKey(plugin, "durability_max");
        this.keyLore = new NamespacedKey(plugin, "durability_lore");
        this.keyWarned = new NamespacedKey(plugin, "durability_warned");
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("item-durability.enabled", true);
    }

    private boolean loreEnabled() {
        return plugin.getConfig().getBoolean("item-durability.lore-enabled", true);
    }

    // ─── Перехват ванильной траты прочности ─────────────────────────

    /**
     * Перехватывает ванильную трату прочности: событие отменяется, урон
     * наносится в PDC, ванильная прочность синхронизируется из PDC.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (!enabled()) return;
        ItemStack item = event.getItem();
        // Предмет уже сломан/пуст (например, «Бур» сломал его в BlockBreakEvent,
        // а центральный блок всё равно шлёт событие урона) — не тратим PDC повторно
        if (item.getAmount() <= 0) {
            event.setCancelled(true);
            return;
        }
        int vanillaMax = item.getType().getMaxDurability();
        if (vanillaMax <= 0) return;
        int amount = event.getDamage();
        if (amount <= 0) return;

        // 1) PDC: инициализация (если ещё нет) и списание урона
        int[] data = ensureInit(item, vanillaMax);
        if (data == null) return;

        int current = data[0] - amount;
        if (current <= 0) {
            writePdc(item, 0, data[1]);      // PDC первым
            event.setCancelled(true);
            breakItem(event.getPlayer(), item);
            return;
        }

        // 2) PDC первым, ванильная прочность потом
        writePdc(item, current, data[1]);
        syncVanilla(item, current, data[1]);
        checkWarnings(event.getPlayer(), item, current, data[1]);
        event.setCancelled(true);
    }

    // ─── Инициализация при взятии предмета в руку ───────────────────

    /**
     * При взятии предмета в руку: инициализирует PDC (если ещё нет) и
     * приводит ванильную прочность в соответствие с PDC.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHold(PlayerItemHeldEvent event) {
        if (!enabled()) return;
        ItemStack item = event.getPlayer().getInventory().getItem(event.getNewSlot());
        if (item == null || item.getType().isAir()) return;
        int vanillaMax = item.getType().getMaxDurability();
        if (vanillaMax <= 0) return;

        int[] data = ensureInit(item, vanillaMax);
        if (data == null) return;
        ensureLore(item, data[0], data[1]);
        syncVanilla(item, data[0], data[1]);
    }

    // ─── Синхронизация PDC после починки/кузницы ────────────────────

    /**
     * Наковальня: результат уже починен ванилью — отражаем новую прочность в PDC.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack result = event.getResult();
        if (resyncResult(result)) event.setResult(result);
    }

    /**
     * Точило: результат уже починен ванилью — отражаем новую прочность в PDC.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        ItemStack result = event.getResult();
        if (resyncResult(result)) event.setResult(result);
    }

    /**
     * Кузница: апгрейд сбрасывает ванильный урон — в PDC ставим полную прочность.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        ItemStack result = event.getResult();
        if (resyncResult(result)) event.setResult(result);
    }

    // ─── Публичный API ──────────────────────────────────────────────

    /**
     * Наносит {@code amount} прочности предмету через PDC (без ванильной траты).
     * Мутирует {@code item} на месте. Возвращает {@code false}, если предмет
     * сломался (его стек стал пустым).
     * <p>
     * Используется там, где прочность тратит сам плагин (например, «Бур»),
     * чтобы не нарушать синхронизацию PDC ↔ ванильная прочность.
     */
    public boolean applyDamage(Player player, ItemStack item, int amount) {
        if (item == null || item.getType().isAir() || amount <= 0) return true;
        int vanillaMax = item.getType().getMaxDurability();
        if (vanillaMax <= 0) return true;

        // Система выключена — ведём себя как ваниль (без PDC)
        if (!enabled()) {
            int damage = item.getDurability() + amount;
            if (damage >= vanillaMax) {
                breakItem(player, item);
                return false;
            }
            item.setDurability((short) damage);
            return true;
        }

        int[] data = ensureInit(item, vanillaMax);
        if (data == null) return true;

        int current = data[0] - amount;
        if (current <= 0) {
            writePdc(item, 0, data[1]);   // PDC первым
            breakItem(player, item);
            return false;
        }

        writePdc(item, current, data[1]); // PDC первым
        syncVanilla(item, current, data[1]);
        checkWarnings(player, item, current, data[1]);
        return true;
    }

    // ─── Внутренняя логика ──────────────────────────────────────────

    /**
     * Гарантирует наличие PDC на предмете. Возвращает {@code [current, max]}
     * или {@code null}, если предмет не должен отслеживаться (воздух, без
     * прочности, неубиваемый).
     */
    private int[] ensureInit(ItemStack item, int vanillaMax) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.isUnbreakable()) return null;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer current = pdc.get(keyCurrent, PersistentDataType.INTEGER);
        Integer max = pdc.get(keyMax, PersistentDataType.INTEGER);
        if (current != null && max != null && max > 0) {
            return new int[]{Math.min(current, max), max};
        }

        // Инициализация из ванильной прочности (0 урона = новая вещь).
        // Поношенный предмет (полученный уже потёртым) не должен сразу спамить
        // оповещения о пройденных порогах — отмечаем их как уже пройденные.
        int damage = item.getDurability();
        if (damage < 0) damage = 0;
        int initCurrent = Math.max(0, vanillaMax - damage);
        premarkWarned(meta, initCurrent, vanillaMax);

        writePdc(item, initCurrent, vanillaMax); // PDC первым
        return new int[]{initCurrent, vanillaMax};
    }

    /**
     * Пишет в PDC текущую/максимальную прочность и обновляет строку
     * «Прочность: X/100» в описании. Только PDC-часть меты предмета.
     */
    private void writePdc(ItemStack item, int current, int max) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyCurrent, PersistentDataType.INTEGER, current);
        pdc.set(keyMax, PersistentDataType.INTEGER, max);

        List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        String oldLine = pdc.get(keyLore, PersistentDataType.STRING);
        if (oldLine != null) {
            lore.remove(oldLine);
        }
        if (loreEnabled()) {
            String line = loreLine(current, max);
            lore.add(line);
            meta.setLore(lore);
            pdc.set(keyLore, PersistentDataType.STRING, line);
        } else {
            // Лор выключен: убираем нашу строку, но не трогаем чужой лор
            pdc.remove(keyLore);
            if (oldLine != null) {
                meta.setLore(lore);
            }
        }
        item.setItemMeta(meta);
    }

    /** Проверяет, что строка прочности есть в лоре (добавляет, если пропала). */
    private void ensureLore(ItemStack item, int current, int max) {
        if (!loreEnabled()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        String stored = meta.getPersistentDataContainer().get(keyLore, PersistentDataType.STRING);
        if (stored != null && meta.getLore() != null && meta.getLore().contains(stored)) {
            return; // уже на месте — ничего не пишем
        }
        writePdc(item, current, max);
    }

    /** Ставит ванильную прочность предмета в тот % от максимума, что в PDC. */
    private void syncVanilla(ItemStack item, int current, int max) {
        int vanillaMax = item.getType().getMaxDurability();
        if (vanillaMax <= 0) return;
        int target = (int) Math.round((double) current / max * vanillaMax);
        int damage = Math.max(0, vanillaMax - target);
        if (item.getDurability() != damage) {
            item.setDurability((short) damage);
        }
    }

    /**
     * Пересчитывает PDC из ванильного результата починки/апгрейда.
     * Возвращает {@code true}, если предмет был изменён.
     */
    private boolean resyncResult(ItemStack result) {
        if (!enabled() || result == null || result.getType().isAir()) return false;
        int vanillaMax = result.getType().getMaxDurability();
        if (vanillaMax <= 0) return false;
        ItemMeta meta = result.getItemMeta();
        if (meta == null || meta.isUnbreakable()) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer max = pdc.get(keyMax, PersistentDataType.INTEGER);
        Integer current = pdc.get(keyCurrent, PersistentDataType.INTEGER);
        if (current == null || max == null || max <= 0) return false;

        // Ваниль уже починила предмет — пропорционально отражаем в PDC.
        // Если материал изменился (кузница: алмаз → незерит), берём новый максимум.
        int newMax = vanillaMax != max ? vanillaMax : max;
        int vanillaDamage = result.getDurability();
        int newCurrent = (int) Math.round((double) Math.max(0, vanillaMax - vanillaDamage) / vanillaMax * newMax);
        newCurrent = Math.max(0, Math.min(newMax, newCurrent));
        if (newCurrent == current && newMax == max) return false;

        boolean repaired = newCurrent > current;
        writePdc(result, newCurrent, newMax); // PDC первым
        syncVanilla(result, newCurrent, newMax);
        if (repaired) {
            // Починили — сброс отметок порогов: новый цикл износа предупредит снова
            ItemMeta cleared = result.getItemMeta();
            if (cleared != null) {
                cleared.getPersistentDataContainer().remove(keyWarned);
                result.setItemMeta(cleared);
            }
        }
        return true;
    }

    // ─── Оповещения о прочности в чат ───────────────────────────────

    /**
     * Оповещает игрока в чат при достижении порогов прочности
     * (по умолчанию 75/50/25/10/5%). Каждый порог — один раз на предмет,
     * повторно не спамит. Сброс порогов — при починке предмета.
     */
    private void checkWarnings(Player player, ItemStack item, int current, int max) {
        if (player == null || !player.isOnline()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer warned = pdc.get(keyWarned, PersistentDataType.INTEGER);
        int mask = warned == null ? 0 : warned;
        int percent = percent(current, max);

        List<Integer> thresholds = thresholds();
        int newMask = mask;
        boolean changed = false;
        for (int i = 0; i < thresholds.size(); i++) {
            int bit = 1 << i;
            if ((mask & bit) != 0) continue; // этот порог уже был
            if (percent <= thresholds.get(i)) {
                newMask |= bit;
                changed = true;
                player.sendMessage(MessagesManager.format("durability.warn_format",
                        "§6⚒ §eПрочность предмета: §f{percent}% §7— скоро сломается!",
                        "percent", String.valueOf(percent)));
            }
        }
        if (changed) {
            pdc.set(keyWarned, PersistentDataType.INTEGER, newMask);
            item.setItemMeta(meta);
        }
    }

    /** Отмечает пороги, которые предмет уже прошёл (для инициализации поношенных вещей). */
    private void premarkWarned(ItemMeta meta, int current, int max) {
        int percent = percent(current, max);
        int mask = 0;
        List<Integer> thresholds = thresholds();
        for (int i = 0; i < thresholds.size(); i++) {
            if (percent <= thresholds.get(i)) {
                mask |= (1 << i);
            }
        }
        if (mask != 0) {
            meta.getPersistentDataContainer().set(keyWarned, PersistentDataType.INTEGER, mask);
        }
    }

    /** Пороги оповещений из конфига (1-100), отсортированные по убыванию, без дублей. */
    private List<Integer> thresholds() {
        List<Integer> list = new ArrayList<>();
        for (int t : plugin.getConfig().getIntegerList("item-durability.warn-thresholds")) {
            if (t >= 1 && t <= 100 && !list.contains(t)) list.add(t);
        }
        list.sort(java.util.Collections.reverseOrder());
        return list;
    }

    /** Прочность в процентах (0-100), округлённая. */
    private int percent(int current, int max) {
        if (max <= 0) return 100;
        int p = (int) Math.round((double) current / max * 100);
        return Math.max(0, Math.min(100, p));
    }

    /** Строка лора: «Прочность: X/100». */
    private String loreLine(int current, int max) {
        String fmt = MessagesManager.getString("durability.lore_format", "§7Прочность: §f{percent}§7/100");
        return fmt.replace("{percent}", String.valueOf(percent(current, max)))
                .replace("{current}", String.valueOf(current))
                .replace("{max}", String.valueOf(max));
    }

    /** Ломает предмет: событие поломки, звук, очистка стека. */
    private void breakItem(Player player, ItemStack item) {
        PlayerItemBreakEvent breakEvent = new PlayerItemBreakEvent(player, item);
        plugin.getServer().getPluginManager().callEvent(breakEvent);
        item.setAmount(0);
        if (player != null && player.isOnline()) {
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
        }
    }
}
