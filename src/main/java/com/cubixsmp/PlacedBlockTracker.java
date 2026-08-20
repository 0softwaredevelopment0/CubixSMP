package com.cubixsmp;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.Set;

/**
 * Трекер игроком-поставленных блоков (руды, брёвна, посевы).
 *
 * Использует Chunk#getPersistentDataContainer() — данные:
 *   - сохраняются в NBT чанка при авто-сейве мира,
 *   - выгружаются из ОЗУ вместе с чанком (нет утечек памяти),
 *   - мгновенно доступны через Bukkit API.
 *
 * На BlockBreakEvent слушатели проверяют {@link #wasPlacedByPlayer(Block)}.
 * Если блок был поставлен игроком — XP не начисляется.
 * Если блок НЕ в трекере (сгенерирован миром или поставлен до плагина) —
 *      срабатывает {@link NaturalCheck#isNaturalOre(org.bukkit.block.Block)} как fallback.
 */
public class PlacedBlockTracker implements Listener {

    private final CubixSMP plugin;
    private final NamespacedKey key;

    public PlacedBlockTracker(CubixSMP plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "placed_blocks");
    }

    // ─── Упаковка координат в int ─────────────────────────
    // Локальные X и Z чанка (0..15) + Y со смещением -64..512
    private static int pack(int localX, int localZ, int y) {
        int yOffset = y + 64;
        return ((localX & 0xF) << 24) | ((localZ & 0xF) << 16) | (yOffset & 0xFFFF);
    }

    /**
     * Какие материалы имеет смысл трекать.
     * Только руды, брёвна и культуры — то, за что реально выдаётся XP.
     */
    public boolean isTrackedMaterial(Material mat) {
        String name = mat.name();
        return name.endsWith("_ORE")
                || mat == Material.ANCIENT_DEBRIS
                || name.endsWith("_LOG")
                || name.endsWith("_WOOD")
                || name.endsWith("_STEM")
                || isCrop(mat);
        // DEEPSLATE_ORE варианты уже перехватываются _ORE выше;
        // ANCIENT_DEBRIS не заканчивается на _ORE — без него поставленный игроком
        // древний обломок не трекался, и MiningListener выдавал XP за его добычу
        // (NaturalCheck считает его натуральным из-за соседнего нэзеррака).
        // Без DEEPSLATE_BRICKS etc. — они не дают XP и не должны трекаться.
    }

    private boolean isCrop(Material mat) {
        // PUMPKIN/MELON ТРЕКАЮТСЯ: единственная проверка «плод натуральный?» —
        // wasPlacedByPlayer(сам_плод). Если игрок поставил плод напрямую через /setblock
        // или creative — BlockPlaceEvent зарегистрирует координату, и FarmingListener
        // откажет в XP. Если плод ВЫРОС из стебля (или сгенерирован миром) — события
        // размещения нет, проверка возвращает false → XP выдаётся.
        return switch (mat) {
            case WHEAT, CARROTS, POTATOES, BEETROOTS,
                 NETHER_WART, COCOA,
                 SWEET_BERRY_BUSH,
                 PUMPKIN, MELON,
                 PUMPKIN_STEM, MELON_STEM,
                 ATTACHED_MELON_STEM, ATTACHED_PUMPKIN_STEM
                    -> true;
            default -> false;
        };
    }

    /**
     * Проверка: блок был поставлен игроком?
     * Если true — НЕ натуральный, XP не начисляется.
     */
    public boolean wasPlacedByPlayer(Block block) {
        int packed = pack(block.getX() & 0xF, block.getZ() & 0xF, block.getY());
        return getTrackedSet(block.getChunk()).contains(packed);
    }

    public void track(Block block) {
        Chunk chunk = block.getChunk();
        Set<Integer> set = getTrackedSet(chunk);
        if (set.add(pack(block.getX() & 0xF, block.getZ() & 0xF, block.getY()))) {
            saveSet(chunk, set);
        }
    }

    public void untrack(Block block) {
        Chunk chunk = block.getChunk();
        Set<Integer> set = getTrackedSet(chunk);
        if (set.remove(pack(block.getX() & 0xF, block.getZ() & 0xF, block.getY()))) {
            saveSet(chunk, set);
        }
    }

    private Set<Integer> getTrackedSet(Chunk chunk) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        int[] arr = pdc.get(key, PersistentDataType.INTEGER_ARRAY);
        Set<Integer> set = new HashSet<>();
        if (arr != null) {
            for (int p : arr) set.add(p);
        }
        return set;
    }

    private void saveSet(Chunk chunk, Set<Integer> set) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        if (set.isEmpty()) {
            pdc.remove(key);
        } else {
            int[] arr = set.stream().mapToInt(Integer::intValue).toArray();
            pdc.set(key, PersistentDataType.INTEGER_ARRAY, arr);
        }
    }

    // ─── События ───────────────────────────────
    // MONITOR — самый низкий приоритет, чтобы все остальные слушатели (MiningListener и т.п.)
    // с приоритетом HIGHEST уже приняли решение, прежде чем мы уберём блок из трекера.

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (isTrackedMaterial(block.getType())) {
            track(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (isTrackedMaterial(block.getType())) {
            untrack(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        var dir = event.getDirection();
        for (Block block : event.getBlocks()) {
            if (wasPlacedByPlayer(block)) {
                untrack(block);
                Block newLoc = block.getRelative(dir);
                if (isTrackedMaterial(newLoc.getType())) {
                    track(newLoc);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        var dir = event.getDirection().getOppositeFace();
        for (Block block : event.getBlocks()) {
            if (wasPlacedByPlayer(block)) {
                untrack(block);
                Block newLoc = block.getRelative(dir);
                if (isTrackedMaterial(newLoc.getType())) {
                    track(newLoc);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            if (isTrackedMaterial(block.getType()) && wasPlacedByPlayer(block)) {
                untrack(block);
            }
        }
    }
}
