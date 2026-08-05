package com.cubixsmp;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import java.util.UUID;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
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
            case "daily" -> handleDaily(sender);
            case "stats" -> handleStats(sender);
            case "admin" -> handleAdmin(sender, args);
            case "sound" -> handleSound(sender);
            case "leaders" -> handleLeaders(sender);
            case "ping" -> handlePing(sender);
            case "checkonline" -> handleCheckOnline(sender, args);
            default -> handleHelp(sender);
        };
    }

    // ─── Обработчики команд ─────────────────────

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("cubixsmp.reload")) {
            sender.sendMessage(MessagesManager.getString("general.no_permission", "§c❌ You don't have permission!"));
            return true;
        }
        plugin.reloadConfig();
        MessagesManager.reload();
        plugin.getLevelManager().reload();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            plugin.getPlayerDataManager().syncToManagers(p.getUniqueId());
        }
        sender.sendMessage(MessagesManager.getString("general.config_reloaded", "§a✔ Configuration reloaded!"));
        return true;
    }

    private boolean handleDaily(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessagesManager.getString("general.player_only", "§c❌ Only players can use this command!"));
            return true;
        }
        if (plugin.getPlayerDataManager().canClaimDailyBonus(player.getUniqueId())) {
            plugin.getPlayerDataManager().claimDailyBonus(player.getUniqueId(), player);
        } else {
            player.sendMessage(MessagesManager.getString("general.already_daily", "§c❌ You already claimed daily bonus today!"));
        }
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

    private boolean handleHelp(CommandSender sender) {
        if (sender.hasPermission("cubixsmp.admin")) {
            for (String line : MessagesManager.getStringList("command.help_admin",
                    List.of("§e/cubixsmp §7— stats", "§e/cubixsmp stats §7— stats",
                            "§e/cubixsmp daily §7— daily bonus", "§e/cubixsmp sound §7— toggle XP sound",
                            "§e/cubixsmp leaders §7— top players",
                            "§e/cubixsmp reload §7— reload config",
                            "§e/cubixsmp admin §7— admin commands"))) {
                sender.sendMessage(line);
            }
        } else {
            sender.sendMessage(MessagesManager.format("command.help_header", "§6CubixSMP §7v{version}",
                    "version", plugin.getDescription().getVersion()));
            for (String line : MessagesManager.getStringList("command.help_player",
                    List.of("§e/cubixsmp §7— stats", "§e/cubixsmp daily §7— daily bonus",
                            "§e/cubixsmp sound §7— toggle XP sound",
                            "§e/cubixsmp leaders §7— top players"))) {
                sender.sendMessage(line);
            }
        }
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
                            "§e/cs admin reset <player>"))) {
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
