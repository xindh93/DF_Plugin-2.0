package cjs.DF_Plugin.data;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public abstract class DataManager {
    protected final DF_Main plugin;
    protected final String fileName;
    protected FileConfiguration dataConfig;
    protected File dataFile;

    public DataManager(DF_Main plugin, String fileName) {
        this.plugin = plugin;
        this.fileName = fileName;
        saveDefaultConfig();
    }

    public void reloadConfig() {
        if (this.dataFile == null) {
            this.dataFile = new File(this.plugin.getDataFolder(), this.fileName);
        }
        this.dataConfig = YamlConfiguration.loadConfiguration(this.dataFile);
    }

    public FileConfiguration getConfig() {
        if (this.dataConfig == null) {
            reloadConfig();
        }
        return this.dataConfig;
    }

    public void saveConfig() {
        if (this.dataConfig == null || this.dataFile == null) {
            return;
        }
        try {
            getConfig().save(this.dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save config to " + this.dataFile);
            e.printStackTrace();
        }
    }

    public void saveDefaultConfig() {
        if (this.dataFile == null) {
            this.dataFile = new File(this.plugin.getDataFolder(), this.fileName);
        }
        if (!this.dataFile.exists()) {
            try {
                this.dataFile.getParentFile().mkdirs();
                this.dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create data file: " + this.fileName);
            }
        }
    }

    public boolean fileExists() {
        if (this.dataFile == null) {
            this.dataFile = new File(this.plugin.getDataFolder(), this.fileName);
        }
        return this.dataFile.exists();
    }

    public void deleteFile() {
        if (this.dataFile == null) {
            this.dataFile = new File(this.plugin.getDataFolder(), this.fileName);
        }
        if (this.dataFile.exists()) {
            if (!this.dataFile.delete()) {
                plugin.getLogger().warning("Could not delete data file: " + this.fileName);
            }
        }
        // 메모리에 로드된 설정도 초기화합니다.
        this.dataConfig = null;
    }
}