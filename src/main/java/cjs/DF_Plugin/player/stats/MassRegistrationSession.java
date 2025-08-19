package cjs.DF_Plugin.player.stats;

import java.util.List;
import java.util.UUID;

/**
 * '/df register all' 명령어를 통해 여러 플레이어의 스탯을 순차적으로 평가하는 세션을 관리하는 클래스입니다.
 */
public class MassRegistrationSession {
    private final UUID editorUUID;
    private final List<UUID> targets;
    private int currentIndex = 0;
    private PlayerStats currentStats;

    public MassRegistrationSession(UUID editorUUID, List<UUID> targets) {
        this.editorUUID = editorUUID;
        this.targets = targets;
    }

    public void next() {
        currentIndex++;
    }

    public boolean isFinished() {
        return currentIndex >= targets.size();
    }

    public UUID getCurrentPlayer() {
        if (!isFinished()) {
            return targets.get(currentIndex);
        }
        return null;
    }

    public PlayerStats getCurrentStats() {
        return currentStats;
    }

    public void setCurrentStats(PlayerStats currentStats) {
        this.currentStats = currentStats;
    }

    public String getProgress() {
        int displayIndex = Math.min(currentIndex + 1, targets.size());
        return "(" + displayIndex + "/" + targets.size() + ")";
    }
}