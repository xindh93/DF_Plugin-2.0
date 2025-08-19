package cjs.DF_Plugin.clan.storage;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.clan.Clan;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class ClanStorageManager {
    private final DF_Main plugin;
    private final File clansFolder;

    public ClanStorageManager(DF_Main plugin) {
        this.plugin = plugin;
        this.clansFolder = new File(plugin.getDataFolder(), "clans");
        if (!clansFolder.exists()) {
            clansFolder.mkdirs();
        }
    }

    private File getClanFolder(String clanName) {
        return new File(clansFolder, clanName);
    }

    public Map<String, Clan> loadAllClans() {
        Map<String, Clan> clans = new HashMap<>();
        File[] clanFolders = clansFolder.listFiles(File::isDirectory);
        if (clanFolders == null) return clans;

        for (File clanFolder : clanFolders) {
            File clanFile = new File(clanFolder, "info.yml");
            if (!clanFile.exists()) continue;

            try {
                FileConfiguration clanConfig = YamlConfiguration.loadConfiguration(clanFile);
                String clanName = clanFolder.getName();
                Clan clan = new Clan(clanName, clanConfig);
                clans.put(clanName.toLowerCase(), clan);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "손상되었거나 잘못된 클랜 파일을 불러오는데 실패했습니다: " + clanFolder.getName(), e);
            }
        }
        return clans;
    }

    public void saveClan(Clan clan) {
        File clanFolder = getClanFolder(clan.getName());
        if (!clanFolder.exists()) clanFolder.mkdirs();
        File clanFile = new File(clanFolder, "info.yml");
        FileConfiguration clanConfig = new YamlConfiguration();
        clan.save(clanConfig); // Clan의 save 메소드가 config를 채움
        try {
            clanConfig.save(clanFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save clan " + clan.getName(), e);
        }
    }

    public void deleteClan(String clanName) {
        File clanFolder = getClanFolder(clanName);
        if (clanFolder.exists()) {
            if (!deleteDirectory(clanFolder)) {
                plugin.getLogger().severe("Failed to delete clan folder for " + clanName);
            }
        }
    }

    private boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }

    public Inventory loadPylonStorage(Clan clan) {
        File storageFile = new File(getClanFolder(clan.getName()), "storage.yml");
        Inventory inventory = Bukkit.createInventory(null, 54, clan.getDisplayName() + " §r§f파일런 창고"); // §r to reset color

        if (storageFile.exists()) {
            FileConfiguration storageConfig = YamlConfiguration.loadConfiguration(storageFile);
            if (storageConfig.contains("inventory.content")) {
                try {
                    List<?> rawList = storageConfig.getList("inventory.content");
                    if (rawList != null) {
                        ItemStack[] content = rawList.stream()
                                .map(item -> item instanceof ItemStack ? (ItemStack) item : null)
                                .toArray(ItemStack[]::new);
                        inventory.setContents(content);
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to load pylon storage for clan " + clan.getName() + ": " + e.getMessage());
                }
            }
        }
        return inventory;
    }

    public void savePylonStorage(Clan clan, Inventory inventory) {
        File clanFolder = getClanFolder(clan.getName());
        if (!clanFolder.exists()) clanFolder.mkdirs();
        File storageFile = new File(clanFolder, "storage.yml");
        FileConfiguration storageConfig = new YamlConfiguration();
        storageConfig.set("inventory.content", inventory.getContents());
        try {
            storageConfig.save(storageFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save pylon storage for clan " + clan.getName() + ": " + e.getMessage());
        }
    }

    public Inventory loadGiftBox(Clan clan) {
        File storageFile = new File(getClanFolder(clan.getName()), "giftbox.yml");
        Inventory inventory = Bukkit.createInventory(null, 27, "§d[" + clan.getDisplayName() + "§d] 선물상자");

        if (storageFile.exists()) {
            FileConfiguration storageConfig = YamlConfiguration.loadConfiguration(storageFile);
            if (storageConfig.contains("inventory.content")) {
                try {
                    List<?> rawList = storageConfig.getList("inventory.content");
                    if (rawList != null) {
                        ItemStack[] content = rawList.stream()
                                .map(item -> item instanceof ItemStack ? (ItemStack) item : null)
                                .toArray(ItemStack[]::new);
                        inventory.setContents(content);
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to load gift box for clan " + clan.getName() + ": " + e.getMessage());
                }
            }
        }
        return inventory;
    }

    public void saveGiftBox(Clan clan, Inventory inventory) {
        File clanFolder = getClanFolder(clan.getName());
        if (!clanFolder.exists()) clanFolder.mkdirs();
        File storageFile = new File(clanFolder, "giftbox.yml");
        FileConfiguration storageConfig = new YamlConfiguration();
        storageConfig.set("inventory.content", inventory.getContents());
        try {
            storageConfig.save(storageFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save gift box for clan " + clan.getName() + ": " + e.getMessage());
        }
    }
}