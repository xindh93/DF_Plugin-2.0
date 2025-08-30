package cjs.DF_Plugin;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * DF Bridge가 있으면 리플렉션으로 emit() 호출, 없으면 조용히 no-op.
 * 프로젝트 어디서든 import 후 EmitHelper.xxx(...) 한 줄만 호출하면 됩니다.
 */
public final class EmitHelper {
    private EmitHelper() {}

    private static final String BRIDGE_CLASS = "df.bridge.DFBridgePlugin";
    private static final String TYPE_CLASS   = "df.bridge.DFNotifyEvent$Type";

    private static Method EMIT;                 // DFBridgePlugin.emit(Type, Map<String,String>)
    @SuppressWarnings("rawtypes")
    private static Class<? extends Enum> TYPE;  // DFNotifyEvent.Type

    static {
        try {
            Class<?> bridge = Class.forName(BRIDGE_CLASS);
            @SuppressWarnings("unchecked")
            Class<? extends Enum> typeEnum = (Class<? extends Enum>) Class.forName(TYPE_CLASS).asSubclass(Enum.class);
            Method emit = bridge.getMethod("emit", typeEnum, Map.class);

            TYPE = typeEnum;
            EMIT = emit;
            // 성공적으로 연결됨
        } catch (Throwable ignored) {
            // DF Bridge 미존재/버전 불일치 → 조용히 no-op
            TYPE = null;
            EMIT = null;
        }
    }

    // ---------- 내부 공통 ----------
    private static Map<String, String> m(String k1, String v1) {
        Map<String, String> m = new HashMap<>();
        if (k1 != null && v1 != null) m.put(k1, v1);
        return m;
    }
    private static Map<String, String> m(String k1, String v1, String k2, String v2) {
        Map<String, String> m = m(k1, v1);
        if (k2 != null && v2 != null) m.put(k2, v2);
        return m;
    }
    private static Map<String, String> m(String k1, String v1, String k2, String v2, String k3, String v3) {
        Map<String, String> m = m(k1, v1, k2, v2);
        if (k3 != null && v3 != null) m.put(k3, v3);
        return m;
    }

    private static void emit(String typeName, Map<String, String> ctx) {
        if (EMIT == null || TYPE == null) return; // no-op
        try {
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object typeConst = Enum.valueOf(TYPE, typeName);
            EMIT.invoke(null, typeConst, ctx != null ? ctx : Map.of());
        } catch (Throwable ignored) {
            // 브리지 쪽 예외는 삼킨다(게임 진행 방해 금지)
        }
    }

    // ---------- 이벤트 헬퍼(외부에서 호출) ----------
    // ── 클랜/영역 ─────────────────────────────────────────
    public static void clanIntrusion(String intrudedClan, String intruderClanOrNull) {
        emit("CLAN_INTRUSION", intruderClanOrNull == null
                ? m("clan", intrudedClan)
                : m("clan", intrudedClan, "enemyClan", intruderClanOrNull));
    }

    public static void pylonDestroyed(String victimClan, String attackerClanOrNull) {
        emit("PYLON_DESTROYED", attackerClanOrNull == null
                ? m("clan", victimClan)
                : m("clan", victimClan, "enemyClan", attackerClanOrNull));
    }

    public static void clanAbsorbed(String loser, String winner) {
        emit("CLAN_ABSORBED", m("loser", loser, "winner", winner));
    }

    public static void clanMemberJoined(String clan, String player) {
        emit("CLAN_MEMBER_JOINED", m("clan", clan, "player", player));
    }

    // ── 균열/엔드 ────────────────────────────────────────
    public static void riftUnstable()         { emit("RIFT_UNSTABLE",         Map.of()); }
    public static void riftStrongEnergy()     { emit("RIFT_STRONG_ENERGY",    Map.of()); }
    public static void riftClosed()           { emit("RIFT_CLOSED",           Map.of()); }

    public static void endPortalCountdown(int minutes) {
        emit("END_PORTAL_COUNTDOWN", m("minutes", Integer.toString(minutes)));
    }
    public static void endPortalOpened()      { emit("END_PORTAL_OPENED",     Map.of()); }
    public static void enderDragonDefeated()  { emit("ENDER_DRAGON_DEFEATED", Map.of()); }
    public static void endWorldCollapseSoon(int minutes) {
        emit("END_WORLD_COLLAPSE_SOON", m("minutes", Integer.toString(minutes)));
    }

    // ── 강화/아이템 ───────────────────────────────────────
    public static void upgradeDestroyed(int level, String itemOrMaterial) {
        emit("UPGRADE_DESTROYED", m("level", Integer.toString(level), "item", itemOrMaterial));
    }

    public static void upgradeLv10Born(String itemName) {
        emit("UPGRADE_LV10_BORN", m("item", itemName));
    }

    // ── 선물상자 ─────────────────────────────────────────
    public static void giftboxArrived(String clan) {
        emit("GIFTBOX_ARRIVED", m("clan", clan));
    }
}
