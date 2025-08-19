package cjs.DF_Plugin.player;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.clan.Clan;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class PlayerChatListener implements Listener {

    private final DF_Main plugin;

    public PlayerChatListener(DF_Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        // 전체 채팅 비활성화 확인
        if (plugin.getGameConfigManager().isChatDisabled()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c현재 서버 전체 채팅이 비활성화되어 있습니다.");
            return;
        }

        if (event.isCancelled() || !plugin.getGameConfigManager().isClanChatPrefixEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());

        if (clan != null) {
            String prefix = clan.getColor() + "[" + clan.getName() + "]§r ";
            event.setFormat(prefix + event.getFormat());
        }
    }
}