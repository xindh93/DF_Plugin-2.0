package cjs.DF_Plugin.data;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.UUID;

public class PlayerDataManager extends DataManager {

    public PlayerDataManager(DF_Main plugin) {
        super(plugin, "playerdata.yml");
    }

    public ConfigurationSection getPlayerSection(UUID uuid) {
        ConfigurationSection playersSection = getConfig().getConfigurationSection("players");
        if (playersSection == null) {
            playersSection = getConfig().createSection("players");
        }
        ConfigurationSection playerSection = playersSection.getConfigurationSection(uuid.toString());
        if (playerSection == null) {
            playerSection = playersSection.createSection(uuid.toString());
        }
        return playerSection;
    }

    public void setPlayerName(Player player) {
        getPlayerSection(player.getUniqueId()).set("name", player.getName());
    }

    public boolean hasInitialTeleportDone(UUID uuid) {
        return getPlayerSection(uuid).getBoolean("initial-teleport-done", false);
    }

    public void setInitialTeleportDone(UUID uuid, boolean done) {
        getPlayerSection(uuid).set("initial-teleport-done", done);
    }

    public void resetAllInitialTeleportFlags() {
        ConfigurationSection playersSection = getConfig().getConfigurationSection("players");
        if (playersSection != null) {
            for (String uuidStr : playersSection.getKeys(false)) {
                playersSection.set(uuidStr + ".initial-teleport-done", null);
            }
        }
    }
}