package df.discord;

import df.bridge.DFNotifyEvent;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Listens for {@link DFNotifyEvent}s and forwards them to a Discord channel
 * via webhook.
 */
public final class DFDiscordConnectorPlugin extends JavaPlugin implements Listener {
    private String webhookUrl;
    private Map<String, String> mentions;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        webhookUrl = getConfig().getString("webhook-url", "");
        ConfigurationSection section = getConfig().getConfigurationSection("mentions");
        mentions = section == null ? Map.of() : section.getValues(false).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())));
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onNotify(DFNotifyEvent e) {
        if (webhookUrl == null || webhookUrl.isBlank()) return;
        String msg = format(e);
        if (msg == null) return;
        String mention = mentions.getOrDefault(e.getType().name(), mentions.getOrDefault("default", ""));
        if (!mention.isBlank()) {
            msg = mention + " " + msg;
        }
        send(msg);
    }

    private static String blank(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v;
    }

    private String format(DFNotifyEvent e) {
        Map<String, String> ctx = e.getCtx();
        return switch (e.getType()) {
            case CLAN_INTRUSION -> "팀의 영역에 외부인이 접근했습니다";
            case PYLON_DESTROYED -> "팀의 파일런이 파괴되었습니다";
            case CLAN_ABSORBED -> "(@%s) 가문이 마지막 파일런을 잃고 + (@%s) 가문에게 흡수되었습니다"
                    .formatted(blank(ctx.get("loser"), "파괴된가문"), blank(ctx.get("winner"), "승리한가문"));
            case CLAN_MEMBER_JOINED -> "@(%s)팀이 \"%s\" 님을 새로운 가문원으로 영입했습니다!"
                    .formatted(blank(ctx.get("clan"), "가문"), blank(ctx.get("player"), "플레이어"));
            case RIFT_UNSTABLE -> "차원의 어딘가가 불안정합니다";
            case RIFT_STRONG_ENERGY -> "균열에서 강력한 기운이 감지됩니다!";
            case RIFT_CLOSED -> "차원의 균열이 닫힙니다.";
            case END_PORTAL_COUNTDOWN -> "공허의 기운이 꿈틀거립니다... %s분 뒤 엔드 포탈이 열립니다!"
                    .formatted(blank(ctx.get("minutes"), "0"));
            case END_PORTAL_OPENED -> "엔더 드래곤의 포효가 들려옵니다! 엔드포탈이 활성화되었습니다!";
            case ENDER_DRAGON_DEFEATED -> "엔더 드래곤이 쓰러져 엔드 포탈이 닫혔습니다!";
            case END_WORLD_COLLAPSE_SOON -> "엔드 월드가 %s분 뒤 붕괴를 시작합니다! 서둘러 탈출하세요!"
                    .formatted(blank(ctx.get("minutes"), "0"));
            case UPGRADE_DESTROYED -> "[!] 한 %s (+%s) 아이템이 강화에 실패하여 파괴되었습니다."
                    .formatted(blank(ctx.get("item"), "아이템"), blank(ctx.get("level"), "0"));
            case UPGRADE_LV10_BORN -> "[!] \"%s\"이(가) 탄생했습니다!"
                    .formatted(blank(ctx.get("item"), "아이템"));
            case GIFTBOX_ARRIVED -> "@(%s)팀에 선물상자가 도착했습니다!"
                    .formatted(blank(ctx.get("clan"), "가문"));
        };
    }

    private void send(String content) {
        try {
            String json = "{\"content\":\"" + escape(content) + "\"}";
            HttpRequest req = HttpRequest.newBuilder(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ex) {
            getLogger().warning("Failed to send Discord message: " + ex.getMessage());
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
