package com.cubixsmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import java.util.UUID;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import static com.cubixsmp.PlayerDataManager.formatXp;
import java.util.List;

public class CubixSMPCommand implements CommandExecutor {

    private final CubixSMP plugin;

    public CubixSMPCommand(CubixSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MessagesManager.getString("general.player_only", "§c❌ Only players can use this command!"));
                return true;
            }
            showStats(player);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "stats" -> handleStats(sender);
            case "particle" -> handleParticle(sender, args);
            case "admin" -> handleAdmin(sender, args);
            case "sound" -> handleSound(sender);
            case "leaders" -> handleLeaders(sender);
            case "ping" -> handlePing(sender);
            case "checkonline" -> handleCheckOnline(sender, args);
            case "action" -> handleAction(sender, args);
            case "help" -> handleHelp(sender, args);
            default -> handleHelp(sender, args);
        };
    }

    // ─── Обработчики команд ─────────────────────

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("cubixsmp.reload")) {
            sender.sendMessage(MessagesManager.getString("general.no_permission", "§c❌ You don't have permission!"));
            return true;
        }
        // 🔧 Проверка и починка config.yml (недостающие ключи → в конец, дубликаты удаляются)
        ConfigRepair.repair(plugin);
        plugin.reloadConfig();
        MessagesManager.reload();
        plugin.getLevelManager().reload();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            plugin.getPlayerDataManager().syncToManagers(p.getUniqueId());
        }
        plugin.getPlayerDataManager().writeUidListFile();
        plugin.getParticleTrailManager().reload(); // перечитываем particles.* из конфига
        sender.sendMessage(MessagesManager.getString("general.config_reloaded", "§a✔ Configuration reloaded!"));
        return true;
    }

    private boolean handleStats(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessagesManager.getString("general.player_only", "§c❌ Only players can use this command!"));
            return true;
        }
        showStats(player);
        return true;
    }

    /**
     * /csmp particle <имя> — персональный трейл частиц вокруг игрока.
     * /csmp particle off — выключить. /csmp particle list — доступные частицы.
     */
    private boolean handleParticle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessagesManager.getString("general.player_only", "§c❌ Only players can use this command!"));
            return true;
        }
        if (!player.hasPermission("cubixsmp.particle")) {
            sender.sendMessage(MessagesManager.getString("general.no_permission", "§c❌ You don't have permission!"));
            return true;
        }

        // Без аргументов — показываем текущий трейл (или подсказку)
        if (args.length < 2) {
            String current = plugin.getPlayerDataManager().getParticle(player.getUniqueId());
            if (current.isEmpty()) {
                player.sendMessage(MessagesManager.getString("particle.usage",
                        "§e/csmp particle <имя> §7— трейл частиц, §e/csmp particle off §7— выключить"));
            } else {
                player.sendMessage(MessagesManager.format("particle.current",
                        "§7Текущий трейл: §e{particle}§7. §e/csmp particle off §7— выключить",
                        "particle", current));
            }
            return true;
        }

        String sub = args[1];
        if (sub.equalsIgnoreCase("off")) {
            plugin.getPlayerDataManager().setParticle(player.getUniqueId(), "");
            player.sendMessage(MessagesManager.getString("particle.off", "§7✔ Партикл-трейл выключен"));
            return true;
        }

        if (sub.equalsIgnoreCase("list")) {
            showParticleList(player);
            return true;
        }

        // Проверяем, что частица существует
        Particle particle = ParticleTrailManager.resolve(sub);
        if (particle == null) {
            player.sendMessage(MessagesManager.format("particle.invalid",
                    "§c❌ Частица §e{particle} §cне найдена!", "particle", sub));
            return true;
        }

        // Частицы, требующие данные (dust, block, item и т.п.), трейлом быть не могут
        if (particle.getDataType() != Void.class) {
            player.sendMessage(MessagesManager.format("particle.error_requires_data",
                    "§c❌ Эта частица требует специальные данные и не может быть трейлом!",
                    "particle", sub));
            return true;
        }

        // Проверка списка разрешённых частиц (particles.allowed в конфиге)
        String norm = ParticleTrailManager.normalize(sub);
        List<String> allowed = plugin.getConfig().getStringList("particles.allowed");
        boolean inList = false;
        for (String a : allowed) {
            if (ParticleTrailManager.normalize(a).equals(norm)) {
                inList = true;
                break;
            }
        }
        if (!allowed.isEmpty() && !inList && !player.hasPermission("cubixsmp.particle.any")) {
            player.sendMessage(MessagesManager.format("particle.not_allowed",
                    "§c❌ Частица §e{particle} §cнедоступна! Используйте §e/csmp particle list",
                    "particle", sub));
            return true;
        }

        plugin.getPlayerDataManager().setParticle(player.getUniqueId(), norm);
        player.sendMessage(MessagesManager.format("particle.on",
                "§a✔ Партикл-трейл включён: §e{particle}", "particle", norm));
        return true;
    }

    /** Показывает список доступных игроку частиц. */
    private void showParticleList(Player player) {
        player.sendMessage(MessagesManager.getString("particle.list_header", "§6Доступные частицы:"));
        List<String> allowed = plugin.getConfig().getStringList("particles.allowed");
        if (allowed.isEmpty()) {
            player.sendMessage(MessagesManager.getString("particle.list_all",
                    "§7Разрешены все простые частицы (note, rain, end_rod, flame, heart...). §e/csmp particle <имя>"));
        } else {
            for (String a : allowed) {
                player.sendMessage("§7- §f" + a);
            }
        }
        player.sendMessage(MessagesManager.getString("particle.list_off", "§7/csmp particle off §7— выключить"));
    }

    /**
     * /csmp help [страница] — список всех команд с пагинацией.
     * Внизу — кнопки [<] [>] (кликабельные) и счётчик «страница/всего страниц».
     */
    private boolean handleHelp(CommandSender sender, String[] args) {
        List<String> commands;
        if (sender.hasPermission("cubixsmp.admin")) {
            commands = new java.util.ArrayList<>(MessagesManager.getStringList("command.help_admin",
                    List.of("§e/csmp §7— статистика (уровень, XP)",
                            "§e/csmp stats §7— статистика",
                            "§e/csmp particle <имя> §7— партикл-трейл",

                            "§e/csmp sound §7— вкл/выкл звук XP",
                            "§e/csmp leaders §7— топ игроков",
                            "§e/csmp ping §7— вкл/выкл звук пинга",
                            "§e/csmp checkonline §7— статистика онлайна",
                            "§e/csmp help §7— эта помощь",
                            "§e/csmp reload §7— перезагрузить конфиг",
                            "§e/csmp admin §7— админ-команды")));
        } else {
            commands = new java.util.ArrayList<>(MessagesManager.getStringList("command.help_player",
                    List.of("§e/csmp §7— статистика (уровень, XP)",
                            "§e/csmp stats §7— статистика",
                            "§e/csmp particle <имя> §7— партикл-трейл",

                            "§e/csmp sound §7— вкл/выкл звук XP",
                            "§e/csmp leaders §7— топ игроков",
                            "§e/csmp ping §7— вкл/выкл звук пинга",
                            "§e/csmp checkonline §7— статистика онлайна",
                            "§e/csmp help §7— эта помощь")));
        }

        int pageSize = plugin.getConfig().getInt("settings.help-page-size", 6);
        int page = 1;
        if (args.length > 1) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
                // не число — остаёмся на первой странице
            }
        }
        int maxPage = Math.max(1, (commands.size() + pageSize - 1) / pageSize);
        page = Math.max(1, Math.min(page, maxPage));

        sender.sendMessage(MessagesManager.format("command.help_header",
                "§6CubixSMP §7v{version} §8— Помощь",
                "version", plugin.getDescription().getVersion()));

        int from = (page - 1) * pageSize;
        int to = Math.min(from + pageSize, commands.size());
        if (commands.isEmpty()) {
            sender.sendMessage(MessagesManager.getString("command.help_empty", "§7Команды пока не настроены."));
        } else {
            for (int i = from; i < to; i++) {
                sender.sendMessage(commands.get(i));
            }
        }

        String pageLabel = MessagesManager.format("command.help_page_label", "§8Страница §e{page}§7/§e{max}",
                "page", String.valueOf(page), "max", String.valueOf(maxPage));
        if (sender instanceof Player player) {
            Component prev = buildHelpButton(page > 1,
                    MessagesManager.getString("command.help_prev_button", "§8[<]"),
                    MessagesManager.getString("command.help_prev_button_active", "§a[<]"),
                    MessagesManager.getString("command.help_prev_hover", "§7Предыдущая страница"),
                    "/csmp help " + (page - 1));
            Component next = buildHelpButton(page < maxPage,
                    MessagesManager.getString("command.help_next_button", "§8[>]"),
                    MessagesManager.getString("command.help_next_button_active", "§a[>]"),
                    MessagesManager.getString("command.help_next_hover", "§7Следующая страница"),
                    "/csmp help " + (page + 1));
            player.sendMessage(Component.text(" ")
                    .append(prev)
                    .append(LegacyComponentSerializer.legacySection().deserialize(pageLabel))
                    .append(next));
        } else {
            sender.sendMessage(pageLabel);
        }
        return true;
    }

    /**
     * Кликабельная кнопка пагинации. Если disabled — серый текст без действия.
     */
    private Component buildHelpButton(boolean enabled, String disabledText, String enabledText,
                                      String hover, String command) {
        if (!enabled) {
            return LegacyComponentSerializer.legacySection().deserialize(disabledText);
        }
        return LegacyComponentSerializer.legacySection().deserialize(enabledText)
                .hoverEvent(HoverEvent.showText(LegacyComponentSerializer.legacySection().deserialize(hover)))
                .clickEvent(ClickEvent.runCommand(command));
    }

    // ─── Меню действий по клику на сообщение ────
    // Анти-спам: КД между открытиями меню (1 сек по умолчанию)
    private final java.util.Map<UUID, Long> actionCooldowns = new java.util.HashMap<>();

    /**
     * /csmp action <игрок> — открывает GUI-диалог «Что вы хотите сделать?»
     * с кнопками [ЛС], [TPA] и [Отмена] (см. {@link ActionMenu}).
     * Вызывается кликом по НИКУ отправителя в чате.
     * Анти-спам: КД из chat-format.click-menu-cooldown (по умолчанию 1000 мс).
     */
    private boolean handleAction(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessagesManager.getString("general.player_only", "§c❌ Only players can use this command!"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(MessagesManager.getString("command.action_usage", "§c❌ Использование: §e/csmp action <игрок>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(MessagesManager.format("general.player_not_found", "§c❌ Player §e{player} §cnot found!", "player", args[1]));
            return true;
        }

        // Анти-спам: КД 1 секунда (настраивается в chat-format.click-menu-cooldown)
        long cooldown = plugin.getConfig().getLong("chat-format.click-menu-cooldown", 1000);
        long now = System.currentTimeMillis();
        actionCooldowns.entrySet().removeIf(e -> now - e.getValue() > cooldown);
        Long last = actionCooldowns.get(player.getUniqueId());
        if (last != null && now - last < cooldown) {
            long seconds = (cooldown - (now - last) + 999) / 1000;
            player.sendMessage(MessagesManager.format("command.action_cooldown", "§c❌ Подождите §e{time} §cсек!",
                    "time", String.valueOf(seconds)));
            return true;
        }
        actionCooldowns.put(player.getUniqueId(), now);

        plugin.getActionMenu().open(player, target);
        return true;
    }

    // ─── Админ-команды ─────────────────────────

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("cubixsmp.admin")) {
            sender.sendMessage(MessagesManager.getString("admin.no_permission", "§c❌ You don't have admin permission!"));
            return true;
        }

        if (args.length < 2) {
            showAdminHelp(sender);
            return true;
        }

        return switch (args[1].toLowerCase()) {
            case "setlevel" -> handleSetLevel(sender, args);
            case "addxp" -> handleAddXp(sender, args);
            case "removexp" -> handleRemoveXp(sender, args);
            case "reset" -> handleReset(sender, args);
            case "info" -> handleInfo(sender, args);
            case "giveenchant" -> handleGiveEnchant(sender, args);
            default -> {
                sender.sendMessage(MessagesManager.getString("admin.unknown_subcommand", "§c❌ Unknown subcommand!"));
                showAdminHelp(sender);
                yield true;
            }
        };
    }

    private void showAdminHelp(CommandSender sender) {
        sender.sendMessage(MessagesManager.getString("admin.help_header", "§6╔═══════════════════════════════╗"));
        sender.sendMessage(MessagesManager.getString("admin.help_title", "§6║ §lCubixSMP Admin §r§6       ║"));
        sender.sendMessage(MessagesManager.getString("admin.help_footer", "§6╚═══════════════════════════════╝"));
        for (String line : MessagesManager.getStringList("admin.help_commands",List.of("§e/cs admin info <player>", "§e/cs admin setlevel <player> <level>",
                            "§e/cs admin addxp <player> <amount>", "§e/cs admin removexp <player> <amount>",
                            "§e/cs admin reset <player>", "§e/cs admin giveenchant <player> <bur|autosmelt>"))) {
            sender.sendMessage(line);
        }
    }

    /**
     * /cubixsmp admin setlevel <player> <level>
     */
    private boolean handleSetLevel(CommandSender sender, String[] args) {
        if (!sender.hasPermission("cubixsmp.admin.setlevel") && !sender.hasPermission("cubixsmp.admin")) {
            sender.sendMessage(MessagesManager.getString("general.no_permission", "§c❌ No permission!"));
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage(MessagesManager.getString("admin.setlevel_usage", "§c❌ Usage: /cubixsmp admin setlevel <player> <level>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(MessagesManager.format("general.player_not_found", "§c❌ Player §e{player} §cnot found!", "player", args[2]));
            return true;
        }

        int level;
        try {
            level = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(MessagesManager.getString("admin.setlevel_invalid_level", "§c❌ Level must be a number!"));
            return true;
        }

        int minLevel = plugin.getLevelManager().getMinLevel();
        int maxLevel = plugin.getLevelManager().getMaxLevel();
        if (level < minLevel || level > maxLevel) {
            sender.sendMessage(MessagesManager.format("admin.setlevel_invalid_level", "§c❌ Level must be {min}-{max}!",
                    "min", String.valueOf(minLevel), "max", String.valueOf(maxLevel)));
            return true;
        }

        plugin.getPlayerDataManager().setLevel(target.getUniqueId(), level);
        plugin.getPlayerDataManager().setXp(target.getUniqueId(), 0);
        plugin.getPlayerDataManager().syncToManagers(target.getUniqueId());

        sender.sendMessage(MessagesManager.format("admin.setlevel_success", "§a✔ Level set to §e{level} §afor §e{target}§a!",
                "target", target.getName(), "level", String.valueOf(level)));
        target.sendMessage(MessagesManager.format("admin.setlevel_notified", "§e✦ §aAdmin set your level: §e{level}",
                "level", String.valueOf(level)));
        return true;
    }

    /**
     * /cubixsmp admin addxp <player> <amount>
     */
    private boolean handleAddXp(CommandSender sender, String[] args) {
        if (!sender.hasPermission("cubixsmp.admin.addxp") && !sender.hasPermission("cubixsmp.admin")) {
            sender.sendMessage(MessagesManager.getString("general.no_permission", "§c❌ No permission!"));
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage(MessagesManager.getString("admin.addxp_usage", "§c❌ Usage: /cubixsmp admin addxp <player> <amount>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(MessagesManager.format("general.player_not_found", "§c❌ Player §e{player} §cnot found!", "player", args[2]));
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(MessagesManager.getString("admin.addxp_invalid_amount", "§c❌ Amount must be a number!"));
            return true;
        }

        if (amount <= 0) {
            sender.sendMessage(MessagesManager.getString("admin.addxp_invalid_amount", "§c❌ Amount must be positive!"));
            return true;
        }

        plugin.getPlayerDataManager().addXp(target.getUniqueId(), amount, target);
        plugin.setLastAction(target.getUniqueId(), "Admin");

        sender.sendMessage(MessagesManager.format("admin.addxp_success", "§a✔ Added §e{amount} XP §ato §e{target}§a!",
                "target", target.getName(), "amount", formatXp(amount)));
        target.sendMessage(MessagesManager.format("admin.addxp_notified", "§e✦ §aAdmin added §e{amount} XP",
                "amount", formatXp(amount)));
        return true;
    }

    /**
     * /cubixsmp admin removexp <player> <amount>
     */
    private boolean handleRemoveXp(CommandSender sender, String[] args) {
        if (!sender.hasPermission("cubixsmp.admin.removexp") && !sender.hasPermission("cubixsmp.admin")) {
            sender.sendMessage(MessagesManager.getString("general.no_permission", "§c❌ No permission!"));
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage(MessagesManager.getString("admin.removexp_usage", "§c❌ Usage: /cubixsmp admin removexp <player> <amount>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(MessagesManager.format("general.player_not_found", "§c❌ Player §e{player} §cnot found!", "player", args[2]));
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(MessagesManager.getString("admin.removexp_invalid_amount", "§c❌ Amount must be a number!"));
            return true;
        }

        if (amount <= 0) {
            sender.sendMessage(MessagesManager.getString("admin.removexp_invalid_amount", "§c❌ Amount must be positive!"));
            return true;
        }

        UUID uuid = target.getUniqueId();
        double currentXp = plugin.getPlayerDataManager().getXp(uuid);
        double newXp = Math.max(0, currentXp - amount);
        plugin.getPlayerDataManager().setXp(uuid, newXp);
        plugin.getPlayerDataManager().syncToManagers(uuid);

        sender.sendMessage(MessagesManager.format("admin.removexp_success", "§a✔ Removed §e{amount} XP §afrom §e{target}§a!",
                "target", target.getName(), "amount", formatXp(amount)));
        target.sendMessage(MessagesManager.format("admin.removexp_notified", "§e✦ §cAdmin removed §e{amount} XP",
                "amount", formatXp(amount)));
        return true;
    }

    /**
     * /cubixsmp admin reset <player> [confirm]
     */
    private boolean handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("cubixsmp.admin.reset") && !sender.hasPermission("cubixsmp.admin")) {
            sender.sendMessage(MessagesManager.getString("general.no_permission", "§c❌ No permission!"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(MessagesManager.getString("admin.reset_usage", "§c❌ Usage: /cubixsmp admin reset <player>"));
            return true;
        }

        // Требуется подтверждение
        if (args.length < 4 || !args[3].equalsIgnoreCase("confirm")) {
            sender.sendMessage(MessagesManager.format("admin.reset_confirm", "§c⚠ Are you sure? Use §e/cubixsmp admin reset {target} confirm",
                    "target", args[2]));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[2]);
        java.util.UUID uuid;
        String targetName;

        if (target != null) {
            uuid = target.getUniqueId();
            targetName = target.getName();
        } else {
            // Try to find offline player by name — use online player only
            sender.sendMessage(MessagesManager.format("general.player_not_found", "§c❌ Player §e{player} §cnot found!", "player", args[2]));
            return true;
        }

        plugin.getPlayerDataManager().setLevel(uuid, plugin.getLevelManager().getMinLevel());
        plugin.getPlayerDataManager().setXp(uuid, 0);
        plugin.getPlayerDataManager().syncToManagers(uuid);

        // Удаляем файл данных (через менеджер — с синхронизацией, чтобы не конфликтовать с автосохранением)
        plugin.getPlayerDataManager().deleteDataFile(uuid);

        sender.sendMessage(MessagesManager.format("admin.reset_success", "§a✔ Player §e{target} §areset!",
                "target", targetName));
        if (target != null && target.isOnline()) {
            target.sendMessage(MessagesManager.getString("admin.reset_notified", "§c✦ Your CubixSMP progress has been reset by admin!"));
        }
        return true;
    }

    /**
     * /cubixsmp admin info <player>
     */
    private boolean handleInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("cubixsmp.admin.info") && !sender.hasPermission("cubixsmp.admin")) {
            sender.sendMessage(MessagesManager.getString("general.no_permission", "§c❌ No permission!"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(MessagesManager.getString("admin.info_usage", "§c❌ Usage: /cubixsmp admin info <player>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(MessagesManager.format("general.player_not_found", "§c❌ Player §e{player} §cnot found!", "player", args[2]));
            return true;
        }

        java.util.UUID uuid = target.getUniqueId();
        String version = plugin.getDescription().getVersion();
        int level = plugin.getLevelManager().getLevel(uuid);
        double xp = plugin.getLevelManager().getXp(uuid);
        double needed = plugin.getLevelManager().getXpForNextLevel(level);
        int maxLevel = plugin.getLevelManager().getMaxLevel();
        int progressPercent = needed > 0 ? (int) ((xp / needed) * 100) : 0;
        int playtimeSeconds = plugin.getPlayerDataManager().getPlaytimeSeconds(uuid);
        String playtime = formatPlaytime(playtimeSeconds);

        sender.sendMessage(MessagesManager.getString("admin.info_header", "§6╔═══════════════════════════════╗"));
        sender.sendMessage(MessagesManager.format("admin.info_name", "§6║ §lInfo: §e{target}", "target", target.getName()));
        sender.sendMessage(MessagesManager.getString("admin.info_footer", "§6╚═══════════════════════════════╝"));
        sender.sendMessage(MessagesManager.format("admin.info_uuid", "§eUUID: §f{uuid}", "uuid", uuid.toString()));
        sender.sendMessage(MessagesManager.format("admin.info_level", "§eLevel: §f{level} §7/ {max}",
                "level", String.valueOf(level), "max", String.valueOf(maxLevel)));
        sender.sendMessage(MessagesManager.format("admin.info_xp", "§eXP: §f{xp}", "xp", formatXp(xp)));
        sender.sendMessage(MessagesManager.format("admin.info_xp_needed", "§eTo next level: §f{needed} XP §7({percent}%)",
                "needed", formatXp(needed), "percent", String.valueOf(Math.min(progressPercent, 100))));
        sender.sendMessage(MessagesManager.format("admin.info_playtime", "§ePlaytime: §f{playtime}", "playtime", playtime));
        sender.sendMessage(MessagesManager.format("admin.info_progress_bar", "§eProgress: §f{progress}",
                "progress", progressBar(progressPercent)));
        return true;
    }

    /**
     * /cubixsmp admin giveenchant <player> <bur|autosmelt> [count]
     * Выдаёт игроку спец. зачарованную книгу (Бур / Автопереплавка) с PDC.
     * Применяется на верстаке: инструмент + книга.
     */
    private boolean handleGiveEnchant(CommandSender sender, String[] args) {
        if (!sender.hasPermission("cubixsmp.admin.giveenchant") && !sender.hasPermission("cubixsmp.admin")) {
            sender.sendMessage(MessagesManager.getString("general.no_permission", "§c❌ No permission!"));
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage(MessagesManager.getString("admin.giveenchant_usage",
                    "§c❌ Использование: §e/cubixsmp admin giveenchant <player> <bur|autosmelt> [count]"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(MessagesManager.format("general.player_not_found", "§c❌ Player §e{player} §cnot found!",
                    "player", args[2]));
            return true;
        }

        CustomEnchantType type = CustomEnchantType.fromId(args[3]);
        if (type == null) {
            sender.sendMessage(MessagesManager.format("admin.giveenchant_unknown",
                    "§c❌ Неизвестное зачарование §e{enchant}§c. Доступны: §ebur, autosmelt",
                    "enchant", args[3]));
            return true;
        }

        int count = 1;
        if (args.length >= 5) {
            try {
                count = Math.max(1, Math.min(64, Integer.parseInt(args[4])));
            } catch (NumberFormatException ignored) {
                // не число — остаётся 1
            }
        }

        ItemStack book = plugin.getCustomEnchantManager().createBook(type);
        book.setAmount(count);
        java.util.Map<Integer, ItemStack> leftover = target.getInventory().addItem(book);
        for (ItemStack rest : leftover.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), rest);
        }

        sender.sendMessage(MessagesManager.format("admin.giveenchant_success",
                "§a✔ Игроку §e{target} §aвыдана книга: §f{enchant} §a(§e{amount}§a)",
                "target", target.getName(),
                "enchant", plugin.getCustomEnchantManager().displayName(type),
                "amount", String.valueOf(count)));
        return true;
    }

    // ─── Sound toggle ───────────────────────────

    private boolean handleSound(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessagesManager.getString("general.player_only", "§c❌ Only players can use this command!"));
            return true;
        }
        boolean newState = plugin.getPlayerDataManager().toggleSound(player.getUniqueId());
        if (newState) {
            player.sendMessage(MessagesManager.getString("command.sound_on", "§a✔ Звук XP §a§lВКЛЮЧЁН"));
        } else {
            player.sendMessage(MessagesManager.getString("command.sound_off", "§c✔ Звук XP §c§lВЫКЛЮЧЕН"));
        }
        return true;
    }

    // ─── CheckOnline ───────────────────────────

    private boolean handleCheckOnline(CommandSender sender, String[] args) {
        // args[0] = "checkonline", args[1+] = player name (optional)
        String[] strippedArgs = new String[args.length - 1];
        System.arraycopy(args, 1, strippedArgs, 0, args.length - 1);
        return plugin.getPlaytimeTracker().handleCheckOnline(sender, strippedArgs);
    }

    // ─── Ping toggle ────────────────────────────

    private boolean handlePing(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessagesManager.getString("general.player_only", "§c❌ Only players can use this command!"));
            return true;
        }
        boolean newState = plugin.getPingSettings().toggle(player);
        if (newState) {
            player.sendMessage(MessagesManager.getString("ping.toggled_on", "§a✔ Звук пинга §a§lВКЛЮЧЁН"));
        } else {
            player.sendMessage(MessagesManager.getString("ping.toggled_off", "§c✔ Звук пинга §c§lВЫКЛЮЧЕН"));
        }
        return true;
    }

    // ─── Leaders (топ игроков) ───────────────────

    private boolean handleLeaders(CommandSender sender) {
        int limit = plugin.getConfig().getInt("settings.leaders-limit", 10);
        java.util.List<String[]> top = plugin.getPlayerDataManager().getTopPlayers(limit);

        sender.sendMessage(MessagesManager.format("leaders.header", "§6╔═══════════════════════════════╗",
                "limit", String.valueOf(limit)));
        sender.sendMessage(MessagesManager.getString("leaders.title", "§6║ §lТоп игроков §r§6              ║"));
        sender.sendMessage(MessagesManager.getString("leaders.footer", "§6╚═══════════════════════════════╝"));

        if (top.isEmpty()) {
            sender.sendMessage(MessagesManager.getString("leaders.empty", "§7Пока нет данных для топа."));
            return true;
        }

        int i = 1;
        String format = MessagesManager.getString("leaders.entry_format", "§f#{rank} §e{player} §7— §eУровень {level} §7({xp} XP)");
        for (String[] entry : top) {
            String line = format
                    .replace("{rank}", String.valueOf(i))
                    .replace("{player}", entry[0])
                    .replace("{level}", entry[1])
                    .replace("{xp}", entry[2]);
            sender.sendMessage(line);
            i++;
        }
        return true;
    }

    // ─── Вспомогательные методы ────────────────

    private void showStats(Player player) {
        var uuid = player.getUniqueId();
        int level = plugin.getLevelManager().getLevel(uuid);
        double xp = plugin.getLevelManager().getXp(uuid);
        double needed = plugin.getLevelManager().getXpForNextLevel(level);
        int maxLevel = plugin.getLevelManager().getMaxLevel();

        player.sendMessage(MessagesManager.getString("stats.header", "§6╔══════════════════════════════╗"));
        player.sendMessage(MessagesManager.getString("stats.title", "§6║ §lCubixSMP §r§6— Your progress ║"));
        player.sendMessage(MessagesManager.getString("stats.footer", "§6╚══════════════════════════════╝"));
        player.sendMessage(MessagesManager.format("stats.level", "§e✦ Level: §f{level} §7/ {max}",
                "level", String.valueOf(level), "max", String.valueOf(maxLevel)));

        if (level < maxLevel) {
            int progressPercent = needed > 0 ? (int) ((xp / needed) * 100) : 0;
            player.sendMessage(MessagesManager.format("stats.xp", "§e✦ XP: §f{xp} §7/ {needed} XP",
                    "xp", formatXp(xp), "needed", formatXp(needed)));
            player.sendMessage(MessagesManager.format("stats.progress", "§e✦ Progress: §f{percent}%",
                    "percent", String.valueOf(Math.min(progressPercent, 100))));
            player.sendMessage(progressBar(progressPercent));
        } else {
            player.sendMessage(MessagesManager.getString("stats.max_level", "§6✦ §lMAX LEVEL! §6✦"));
        }
    }

    private String progressBar(int percent) {
        int bars = Math.min(percent / 10, 10);
        String filled = MessagesManager.getString("stats.progress_bar_filled", "§a■");
        String empty = MessagesManager.getString("stats.progress_bar_empty", "§7■");
        String bracket = MessagesManager.getString("stats.progress_bar_bracket", "§7[");
        StringBuilder sb = new StringBuilder(bracket);
        for (int i = 0; i < bars; i++) sb.append(filled);
        for (int i = bars; i < 10; i++) sb.append(empty);
        sb.append("§7]");
        return sb.toString();
    }

    private String formatPlaytime(int seconds) {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        if (hours > 0) return hours + "ч " + minutes + "мин";
        return minutes + "мин";
    }
}
