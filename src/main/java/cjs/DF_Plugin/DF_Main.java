package cjs.DF_Plugin;

import cjs.DF_Plugin.util.ActionBarManager;
import cjs.DF_Plugin.clan.Clan;
import cjs.DF_Plugin.clan.ClanManager;
import cjs.DF_Plugin.clan.nether.ClanNetherListener;
import cjs.DF_Plugin.command.DFCommand;
import cjs.DF_Plugin.command.ItemNameCommand;
import cjs.DF_Plugin.command.DFTabCompleter;
import cjs.DF_Plugin.command.StorageCommand;
import cjs.DF_Plugin.enchant.EnchantManager;
import cjs.DF_Plugin.enchant.EnchantmentRuleListener;
import cjs.DF_Plugin.events.game.GameStartManager;
import cjs.DF_Plugin.events.supplydrop.SupplyDropScheduler;
import cjs.DF_Plugin.events.supplydrop.SupplyDropManager;
import cjs.DF_Plugin.enchant.EnchantListener;
import cjs.DF_Plugin.items.ItemNameManager;
import cjs.DF_Plugin.items.RecipeManager;
import cjs.DF_Plugin.items.SpecialItemListener;
import cjs.DF_Plugin.player.offline.OfflinePlayerManager;
import cjs.DF_Plugin.player.PlayerChatListener;
import cjs.DF_Plugin.player.PlayerJoinListener;
import cjs.DF_Plugin.player.PlayerRegistryManager;
import cjs.DF_Plugin.player.death.PlayerDeathManager;
import cjs.DF_Plugin.player.stats.PlayerEvalGuiManager;
import cjs.DF_Plugin.player.stats.StatsListener;
import cjs.DF_Plugin.player.stats.StatsManager;
import cjs.DF_Plugin.pylon.PylonManager;
import cjs.DF_Plugin.pylon.beaconinteraction.*;
import cjs.DF_Plugin.pylon.item.ReturnScrollListener;
import cjs.DF_Plugin.pylon.beacongui.giftbox.GiftBoxGuiManager;
import cjs.DF_Plugin.pylon.beacongui.recon.ReconManager;
import cjs.DF_Plugin.pylon.beacongui.giftbox.GiftBoxRefillTask;
import cjs.DF_Plugin.pylon.beacongui.BeaconGUIListener;
import cjs.DF_Plugin.settings.GameConfigManager;
import cjs.DF_Plugin.settings.GameModeManager;
import cjs.DF_Plugin.upgrade.UpgradeListener;
import cjs.DF_Plugin.upgrade.specialability.DurabilityListener;
import cjs.DF_Plugin.upgrade.UpgradeManager;
import cjs.DF_Plugin.upgrade.specialability.passive.BowPassiveListener;
import cjs.DF_Plugin.upgrade.specialability.passive.TridentPassiveListener;
import cjs.DF_Plugin.upgrade.specialability.passive.CrossbowPassiveListener;
import cjs.DF_Plugin.upgrade.specialability.SpecialAbilityListener;
import cjs.DF_Plugin.upgrade.specialability.SpecialAbilityManager;
import cjs.DF_Plugin.world.*;
import cjs.DF_Plugin.world.nether.NetherManager;
import cjs.DF_Plugin.events.end.EndEventManager;
import cjs.DF_Plugin.events.end.EndEventListener;
import cjs.DF_Plugin.events.supplydrop.AltarInteractionListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class DF_Main extends JavaPlugin {

    private static DF_Main instance;

    // --- Core & System Managers ---
    private GameConfigManager gameConfigManager;
    private GameModeManager gameModeManager;
    private WorldManager worldManager;
    private NetherManager netherManager;
    private RecipeManager recipeManager;
    private ItemNameManager itemNameManager;
    private EnchantManager enchantManager;

    // --- Feature Managers ---
    private ClanManager clanManager;
    private PylonManager pylonManager;
    private UpgradeManager upgradeManager;
    private SpecialAbilityManager specialAbilityManager;
    private ActionBarManager actionBarManager;

    // --- Player Data & Interaction Managers ---
    private PlayerRegistryManager playerRegistryManager;
    private PlayerDeathManager playerDeathManager;
    private StatsManager statsManager;
    private PlayerEvalGuiManager playerEvalGuiManager;
    private OfflinePlayerManager offlinePlayerManager;
    private GiftBoxGuiManager giftBoxGuiManager;

    // --- Event Managers ---
    private EndEventManager endEventManager;
    private GameStartManager gameStartManager;
    private SupplyDropManager supplyDropManager;
    private SupplyDropScheduler supplyDropScheduler;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        getLogger().info("Enabling DarkForest 2.0...");

        initializeManagers();

        // 게임 모드 설정을 config.yml에 적용합니다. 이 작업은 리스너 등록 전에 이루어져야 합니다.
        this.gameModeManager.applyCurrentMode();

        registerCommands();
        registerListeners();
        scheduleTasks();

        // 설정 기반 후처리 작업
        this.recipeManager.updateRecipes();
        this.worldManager.applyAllWorldSettings();

        getLogger().info("DarkForest 2.0 plugin has been enabled!");
    }

    /**
     * 플러그인에 필요한 모든 관리자(Manager) 클래스를 초기화합니다.
     * 의존성 순서를 고려하여 핵심 시스템부터 기능, 플레이어 데이터 순으로 로드합니다.
     */
    private void initializeManagers() {
        getLogger().info("Initializing managers...");

        // --- Core & System Managers ---
        gameConfigManager = new GameConfigManager(this);
        gameModeManager = new GameModeManager(this);
        worldManager = new WorldManager(this);
        netherManager = new NetherManager(this);
        recipeManager = new RecipeManager(this);
        itemNameManager = new ItemNameManager(this);
        enchantManager = new EnchantManager(this);

        // --- Player Data & Interaction Managers ---
        playerRegistryManager = new PlayerRegistryManager(this);
        playerDeathManager = new PlayerDeathManager(this);
        statsManager = new StatsManager(this);
        playerEvalGuiManager = new PlayerEvalGuiManager(this);
        offlinePlayerManager = new OfflinePlayerManager(this);
        giftBoxGuiManager = new GiftBoxGuiManager(this);

        // --- Feature Managers (can depend on core managers) ---
        clanManager = new ClanManager(this);
        pylonManager = new PylonManager(this);

        // Upgrade System
        specialAbilityManager = new SpecialAbilityManager(this);
        upgradeManager = new UpgradeManager(this);
        actionBarManager = new ActionBarManager(this, specialAbilityManager);

        // --- Event Managers ---
        endEventManager = new EndEventManager(this);
        gameStartManager = new GameStartManager(this);
        supplyDropManager = new SupplyDropManager(this);

        // 모든 매니저가 초기화된 후, UpgradeManager의 프로필에 의존하는 특수 능력들을 등록합니다.
        specialAbilityManager.registerAbilities();

        getLogger().info("Managers initialized successfully.");
    }

    /**
     * 플러그인에서 사용하는 모든 명령어를 등록합니다.
     */
    private void registerCommands() {
        getLogger().info("Registering commands...");
        DFCommand dfCommand = new DFCommand(this);
        getCommand("df").setExecutor(dfCommand);
        getCommand("df").setTabCompleter(new DFTabCompleter(this));
        getCommand("itemname").setExecutor(new ItemNameCommand(this));
        getCommand("storage").setExecutor(new StorageCommand(this));
    }

    /**
     * 모든 이벤트 리스너를 기능별로 그룹화하여 등록합니다.
     */
    private void registerListeners() {
        getLogger().info("Registering event listeners...");

        // --- Core Player & World Listeners ---
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerChatListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerRespawnListener(this), this);
        this.supplyDropScheduler = new SupplyDropScheduler(this);
        getServer().getPluginManager().registerEvents(this.supplyDropScheduler, this);
        getServer().getPluginManager().registerEvents(new WorldLoadListener(this), this);
        getServer().getPluginManager().registerEvents(new GameRuleListener(this), this);
        getServer().getPluginManager().registerEvents(new BossMobListener(this), this);

        // --- 시스템 토글에 따라 리스너를 조건부로 등록 ---

        if (gameConfigManager.isUpgradeSystemEnabled()) {
            getLogger().info("강화 시스템이 활성화되었습니다. 관련 리스너를 등록합니다.");
            // Upgrade & Enchant
            getServer().getPluginManager().registerEvents(new UpgradeListener(this), this);
            getServer().getPluginManager().registerEvents(new SpecialAbilityListener(this), this);
            getServer().getPluginManager().registerEvents(new TridentPassiveListener(this), this);
            getServer().getPluginManager().registerEvents(new CrossbowPassiveListener(this), this);
            getServer().getPluginManager().registerEvents(new BowPassiveListener(this), this);
            getServer().getPluginManager().registerEvents(new EnchantListener(this), this);
            getServer().getPluginManager().registerEvents(new EnchantmentRuleListener(this), this);
            getServer().getPluginManager().registerEvents(new DurabilityListener(this), this);
        }

        if (gameConfigManager.isPylonSystemEnabled()) {
            getLogger().info("파일런 시스템이 활성화되었습니다. 관련 리스너를 등록합니다.");
            // Pylon & Clan
            getServer().getPluginManager().registerEvents(new BeaconInteractionListener(this), this);
            getServer().getPluginManager().registerEvents(new BeaconGUIListener(this, this.pylonManager.getGuiManager()), this);
            getServer().getPluginManager().registerEvents(new PylonItemListener(), this);
            getServer().getPluginManager().registerEvents(new PylonProtectionListener(this), this);
            getServer().getPluginManager().registerEvents(new ReturnScrollListener(this.pylonManager.getScrollManager()), this);
            getServer().getPluginManager().registerEvents(new ClanNetherListener(this), this);
            getServer().getPluginManager().registerEvents(this.giftBoxGuiManager, this);
        }

        if (gameConfigManager.isEventSystemEnabled()) {
            getLogger().info("이벤트 시스템이 활성화되었습니다. 관련 리스너를 등록합니다.");
            // Game Events
            getServer().getPluginManager().registerEvents(new EndEventListener(this), this);
            getServer().getPluginManager().registerEvents(new AltarInteractionListener(this), this);
        }

        // Item & Stats
        getServer().getPluginManager().registerEvents(new SpecialItemListener(this), this);
        getServer().getPluginManager().registerEvents(new StatsListener(this), this);

        // --- Managers as Listeners (self-registering managers are not listed here) ---
        getServer().getPluginManager().registerEvents(this.playerRegistryManager, this);
        getServer().getPluginManager().registerEvents(playerDeathManager, this);
        getServer().getPluginManager().registerEvents(this.pylonManager.getReconManager(), this);
    }

    /**
     * 반복 및 지연 실행이 필요한 모든 작업을 스케줄러에 등록합니다.
     */
    private void scheduleTasks() {
        getLogger().info("Scheduling tasks...");
        if (gameConfigManager.isPylonSystemEnabled()) {
            // 선물상자 자동 리필 작업 시작 (1분마다 확인)
            new GiftBoxRefillTask(this).runTaskTimer(this, 20L * 60, 20L * 60);
        }

        // 서버 로드가 완료된 후 월드 기반 데이터를 로드합니다.
        getServer().getScheduler().runTask(this, () -> {
            if (pylonManager != null) {
                pylonManager.loadExistingPylons();
            }
            if (offlinePlayerManager != null) {
                offlinePlayerManager.loadAndVerifyOfflineStands();
            }
        });
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling DarkForest 2.0...");

        // 서버 종료 시 온라인 상태인 모든 플레이어의 특수능력 상태를 정리합니다.
        // 이는 onCleanup을 호출하여 액션바, 공전 삼지창 등을 올바르게 제거하고,
        // 이 상태가 파일에 저장되어 재접속 시 원치 않는 효과가 나타나는 문제를 방지합니다.
        if (specialAbilityManager != null) {
            // 이 메서드는 SpecialAbilityManager 내에서 현재 활성화된 능력을 가진 모든 플레이어에 대해
            // onCleanup을 호출하도록 구현해야 합니다.
            specialAbilityManager.cleanupAllActiveAbilities();
        }

        cjs.DF_Plugin.upgrade.specialability.impl.LightningSpearAbility.cleanupAllLingeringTridents();
        // 모든 플레이어 및 가문 관련 데이터를 안전하게 저장합니다.
        if (this.statsManager != null) {
            this.statsManager.saveStats();
        }
        if (specialAbilityManager != null) {
            specialAbilityManager.saveData();
        }
        if (this.clanManager != null) {
            // 서버 종료 시 열려있던 가문 창고 및 선물상자 인벤토리를 강제로 저장합니다.
            clanManager.getPylonStorages().forEach((clanName, inventory) -> {
                Clan clan = clanManager.getClanByName(clanName);
                if (clan != null) clanManager.getStorageManager().savePylonStorage(clan, inventory);
            });
            clanManager.getGiftBoxInventories().forEach((clanName, inventory) -> {
                Clan clan = clanManager.getClanByName(clanName);
                if (clan != null) clanManager.getStorageManager().saveGiftBox(clan, inventory);
            });
        }
        if (this.playerDeathManager != null) {
            playerDeathManager.saveOnDisable();
        }
        getLogger().info("DarkForest 2.0 plugin has been disabled.");
    }

    // 다른 클래스에서 매니저에 접근할 수 있도록 Getter를 제공합니다.
    public ClanManager getClanManager() { return clanManager; }
    public PylonManager getPylonManager() { return pylonManager; }
    public PlayerRegistryManager getPlayerRegistryManager() { return playerRegistryManager; }
    public PlayerDeathManager getPlayerDeathManager() { return playerDeathManager; }
    public StatsManager getStatsManager() { return statsManager; }
    public PlayerEvalGuiManager getPlayerEvalGuiManager() { return playerEvalGuiManager; }
    public WorldManager getWorldManager() { return worldManager; }
    // 강화 시스템 Getter
    public UpgradeManager getUpgradeManager() { return upgradeManager; }
    public SpecialAbilityManager getSpecialAbilityManager() { return specialAbilityManager; }
    public ActionBarManager getActionBarManager() { return actionBarManager; }

    // 신규 설정 시스템 Getter
    public GameConfigManager getGameConfigManager() { return gameConfigManager; }
    public GameModeManager getGameModeManager() { return gameModeManager; }
    public RecipeManager getRecipeManager() { return recipeManager; }
    public EnchantManager getEnchantManager() { return enchantManager; }
    public EndEventManager getEndEventManager() { return endEventManager; }
    public ItemNameManager getItemNameManager() { return itemNameManager; }
    public GameStartManager getGameStartManager() { return gameStartManager; }
    public SupplyDropManager getSupplyDropManager() { return supplyDropManager; }
    public SupplyDropScheduler getSupplyDropScheduler() { return supplyDropScheduler; }

    public ReconManager getReconManager() {
        return this.pylonManager.getReconManager();
    }

    public static DF_Main getInstance() {
        return instance;
    }
}