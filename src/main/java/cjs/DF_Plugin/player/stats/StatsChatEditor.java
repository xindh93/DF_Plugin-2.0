package cjs.DF_Plugin.player.stats;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class StatsChatEditor {

    public static void sendEditor(Player editor, OfflinePlayer target, PlayerStats stats, String progress) {
        editor.sendMessage(" ");
        editor.sendMessage(Component.text("§b[스탯 평가] §f" + target.getName() + " §7(" + progress + ")"));
        editor.sendMessage(Component.text("§7====================================="));

        for (StatType type : StatType.values()) {
            TextComponent.Builder builder = Component.text();
            builder.append(Component.text(type.getDisplayName() + ": ", NamedTextColor.GRAY));
            for (int i = 1; i <= 10; i++) {
                final int value = i;
                TextComponent star = Component.text(i <= stats.getStat(type) ? "★" : "☆")
                        .color(i <= stats.getStat(type) ? NamedTextColor.YELLOW : NamedTextColor.GRAY)
                        .clickEvent(ClickEvent.runCommand("/dfadmin setstat " + type.name().toLowerCase() + " " + value));
                builder.append(star).append(Component.space());
            }
            editor.sendMessage(builder.build());
        }

        editor.sendMessage(Component.text("§7====================================="));
        TextComponent confirmButton = Component.text("[확인 및 다음]")
                .color(NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/dfadmin confirmstat"));
        editor.sendMessage(confirmButton);
        editor.sendMessage(" ");
    }
}