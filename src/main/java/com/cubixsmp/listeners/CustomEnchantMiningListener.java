package com.cubixsmp.listeners;

import com.cubixsmp.CubixSMP;
import com.cubixsmp.CustomEnchantManager;
import com.cubixsmp.CustomEnchantType;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Эффекты спец. зачарований при добыче блоков:
 * <ul>
 *   <li><b>Бур</b> — при ломании блока дополнительно ломается область 3×3
 *       в плоскости, перпендикулярной взгляду игрока
 *       (смотрит вниз/вверх — горизонтальная плоскость, влево/вправо/прямо —
 *       вертикальная);</li>
 *   <li><b>Автопереплавка</b> — железная, медная, золотая руда и древние
 *       обломки выпадают сразу переплавленными (слитки / незеритовый скрап).</li>
 * </ul>
 */
public class CustomEnchantMiningListener implements Listener {

    private final CubixSMP plugin;

    /** Блок → что выпадает при «переплавке». */
    private static final Map<Material, Material> SMELTED = new LinkedHashMap<>();
    static {
        SMELTED.put(Material.IRON_ORE, Material.IRON_INGOT);
        SMELTED.put(Material.DEEPSLATE_IRON_ORE, Material.IRON_INGOT);
        SMELTED.put(Material.GOLD_ORE, Material.GOLD_INGOT);
        SMELTED.put(Material.DEEPSLATE_GOLD_ORE, Material.GOLD_INGOT);
        SMELTED.put(Material.COPPER_ORE, Material.COPPER_INGOT);
        SMELTED.put(Material.DEEPSLATE_COPPER_ORE, Material.COPPER_INGOT);
        SMELTED.put(Material.ANCIENT_DEBRIS, Material.NETHERITE_SCRAP);
    }

    /** Защита от рекурсии: true пока бурим область (синтетические события блоков области). */
    private boolean drilling = false;

    public CustomEnchantMiningListener(CubixSMP plugin) {
        this.plugin = plugin;
    }

    private CustomEnchantManager enchants() {
        return plugin.getCustomEnchantManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (drilling) return; // вложенные события блоков области 3×3 — повторно не бурим
        if (!plugin.getConfig().getBoolean("custom-enchants.enabled", true)) return;

        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || tool.getType().isAir()) return;

        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        boolean silkTouch = tool.containsEnchantment(Enchantment.SILK_TOUCH);
        boolean bur = enchants().has(tool, CustomEnchantType.BUR)
                && plugin.getConfig().getBoolean("custom-enchants.bur.enabled", true);
        boolean autosmelt = enchants().has(tool, CustomEnchantType.AUTOSMELT)
                && plugin.getConfig().getBoolean("custom-enchants.autosmelt.enabled", true);

        // Автопереплавка: сам сломанный блок — дроп заменяем переплавленным
        if (autosmelt && !creative && !silkTouch) {
            handleAutosmelt(event, tool);
        }

