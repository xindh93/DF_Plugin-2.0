package df.bridge;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/**
 * Bridge plugin that exposes a static {@link #emit} method invoked via reflection
 * from DF_Plugin's {@code EmitHelper}. It simply re-dispatches notifications as
 * {@link DFNotifyEvent}s on the Bukkit event bus.
 */
public final class DFBridgePlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        // Nothing to initialise – acts purely as a bridge
    }

    public static void emit(DFNotifyEvent.Type type, Map<String, String> ctx) {
        if (type == null) return;
        DFNotifyEvent event = new DFNotifyEvent(type, ctx);
        Bukkit.getPluginManager().callEvent(event);
    }
}
