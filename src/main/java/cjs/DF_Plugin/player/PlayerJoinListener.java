package cjs.DF_Plugin.player;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.clan.ClanManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final DF_Main plugin;
    private final ClanManager clanManager;

    public PlayerJoinListener(DF_Main plugin) {
        this.plugin = plugin;
        this.clanManager = plugin.getClanManager();
    }
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // A small delay might be needed for other plugins to load player data,
        // but Bukkit's event priority usually handles this.
        clanManager.getPlayerTagManager().updatePlayerTag(player);

        plugin.getSupplyDropManager().showBarToPlayer(player);
    }
}