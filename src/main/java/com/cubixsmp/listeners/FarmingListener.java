package com.cubixsmp.listeners;

import com.cubixsmp.CubixSMP;
import com.cubixsmp.MessagesManager;
import com.cubixsmp.WorldSettings;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class FarmingListener implements Listener {

    private final CubixSMP plugin;

    public FarmingListener(CubixSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("farming.enabled", true)) return;

        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material type = block.getType();

        double xp = getXpForCrop(type);
        if (xp > 0) {
            if (block.getBlockData() instanceof Ageable ageable) {
                if (ageable.getAge() < ageable.getMaximumAge()) return;
            }
            // 🌱 Ageable-культуры (WHEAT, CARROTS, POTATOES, BEETROOTS) растут из семян
            // естественным путём — не проверяем wasPlacedByPlayer на самом блоке культуры,
            // иначе фермеры не получат XP за выращенный урожай.
            // Тыквы и арбузы обрабатываются ниже через проверку стебля.
            grantXp(player, xp, getCropName(type));
            return;
        }

        if (type == Material.PUMPKIN) {
            // 🐛 Фикс №2 (v26.2): пользователь жаловался что «проверка на натуральные блоки
            // тыкв не работает». Прежняя логика делала ОТКАЗ по двум причинам:
            //   (a) сам плод был поставлен игроком через /setblock
            //   (b) рядом стоит стебель, отмеченный как поставленный игроком
            // Причина (b) слишком строгая: плод, ВЫРОСШИЙ из посаженного игроком стебля
            // по-прежнему «вырос сам» (= «естественная генерация в рамках посева»), и по правилу
            // «выросла сама / была найдена / была сгенерирована → даём опыт» должен попадать
            // под положительный кейс. Поэтому теперь проверяем ТОЛЬКО прямой факт размещения
            // самого ПЛОДА игроком (то же поведение, что у пшеницы/моркови — те тоже не
            // проверяют wasPlacedByPlayer на самом блоке культуры).
            if (plugin.getPlacedBlockTracker().wasPlacedByPlayer(block)) {
                return;
            }
            grantXp(player, plugin.getConfig().getDouble("farming.crops.PUMPKIN", 1.0),
                    MessagesManager.getString("names.pumpkin", "Pumpkin"));
        } else if (type == Material.MELON) {
            // 🐛 Фикс №2: аналогично тыкве — проверяем только прямую постановку самого ПЛОДА.
            if (plugin.getPlacedBlockTracker().wasPlacedByPlayer(block)) {
                return;
            }
            grantXp(player, plugin.getConfig().getDouble("farming.crops.MELON", 1.0),
                    MessagesManager.getString("names.melon", "Melon"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBerryPick(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("farming.enabled", true)) return;
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Material type = event.getClickedBlock().getType();
        if (type == Material.SWEET_BERRY_BUSH) {
            if (event.getClickedBlock().getBlockData() instanceof Ageable ageable) {
                if (ageable.getAge() >= 2) {
                    // 🌱 Ягодный куст растёт из саженца — XP даётся за сбор урожая
                    grantXp(event.getPlayer(),
                            plugin.getConfig().getDouble("farming.crops.SWEET_BERRY_BUSH", 0.5),
                            MessagesManager.getString("names.berry", "Berries"));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDropItem(BlockDropItemEvent event) {
        if (!plugin.getConfig().getBoolean("farming.enabled", true)) return;
        Material type = event.getBlockState().getType();
        if (type != Material.BEE_NEST && type != Material.BEEHIVE) return;
        // Трекер: ульи, поставленные игроком, не дают XP
        if (plugin.getPlacedBlockTracker().wasPlacedByPlayer(event.getBlock())) {
            return;
        }
        for (var item : event.getItems()) {
            Material dropType = item.getItemStack().getType();
            if (dropType == Material.HONEY_BOTTLE)
                grantXp(event.getPlayer(),
                        plugin.getConfig().getDouble("farming.honey.HONEY_BOTTLE", 5.0),
                        MessagesManager.getString("names.honey_bottle", "Honey"));
            else if (dropType == Material.HONEYCOMB)
                grantXp(event.getPlayer(),
                        plugin.getConfig().getDouble("farming.honey.HONEYCOMB", 2.0),
                        MessagesManager.getString("names.honeycomb", "Honeycomb"));
        }
    }

    // (Удалён helper hasPlacedStemAdjacent — после #№2-фикса проверка соседних стеблей
    // не нужна: плод сам по себе натуральный, если напрямую не был размещён игроком.)

    private double getXpForCrop(Material mat) {
        return plugin.getConfig().getDouble("farming.crops." + mat.name(), 0);
    }

    private String getCropName(Material mat) {
        return switch (mat) {
            case WHEAT -> MessagesManager.getString("names.wheats", "Wheat");
            case CARROTS -> MessagesManager.getString("names.carrots", "Carrots");
            case POTATOES -> MessagesManager.getString("names.potatoes", "Potatoes");
            default -> {
                String name = mat.name().toLowerCase().replace('_', ' ');
                yield Character.toUpperCase(name.charAt(0)) + name.substring(1);
            }
        };
    }

    private void grantXp(Player player, double xp, String name) {
        if (xp <= 0) return;
        // 🌍 Пер-мировая настройка: в этом мире Cubix-XP не начисляется
        if (WorldSettings.isXpDisabled(plugin, player.getWorld())) return;
        plugin.getPlayerDataManager().addXp(player.getUniqueId(), xp, player);
        plugin.setLastAction(player.getUniqueId(), "Farming");
        String msg = MessagesManager.replace(
                MessagesManager.getString("xp.farming", "§7🌾 §a+{amount} XP §7({action})"),
                "amount", formatXp(xp), "action", name);
        if (plugin.getConfig().getBoolean("settings.use-actionbar", true)) {
            player.sendActionBar(net.kyori.adventure.text.Component.text(msg));
        } else {
            player.sendMessage(msg);
        }
    }

    private String formatXp(double xp) {
        if (xp == (long) xp) return String.valueOf((long) xp);
        return String.format("%.1f", xp);
    }
}
