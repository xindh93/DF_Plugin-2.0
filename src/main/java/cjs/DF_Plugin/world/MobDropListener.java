package cjs.DF_Plugin.world;

import cjs.DF_Plugin.pylon.item.PylonItemFactory;
import org.bukkit.entity.ElderGuardian;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.Random;

public class MobDropListener implements Listener {

    private final Random random = new Random();

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof ElderGuardian) {
            if (random.nextDouble() < 0.5) {
                event.getDrops().add(PylonItemFactory.createReturnScroll());
            }
        }
    }
}