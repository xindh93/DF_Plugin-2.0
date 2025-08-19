package cjs.DF_Plugin.player.stats;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.logging.Level;

public class StatsManager {

    private final DF_Main plugin;
    private final Map<UUID, MassRegistrationSession> massRegistrationSessions = new HashMap<>();
    private final Map<UUID, PlayerStats> statsCache = new ConcurrentHashMap<>();
    private final File statsFile;
    private FileConfiguration statsConfig;

    // 전체 스탯 평가를 위한 필드
    private boolean massEvaluationActive = false;
    private final Set<UUID> currentEvaluators = new HashSet<>();
    private final Map<UUID, Map<UUID, PlayerStats>> pendingEvaluations = new ConcurrentHashMap<>();

    public StatsManager(DF_Main plugin) {
        this.plugin = plugin;
        File playersFolder = new File(plugin.getDataFolder(), "players");
        if (!playersFolder.exists()) {
            playersFolder.mkdirs();
        }
        this.statsFile = new File(playersFolder, "player_stats.yml");
        loadStats();
    }

    public void loadStats() {
        if (!statsFile.exists()) {
            try {
                statsFile.createNewFile();
                plugin.getLogger().info("Created a new player_stats.yml file.");
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create player_stats.yml!", e);
                return; // 파일 생성 실패 시, 더 진행하지 않음
            }
        }
        statsConfig = YamlConfiguration.loadConfiguration(statsFile);
        statsCache.clear();

        ConfigurationSection playersSection = statsConfig.getConfigurationSection("players");
        if (playersSection != null) {
            for (String uuidStr : playersSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    PlayerStats stats = new PlayerStats();
                    for (StatType type : StatType.values()) {
                        int value = statsConfig.getInt("players." + uuidStr + "." + type.name(), 1);
                        stats.setStat(type, value);
                    }
                    stats.setKills(statsConfig.getInt("players." + uuidStr + ".kills", 0));
                    stats.setDeaths(statsConfig.getInt("players." + uuidStr + ".deaths", 0));
                    statsCache.put(uuid, stats);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in player_stats.yml: " + uuidStr);
                }
            }
        }
        plugin.getLogger().info(statsCache.size() + " player stats loaded.");
    }

    public void saveStats() {
        for (Map.Entry<UUID, PlayerStats> entry : statsCache.entrySet()) {
            UUID uuid = entry.getKey();
            PlayerStats stats = entry.getValue();
            for (Map.Entry<StatType, Integer> statEntry : stats.getAllStats().entrySet()) {
                statsConfig.set("players." + uuid.toString() + "." + statEntry.getKey().name(), statEntry.getValue());
            }
            statsConfig.set("players." + uuid.toString() + ".kills", stats.getKills());
            statsConfig.set("players." + uuid.toString() + ".deaths", stats.getDeaths());
        }
        try {
            statsConfig.save(statsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save player stats to file!", e);
        }
    }

    /**
     * 특정 플레이어의 스탯만 파일에 저장합니다.
     * @param playerUUID 저장할 플레이어의 UUID
     */
    public void savePlayerStats(UUID playerUUID) {
        PlayerStats stats = getPlayerStats(playerUUID);
        for (Map.Entry<StatType, Integer> statEntry : stats.getAllStats().entrySet()) {
            statsConfig.set("players." + playerUUID.toString() + "." + statEntry.getKey().name(), statEntry.getValue());
        }
        statsConfig.set("players." + playerUUID.toString() + ".kills", stats.getKills());
        statsConfig.set("players." + playerUUID.toString() + ".deaths", stats.getDeaths());
        try {
            statsConfig.save(statsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save player stats for " + playerUUID, e);
        }
    }

    public PlayerStats getPlayerStats(UUID playerUUID) {
        return statsCache.computeIfAbsent(playerUUID, k -> new PlayerStats());
    }

    public void setPlayerStats(UUID playerUUID, PlayerStats stats) {
        statsCache.put(playerUUID, stats);
    }

    public void openEditor(Player editor, Player target) {
        PlayerStats stats = getPlayerStats(target.getUniqueId());
        editor.openInventory(StatsEditor.create(target, stats));
    }

    /**
     * 인벤토리 기반 스탯 편집기(StatsListener)에서 사용하는 스탯 업데이트 메소드입니다.
     * @param target 스탯이 변경될 플레이어
     * @param type 변경할 스탯 종류
     * @param increment 증가(true) 또는 감소(false)
     */
    public void updateStatFromGUI(Player target, StatType type, boolean increment) {
        PlayerStats stats = getPlayerStats(target.getUniqueId());
        int currentValue = stats.getStat(type);
        stats.setStat(type, increment ? currentValue + 1 : currentValue - 1);
    }

    public void incrementKills(UUID playerUUID) {
        getPlayerStats(playerUUID).incrementKills();
    }

    public void incrementDeaths(UUID playerUUID) {
        getPlayerStats(playerUUID).incrementDeaths();
    }

    public void startMassRegistration(Player editor) {
        if (massEvaluationActive) {
            editor.sendMessage("§c이미 전체 스탯 평가가 진행 중입니다. 종료하려면 /dfadmin cancelstat 를 입력하세요.");
            return;
        }

        // 1. 평가자 목록 생성 (온라인 + 권한 보유)
        this.currentEvaluators.clear();
        this.currentEvaluators.addAll(Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("df.admin.register"))
                .map(Player::getUniqueId)
                .collect(Collectors.toList()));

        if (this.currentEvaluators.size() < 2) {
            editor.sendMessage("§c평균을 내기 위해 평가에 참여할 관리자가 2명 이상 필요합니다.");
            return;
        }

        // 2. 평가 대상 목록 생성 (스탯이 등록되지 않은 모든 플레이어)
        List<UUID> targets = plugin.getPlayerRegistryManager().getAllPlayerUUIDs().stream()
                .filter(uuid -> getPlayerStats(uuid).isDefault())
                .collect(Collectors.toList());

        if (targets.isEmpty()) {
            editor.sendMessage("§c새로 평가할 플레이어가 없습니다.");
            return;
        }

        // 3. 각 평가자에게 평가 세션 생성 및 시작 알림
        this.massEvaluationActive = true;
        this.pendingEvaluations.clear();
        massRegistrationSessions.clear();

        for (UUID evaluatorUUID : this.currentEvaluators) {
            Player evaluator = Bukkit.getPlayer(evaluatorUUID);
            if (evaluator == null) continue;

            // 각 평가자는 자신을 제외한 모든 대상을 평가합니다.
            List<UUID> personalTargets = new ArrayList<>(targets);
            personalTargets.remove(evaluatorUUID);

            if (personalTargets.isEmpty()) {
                evaluator.sendMessage("§a평가할 대상이 없습니다.");
                continue;
            }

            MassRegistrationSession session = new MassRegistrationSession(evaluatorUUID, personalTargets);
            if (!personalTargets.isEmpty()) {
                session.setCurrentStats(getPlayerStats(personalTargets.get(0)).clone());
            }
            massRegistrationSessions.put(evaluatorUUID, session);
            evaluator.sendMessage("§a총 " + personalTargets.size() + "명의 플레이어에 대한 전체 스탯 평가가 시작되었습니다.");
            displayNextPlayerForRegistration(evaluator);
        }
    }

    public void startSingleRegistration(Player editor, UUID targetUUID) {
        if (massEvaluationActive) {
            editor.sendMessage("§c현재 전체 스탯 평가가 진행 중입니다. 개별 평가는 할 수 없습니다.");
            return;
        }

        if (massRegistrationSessions.containsKey(editor.getUniqueId())) {
            editor.sendMessage("§c이미 스탯 평가를 진행 중입니다. 종료하려면 /dfadmin cancelstat 를 입력하세요.");
            return;
        }
        if (editor.getUniqueId().equals(targetUUID)) {
            editor.sendMessage("§c자기 자신은 평가할 수 없습니다.");
            return;
        }

        List<UUID> targets = Arrays.asList(targetUUID);
        MassRegistrationSession session = new MassRegistrationSession(editor.getUniqueId(), targets);
        session.setCurrentStats(getPlayerStats(targetUUID).clone());

        massRegistrationSessions.put(editor.getUniqueId(), session);
        editor.sendMessage("§a" + Bukkit.getOfflinePlayer(targetUUID).getName() + "님에 대한 스탯 평가를 시작합니다.");
        displayNextPlayerForRegistration(editor);
    }

    private void displayNextPlayerForRegistration(Player editor) {
        MassRegistrationSession session = massRegistrationSessions.get(editor.getUniqueId());
        if (session == null) return;

        UUID targetUUID = session.getCurrentPlayer();
        if (targetUUID == null) {
            // This editor has finished their list.
            editor.sendMessage("§a당신의 스탯 평가를 모두 완료했습니다!");
            massRegistrationSessions.remove(editor.getUniqueId());
            checkAndEndMassEvaluation();
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        StatsChatEditor.sendEditor(editor, target, session.getCurrentStats(), session.getProgress());
    }

    public void updateStatInSession(Player editor, StatType type, int value) {
        MassRegistrationSession session = massRegistrationSessions.get(editor.getUniqueId());
        if (session == null || session.getCurrentStats() == null) return;

        session.getCurrentStats().setStat(type, value);
        displayNextPlayerForRegistration(editor); // 변경사항을 반영하여 UI를 다시 표시
    }

    public void confirmAndNext(Player editor) {
        MassRegistrationSession session = massRegistrationSessions.get(editor.getUniqueId());
        if (session == null) return;

        UUID currentTargetUUID = session.getCurrentPlayer();
        if (currentTargetUUID != null) {
            if (massEvaluationActive) {
                // 전체 평가 중일 경우, 임시 저장소에 스탯 제출
                pendingEvaluations.computeIfAbsent(currentTargetUUID, k -> new ConcurrentHashMap<>())
                        .put(editor.getUniqueId(), session.getCurrentStats());
                editor.sendMessage("§a" + Bukkit.getOfflinePlayer(currentTargetUUID).getName() + "님의 스탯을 제출했습니다.");
                checkAndFinalizeStats(currentTargetUUID);
            } else {
                // 단일 평가일 경우, 즉시 저장
                statsCache.put(currentTargetUUID, session.getCurrentStats());
                saveStats();
                editor.sendMessage("§a" + Bukkit.getOfflinePlayer(currentTargetUUID).getName() + "님의 스탯을 저장했습니다.");
            }
        }

        // 다음 플레이어로 이동
        session.next();
        if (session.isFinished()) {
            editor.sendMessage("§a당신의 스탯 평가를 모두 완료했습니다!");
            massRegistrationSessions.remove(editor.getUniqueId());
            checkAndEndMassEvaluation();
        } else {
            session.setCurrentStats(getPlayerStats(session.getCurrentPlayer()).clone());
            displayNextPlayerForRegistration(editor);
        }
    }

    private void checkAndFinalizeStats(UUID targetUUID) {
        Map<UUID, PlayerStats> submissions = pendingEvaluations.get(targetUUID);
        if (submissions == null) return;

        // 이 대상을 평가해야 하는 평가자 목록
        Set<UUID> expectedEvaluators = new HashSet<>(this.currentEvaluators);
        expectedEvaluators.remove(targetUUID); // 자신은 평가하지 않음

        if (submissions.keySet().containsAll(expectedEvaluators)) {
            // 모든 평가가 완료되었으므로 평균 계산
            PlayerStats finalStats = new PlayerStats();
            Map<StatType, Integer> totalScores = new HashMap<>();
            int submissionCount = submissions.size();

            for (PlayerStats submittedStat : submissions.values()) {
                for (Map.Entry<StatType, Integer> entry : submittedStat.getAllStats().entrySet()) {
                    totalScores.merge(entry.getKey(), entry.getValue(), Integer::sum);
                }
            }

            for (Map.Entry<StatType, Integer> entry : totalScores.entrySet()) {
                int average = (int) Math.round((double) entry.getValue() / submissionCount);
                finalStats.setStat(entry.getKey(), Math.max(1, Math.min(10, average))); // 1~10 범위 보장
            }

            // 최종 스탯 저장
            setPlayerStats(targetUUID, finalStats);
            saveStats();

            // 임시 데이터 정리
            pendingEvaluations.remove(targetUUID);

            // 모든 평가자에게 알림
            String targetName = Bukkit.getOfflinePlayer(targetUUID).getName();
            String message = "§b[스탯 평가] §e" + targetName + "§b님의 스탯 평가가 완료되어 최종 스탯이 등록되었습니다.";
            currentEvaluators.forEach(uuid -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) p.sendMessage(message);
            });
        }
    }

    private void checkAndEndMassEvaluation() {
        // 모든 평가자의 세션이 종료되었는지 확인
        if (massEvaluationActive && massRegistrationSessions.isEmpty()) {
            massEvaluationActive = false;
            currentEvaluators.clear();
            Bukkit.broadcastMessage("§b[스탯 평가] §a모든 플레이어에 대한 스탯 평가가 종료되었습니다.");
        }
    }

    public void endMassRegistration(Player editor) {
        // This now cancels the entire mass evaluation
        if (!massEvaluationActive) {
            editor.sendMessage("§c진행 중인 전체 스탯 평가가 없습니다.");
            return;
        }
        if (!currentEvaluators.contains(editor.getUniqueId())) {
            editor.sendMessage("§c현재 진행중인 평가의 참여자가 아니므로 종료할 수 없습니다.");
            return;
        }

        String message = "§c" + editor.getName() + "님에 의해 전체 스탯 평가가 강제 종료되었습니다.";
        currentEvaluators.forEach(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(message);
        });

        massRegistrationSessions.clear();
        pendingEvaluations.clear();
        currentEvaluators.clear();
        massEvaluationActive = false;
    }
}