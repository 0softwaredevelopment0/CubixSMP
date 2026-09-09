package com.cubixsmp;

import com.cubixsmp.listeners.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class CubixSMP extends JavaPlugin {

    private static CubixSMP instance;
    private LevelManager levelManager;
    private PlayerDataManager playerDataManager;
    private NaturalCheck naturalCheck;
    private PlacedBlockTracker placedBlockTracker;
    private CubixSMPPlaceholderExpansion placeholderExpansion;
    private CustomEnchantManager customEnchantManager;
    private PlaytimeTracker playtimeTracker;
    private PingSettingsManager pingSettings;
    private ActionMenu actionMenu;
    private ParticleTrailManager particleTrailManager;
    private boolean hasPlaceholderAPI;

    @Override
    public void onEnable() {
        instance = this;

        // Save default config
        saveDefaultConfig();

        // 🔧 Проверка и починка config.yml: недостающие ключи добавляются в конец,
        // дубликаты удаляются (остаётся последнее значение)
        ConfigRepair.repair(this);
        reloadConfig(); // плагин работает уже с починенным файлом

        // Init messages & guide
        MessagesManager.init(this);
        ConfigGuideManager.init(this);

        // Load managers
        this.naturalCheck = new NaturalCheck();
        this.placedBlockTracker = new PlacedBlockTracker(this);
        this.playerDataManager = new PlayerDataManager(this);
        this.levelManager = new LevelManager(this);
        this.customEnchantManager = new CustomEnchantManager(this);

        // ✨ ParticleTrailManager — персональные партикл-трейлы (/csmp particle <имя>)
        this.particleTrailManager = new ParticleTrailManager(this);
        particleTrailManager.reload(); // читает конфиг и запускает задачу спавна

        // Register listeners
        getServer().getPluginManager().registerEvents(new MiningListener(this), this);
        getServer().getPluginManager().registerEvents(new FarmingListener(this), this);
        getServer().getPluginManager().registerEvents(new WoodcuttingListener(this), this);
        getServer().getPluginManager().registerEvents(new FishingListener(this), this);
        getServer().getPluginManager().registerEvents(new HuntingListener(this), this);
        getServer().getPluginManager().registerEvents(new DistanceListener(this), this);
        getServer().getPluginManager().registerEvents(new PlaytimeListener(this), this);
        getServer().getPluginManager().registerEvents(new DailyBonusListener(this), this);

        // Трекер поставленных блоков (для проверки натуральности)
        getServer().getPluginManager().registerEvents(placedBlockTracker, this);

        // 🪓 Топор не ломается о листву (настраивается в config.yml)
        getServer().getPluginManager().registerEvents(new LeafDurabilityListener(this), this);

        // 🚫 Отключение вытаптывания грядок (настраивается в config.yml)
        getServer().getPluginManager().registerEvents(new FarmlandTrampleListener(this), this);

        // 📢 PingSettings — настройки звука пинга
        this.pingSettings = new PingSettingsManager(this);

        // 📊 PlaytimeTracker — ежедневный онлайн игроков
        this.playtimeTracker = new PlaytimeTracker(this);
        getServer().getPluginManager().registerEvents(playtimeTracker, this);
        playtimeTracker.init();

        // 🖱 ActionMenu — GUI-диалог «Что вы хотите сделать?» (клик по нику в чате)
        this.actionMenu = new ActionMenu(this);
        getServer().getPluginManager().registerEvents(actionMenu, this);

        // 💬 ChatFormatListener — miniMessage-права, [item_hand], кликабельные ники.
        // Регистрируется ПЕРВЫМ: он берёт чат под свой контроль, поэтому @пнг и
        // плейсхолдеры обрабатываются внутри него (см. ChatMentionListener.formatMessage).
        ChatMentionListener chatMentionListener = new ChatMentionListener(this, pingSettings);
        getServer().getPluginManager().registerEvents(new ChatFormatListener(this, chatMentionListener), this);

        // 📢 ChatMentionListener — @пнг в чате
        getServer().getPluginManager().registerEvents(chatMentionListener, this);

        // 🔄 ChatPlaceholderListener — пер-плеерные плейсхолдеры в чате (fallback, если chat-format выключен)
        getServer().getPluginManager().registerEvents(new ChatPlaceholderListener(this), this);

        // ⚒ AnvilEnchantListener — запрет зачарований выше ванильного лимита в наковальне
        getServer().getPluginManager().registerEvents(new AnvilEnchantListener(this), this);

        // ⚡ CustomEnchantListener — спец. зачарования «Бур» и «Автопереплавка»
        // (крафт книги на верстаке + эффекты при добыче блоков)
        getServer().getPluginManager().registerEvents(new CustomEnchantCraftListener(this), this);
        getServer().getPluginManager().registerEvents(new CustomEnchantMiningListener(this), this);

        // 💀 DeathMessageListener — сообщение с координатами смерти в чат
        getServer().getPluginManager().registerEvents(new DeathMessageListener(this), this);

        // 📍 RegionChatListener — сообщения о входе/выходе из региона WorldGuard в чат (если WG установлен)
        if (getServer().getPluginManager().getPlugin("WorldGuard") != null) {
            getServer().getPluginManager().registerEvents(new RegionChatListener(this), this);
            getLogger().info("WorldGuard found — region chat messages enabled");
        } else {
            getLogger().info("WorldGuard not found — region chat messages disabled");
        }

        // Register commands + tab completers
        getCommand("cubixsmp").setExecutor(new CubixSMPCommand(this));
        getCommand("cubixsmp").setTabCompleter(new CubixSMPTabCompleter());

        // PlaceholderAPI hook
        this.hasPlaceholderAPI = getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
        if (hasPlaceholderAPI) {
            this.placeholderExpansion = new CubixSMPPlaceholderExpansion(this);
            if (placeholderExpansion.register()) {
                getLogger().info("PlaceholderAPI expansion registered successfully");
            } else {
                getLogger().warning("Failed to register PlaceholderAPI expansion");
                hasPlaceholderAPI = false;
            }
        } else {
            getLogger().info(MessagesManager.getString("errors.papi_not_found", "§ePlaceholderAPI not found — placeholders disabled"));
        }

        // Load all player data
        playerDataManager.loadAll();

        // Start playtime tracker
        getServer().getScheduler().runTaskTimer(this, this::tickPlaytime, 1200L, 1200L); // every 60s

        getLogger().info("CubixSMP v" + getDescription().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        if (playtimeTracker != null) {
            playtimeTracker.shutdown();
        }
        if (particleTrailManager != null) {
            particleTrailManager.stopTask();
        }
        if (playerDataManager != null) {
            playerDataManager.saveAll();
        }
        if (placeholderExpansion != null) {
            CubixSMPPlaceholderExpansion.clearAll();
            placeholderExpansion.unregister();
        }
        getLogger().info("CubixSMP disabled!");
    }

    private void tickPlaytime() {
        if (!getConfig().getBoolean("settings.playtime-xp-enabled", true)) return;
        for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
            playerDataManager.addPlaytime(player.getUniqueId(), 60); // 60 seconds per tick
        }
    }

    // --- Static access ---
    public static CubixSMP getInstance() { return instance; }
    public LevelManager getLevelManager() { return levelManager; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public CustomEnchantManager getCustomEnchantManager() { return customEnchantManager; }
    public NaturalCheck getNaturalCheck() { return naturalCheck; }
    public PlacedBlockTracker getPlacedBlockTracker() { return placedBlockTracker; }
    public PingSettingsManager getPingSettings() { return pingSettings; }
    public PlaytimeTracker getPlaytimeTracker() { return playtimeTracker; }
    public ParticleTrailManager getParticleTrailManager() { return particleTrailManager; }
    public CubixSMPPlaceholderExpansion getPlaceholderExpansion() { return placeholderExpansion; }
    public ActionMenu getActionMenu() { return actionMenu; }
    public boolean hasPlaceholderAPI() { return hasPlaceholderAPI; }

    public void setLastAction(java.util.UUID uuid, String action) {
        if (hasPlaceholderAPI) {
            CubixSMPPlaceholderExpansion.setLastAction(uuid, action);
        }
    }

    public File getPlayerDataFolder() {
        File folder = new File(getDataFolder(), "playerdata");
        if (!folder.exists()) folder.mkdirs();
        return folder;
    }
}
