package df.bridge;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Collections;
import java.util.Map;

/**
 * Bukkit event emitted by DFBridgePlugin whenever DF_Plugin raises a notification
 * through {@code EmitHelper}.
 */
public class DFNotifyEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public enum Type {
        CLAN_INTRUSION,
        PYLON_DESTROYED,
        CLAN_ABSORBED,
        CLAN_MEMBER_JOINED,
        RIFT_UNSTABLE,
        RIFT_STRONG_ENERGY,
        RIFT_CLOSED,
        END_PORTAL_COUNTDOWN,
        END_PORTAL_OPENED,
        ENDER_DRAGON_DEFEATED,
        END_WORLD_COLLAPSE_SOON,
        UPGRADE_DESTROYED,
        UPGRADE_LV10_BORN,
        GIFTBOX_ARRIVED
    }

    private final Type type;
    private final Map<String, String> ctx;

    public DFNotifyEvent(Type type, Map<String, String> ctx) {
        this.type = type;
        this.ctx = ctx == null ? Map.of() : Collections.unmodifiableMap(ctx);
    }

    public Type getType() {
        return type;
    }

    public Map<String, String> getCtx() {
        return ctx;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
