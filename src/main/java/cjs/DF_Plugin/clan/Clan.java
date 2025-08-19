package cjs.DF_Plugin.clan;

import cjs.DF_Plugin.pylon.PylonType;
import cjs.DF_Plugin.util.PluginUtils;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;

public class Clan {
    private final String name;
    private UUID leader;
    private final Set<UUID> members = new HashSet<>();
    private ChatColor color;
    private final Map<String, PylonType> pylonLocations = new HashMap<>();
    private long lastRetrievalTime;
    private long lastReconFireworkTime;
    private long lastGiftBoxTime;
    private long lastAuxiliaryRetrievalTime;
    
    public Clan(String name, UUID leader, ChatColor color) {
        this.name = name;
        this.leader = leader;
        this.members.add(leader);
        this.color = color;
    }

    public Clan(String name, FileConfiguration config) {
        this.name = name;
        String leaderUUIDString = config.getString("leader");
        if (leaderUUIDString == null || leaderUUIDString.isEmpty()) {
            throw new IllegalArgumentException("클랜 '" + name + "'의 파일에 리더 UUID가 없거나 비어있습니다.");
        }
        try {
            this.leader = UUID.fromString(leaderUUIDString);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("클랜 '" + name + "'의 리더 UUID가 잘못되었습니다: " + leaderUUIDString);
        }
        config.getStringList("members").forEach(uuidString -> members.add(UUID.fromString(uuidString)));
        this.color = ChatColor.getByChar(config.getString("color", "f").charAt(0));
        this.lastRetrievalTime = config.getLong("last-retrieval-time", 0);
        this.lastReconFireworkTime = config.getLong("last-recon-firework-time", 0);
        this.lastAuxiliaryRetrievalTime = config.getLong("last-auxiliary-retrieval-time", 0);
        this.lastGiftBoxTime = config.getLong("last-giftbox-time", 0);

        // Load pylon locations and types
        ConfigurationSection pylonSection = config.getConfigurationSection("pylons");
        if (pylonSection != null) {
            for (String locString : pylonSection.getKeys(false)) {
                try {
                    // In YAML, '.' is a path separator, so we save with '_' and load with '.'
                    PylonType type = PylonType.valueOf(pylonSection.getString(locString));
                    this.pylonLocations.put(locString.replace('_', '.'), type);
                } catch (IllegalArgumentException e) {
                    // Fallback for old data format or invalid type
                    this.pylonLocations.put(locString.replace('_', '.'), PylonType.MAIN_CORE);
                }
            }
        } else { // Fallback for very old data format (Set<String>)
            List<String> oldPylonList = config.getStringList("pylon-locations");
            for (String locString : oldPylonList) {
                this.pylonLocations.put(locString, PylonType.MAIN_CORE);
            }
        }
    }

    public void save(FileConfiguration config) {
        config.set("leader", leader.toString());
        List<String> memberUUIDs = new ArrayList<>();
        members.forEach(uuid -> memberUUIDs.add(uuid.toString()));
        config.set("members", memberUUIDs);
        config.set("color", String.valueOf(color.getChar()));
        config.set("last-retrieval-time", lastRetrievalTime);
        config.set("last-recon-firework-time", lastReconFireworkTime);
        config.set("last-auxiliary-retrieval-time", lastAuxiliaryRetrievalTime);
        config.set("last-giftbox-time", lastGiftBoxTime);

        // Save pylon locations and types
        config.set("pylon-locations", null); // remove old list format
        ConfigurationSection pylonSection = config.createSection("pylons");
        for (Map.Entry<String, PylonType> entry : pylonLocations.entrySet()) {
            // In YAML, '.' is a path separator, so we must replace it.
            pylonSection.set(entry.getKey().replace('.', '_'), entry.getValue().name());
        }
    }

    public String getName() { return name; }
    public UUID getLeader() { return leader; }
    public Set<UUID> getMembers() { return members; }
    public ChatColor getColor() { return color; }
    public String getDisplayName() { return color + name; }
    public String getFormattedName() { return color + "[" + name + "]"; }
    public Set<String> getPylonLocations() { return pylonLocations.keySet(); }
    public Map<String, PylonType> getPylonLocationsMap() { return new HashMap<>(pylonLocations); }
    public PylonType getPylonType(String location) { return pylonLocations.get(location); }
    public long getLastRetrievalTime() { return lastRetrievalTime; }
    public long getLastReconFireworkTime() { return lastReconFireworkTime; }
    public long getLastGiftBoxTime() { return lastGiftBoxTime; }

    public void setLeader(UUID leader) { this.leader = leader; }
    public void addMember(UUID member) { members.add(member); }
    public void removeMember(UUID member) { members.remove(member); }
    public void clearAllMembers() {
        members.clear();
    }
    public void addPylonLocation(String location) { addPylonLocation(location, PylonType.MAIN_CORE); }
    public void addPylonLocation(String location, PylonType type) { pylonLocations.put(location, type); }
    public void removePylonLocation(String location) { pylonLocations.remove(location); }
    public void clearPylonLocations() { pylonLocations.clear(); }
    public void setLastRetrievalTime(long time) { this.lastRetrievalTime = time; }
    public void setLastReconFireworkTime(long time) { this.lastReconFireworkTime = time; }
    public void setLastGiftBoxTime(long time) { this.lastGiftBoxTime = time; }
    public long getLastAuxiliaryRetrievalTime() {
        return lastAuxiliaryRetrievalTime;
    }
    public void setLastAuxiliaryRetrievalTime(long lastAuxiliaryRetrievalTime) {
        this.lastAuxiliaryRetrievalTime = lastAuxiliaryRetrievalTime;
    }

    
    public boolean hasAuxiliaryPylons() {
        return pylonLocations.containsValue(PylonType.AUXILIARY);
    }

    public String getMainPylonCoreLocation() {
        for (Map.Entry<String, PylonType> entry : pylonLocations.entrySet()) {
            if (entry.getValue() == PylonType.MAIN_CORE) {
                return entry.getKey();
            }
        }
        return null;
    }

    public Location getMainPylonLocationObject() {
        String locStr = getMainPylonCoreLocation();
        if (locStr != null) {
            return PluginUtils.deserializeLocation(locStr);
        }
        // Fallback for clans that might not have a main core due to old data
        if (!pylonLocations.isEmpty()) {
            return PluginUtils.deserializeLocation(pylonLocations.keySet().iterator().next());
        }
        return null;
    }

    public void broadcastMessage(String message) {
        for (UUID memberId : members) {
            Player player = org.bukkit.Bukkit.getPlayer(memberId);
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }

    /**
     * 이 가문이 주 파일런 코어를 소유하고 있는지 확인합니다.
     * @return 주 파일런 코어가 있으면 true
     */
    public boolean hasMainPylon() {
        return pylonLocations.values().stream().anyMatch(type -> type == PylonType.MAIN_CORE);
    }
}