        // Бур: дополнительно ломаем область 3×3 в плоскости взгляда
        if (bur) {
            handleDrill(event, player, tool, creative, autosmelt && !creative && !silkTouch);
        }
    }

    // ─── Автопереплавка ─────────────────────────────────────────────

    private void handleAutosmelt(BlockBreakEvent event, ItemStack tool) {
        Block block = event.getBlock();
        Material smelted = SMELTED.get(block.getType());
        if (smelted == null) return;

        // Древние обломки уже превращаются в скрап в MiningListener
        // (debris-always-scrap) — не дублируем дроп
        if (block.getType() == Material.ANCIENT_DEBRIS
                && plugin.getConfig().getBoolean("mining.debris-always-scrap", true)) {
            return;
        }
        // Инструментом эту руду не добыть (не та кирка) — оставляем ванильное поведение
        if (!block.isPreferredTool(tool)) return;

        event.setDropItems(false);
        for (ItemStack drop : block.getDrops(tool)) {
            if (drop == null || drop.getType().isAir()) continue;
            block.getWorld().dropItemNaturally(
                    block.getLocation().add(0.5, 0.5, 0.5),
                    new ItemStack(smelted, drop.getAmount()));
        }
    }

    // ─── Бур ────────────────────────────────────────────────────────

    private void handleDrill(BlockBreakEvent event, Player player, ItemStack tool, boolean creative, boolean autosmelt) {
        Block center = event.getBlock();
        boolean respectPlaced = plugin.getConfig().getBoolean("custom-enchants.bur.respect-placed-blocks", true);
        boolean consumeDurability = plugin.getConfig().getBoolean("custom-enchants.bur.consume-durability", true);

        int broken = 0;
        drilling = true;
        try {
            for (int[] off : drillOffsets(player)) {
                Block b = center.getRelative(off[0], off[1], off[2]);
                Material type = b.getType();

                // Воздух, жидкости и неразрушаемые блоки — мимо
                if (type.isAir() || !type.isItem() || type.getHardness() < 0) continue;
                // Инструмент не предназначен для этого блока (не та кирка/топор и т.п.)
                if (!b.isPreferredTool(tool)) continue;
                // Блоки, поставленные игроками, не ломаем (защита построек)
                if (respectPlaced && plugin.getPlacedBlockTracker().wasPlacedByPlayer(b)) continue;

                // Синтетическое событие — плагины защиты (WorldGuard и т.п.) тоже видят область
                BlockBreakEvent sub = new BlockBreakEvent(b, player);
                plugin.getServer().getPluginManager().callEvent(sub);
                if (sub.isCancelled()) continue;

                // Прочность: 1 за каждый блок области (в дополнение к центральному).
                // Ванильная трата прочности (setDurability), поломка инструмента
                // завершает проход по области.
                if (consumeDurability && !creative) {
                    int damage = tool.getDurability() + 1;
                    if (damage >= tool.getType().getMaxDurability()) {
                        tool.setDurability((short) tool.getType().getMaxDurability());
                        break; // инструмент сломался — прекращаем
                    }
                    tool.setDurability((short) damage);
                }

                breakBlock(b, tool, autosmelt, creative);
                broken++;
            }
        } finally {
            drilling = false;
        }

        if (broken > 0 && plugin.getConfig().getBoolean("custom-enchants.bur.play-sound", true)) {
            player.playSound(center.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_STONE_BREAK, 0.6f, 0.8f);
        }
    }

    /**
     * Ломает блок области. С автопереплавкой дроп заменяется слитками,
     * для древних обломков скрап уже выпал из синтетического события
     * (MiningListener) — блок просто убираем.
     * В креативе блок убирается без дропа (как в ванили).
     */
    private void breakBlock(Block block, ItemStack tool, boolean autosmelt, boolean creative) {
        Material type = block.getType();

        // В креативе дропа нет — просто убираем блок (breakNaturally выкидывал бы предметы)
        if (creative) {
            block.setType(Material.AIR, false);
            return;
        }

        // Порядок важен: для древних обломков скрап уже выпал из синтетического
        // события (MiningListener, debris-always-scrap) — повторно не дропаем
        boolean debrisHandled = type == Material.ANCIENT_DEBRIS
                && plugin.getConfig().getBoolean("mining.debris-always-scrap", true);
        Material smelted = autosmelt && !debrisHandled ? SMELTED.get(type) : null;

        if (smelted != null && block.isPreferredTool(tool)) {
            Collection<ItemStack> drops = block.getDrops(tool);
            block.setType(Material.AIR, false);
            for (ItemStack drop : drops) {
                if (drop == null || drop.getType().isAir()) continue;
                block.getWorld().dropItemNaturally(
                        block.getLocation().add(0.5, 0.5, 0.5),
                        new ItemStack(smelted, drop.getAmount()));
            }
        } else if (debrisHandled) {
            block.setType(Material.AIR, false);
        } else {
            block.breakNaturally(tool);
        }
    }

    /**
     * Смещения 8 соседних блоков области 3×3 (без центра) в плоскости,
     * перпендикулярной направлению взгляда:
     * <ul>
     *   <li>смотрим вверх/вниз — горизонтальная плоскость (меняются X и Z);</li>
     *   <li>смотрим вдоль X — вертикальная плоскость YZ;</li>
     *   <li>смотрим вдоль Z — вертикальная плоскость XY.</li>
     * </ul>
     */
    private int[][] drillOffsets(Player player) {
        Vector dir = player.getEyeLocation().getDirection();
        double ax = Math.abs(dir.getX());
        double ay = Math.abs(dir.getY());
        double az = Math.abs(dir.getZ());

        if (ay >= ax && ay >= az) {
            // Плоскость XZ (Y не меняется)
            return new int[][] {
                    {-1, -1, 0}, {0, -1, 0}, {1, -1, 0},
                    {-1, 0, 0}, {1, 0, 0},
                    {-1, 1, 0}, {0, 1, 0}, {1, 1, 0}
            };
        }
        if (ax >= az) {
            // Плоскость YZ (X не меняется)
            return new int[][] {
                    {0, -1, -1}, {0, -1, 0}, {0, -1, 1},
                    {0, 0, -1}, {0, 0, 1},
                    {0, 1, -1}, {0, 1, 0}, {0, 1, 1}
            };
        }
        // Плоскость XY (Z не меняется)
        return new int[][] {
                {-1, -1, 0}, {-1, 0, 0}, {-1, 1, 0},
                {0, -1, 0}, {0, 1, 0},
                {1, -1, 0}, {1, 0, 0}, {1, 1, 0}
        };
    }
}
