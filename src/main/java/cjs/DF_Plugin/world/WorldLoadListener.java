package cjs.DF_Plugin.world;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;

public class WorldLoadListener implements Listener {
    private final DF_Main plugin;

    public WorldLoadListener(DF_Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        // 월드가 처음 생성되거나 로드될 때 규칙을 적용합니다.
        plugin.getWorldManager().applyRulesToWorld(event.getWorld());
    }
}