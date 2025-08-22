package cjs.DF_Plugin.util;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.pylon.clan.Clan;
import cjs.DF_Plugin.pylon.clan.ClanManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class PlayerTagManager {

    private final DF_Main plugin;
    private final ClanManager clanManager;

    public PlayerTagManager(DF_Main plugin, ClanManager clanManager) {
        this.plugin = plugin;
        this.clanManager = clanManager;
    }

    /**
     * 플레이어의 클랜 정보를 바탕으로 이름표와 탭리스트를 업데이트합니다.
     * @param player 업데이트할 플레이어
     */
    public void updatePlayerTag(Player player) {
        Clan clan = clanManager.getClanByPlayer(player.getUniqueId());

        if (clan == null) {
            removePlayerTag(player);
            return;
        }

        String prefix = clan.getColor() + "[" + clan.getName() + "] ";
        // 팀 이름은 16자를 넘을 수 없으며, 고유해야 합니다.
        String teamName = "dfc_" + clan.getName().toLowerCase().replaceAll("[^a-z0-9]", "");
        if (teamName.length() > 16) {
            teamName = teamName.substring(0, 16);
        }

        // 탭 리스트 이름 업데이트
        player.setPlayerListName(prefix + "§r" + player.getName());

        // 이름표 업데이트 (스코어보드 팀 사용)
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }

        team.setPrefix(prefix);
        team.setColor(ChatColor.WHITE); // 이름 색상을 흰색으로 고정하여 접두사 색상만 적용되도록 함
        team.setCanSeeFriendlyInvisibles(true); // 투명화한 팀원을 볼 수 있게 설정

        if (!team.hasEntry(player.getName())) {
            team.addEntry(player.getName());
        }
    }

    /**
     * 플레이어에게 적용된 모든 클랜 태그를 제거합니다.
     * @param player 태그를 제거할 플레이어
     */
    public void removePlayerTag(Player player) {
        player.setPlayerListName(player.getName());

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getEntryTeam(player.getName());
        if (team != null && team.getName().startsWith("dfc_")) {
            team.removeEntry(player.getName());
        }
    }

    /**
     * 클랜 해체 시 사용된 스코어보드 팀을 정리합니다.
     * @param clan 해체된 클랜
     */
    public void cleanupClanTeam(Clan clan) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = "dfc_" + clan.getName().toLowerCase().replaceAll("[^a-z0-9]", "");
        if (teamName.length() > 16) {
            teamName = teamName.substring(0, 16);
        }
        Team team = scoreboard.getTeam(teamName);
        if (team != null) {
            team.unregister();
        }
    }
}