package cjs.DF_Plugin.pylon.beacongui.recon;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.clan.Clan;
import cjs.DF_Plugin.settings.GameConfigManager;
import cjs.DF_Plugin.util.PluginUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ReconManager implements Listener {

    private enum ReconState {
        READY_TO_LAUNCH,
        IN_AIR,
        LANDED
    }

    private final DF_Main plugin;
    private final Map<UUID, ReconState> reconPlayers = new ConcurrentHashMap<>(); // Leader UUID -> State
    private final Map<UUID, List<UUID>> reconParties = new ConcurrentHashMap<>(); // Leader UUID -> List of all party members' UUIDs
    private final Map<UUID, Long> returnTimers = new ConcurrentHashMap<>(); // Leader UUID -> Teleport Timestamp
    private static final String PREFIX = PluginUtils.colorize("&c[정찰] &f");

    public ReconManager(DF_Main plugin) {
        this.plugin = plugin;
        startReturnTask();
    }

    public void activateRecon(Player player) {
        GameConfigManager config = plugin.getGameConfigManager();

        // 1. Check if feature is enabled
        if (!config.getConfig().getBoolean("pylon.recon-firework.enabled", true)) {
            player.sendMessage(PREFIX + "정찰용 폭죽 기능이 비활성화되어 있습니다.");
            return;
        }
        // 2. Check if player is a clan leader
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null || !clan.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(PREFIX + "가문 대표만 정찰용 폭죽을 사용할 수 있습니다.");
            return;
        }

        // 3. Check cooldown
        long cooldownMillis = TimeUnit.HOURS.toMillis(config.getConfig().getInt("pylon.recon-firework.cooldown-hours", 12));
        long lastUsed = clan.getLastReconFireworkTime();
        if (System.currentTimeMillis() - lastUsed < cooldownMillis) {
            long remainingMillis = cooldownMillis - (System.currentTimeMillis() - lastUsed);
            String remainingTime = String.format("%02d시간 %02d분",
                    TimeUnit.MILLISECONDS.toHours(remainingMillis),
                    TimeUnit.MILLISECONDS.toMinutes(remainingMillis) % 60);
            player.sendMessage(PREFIX + "다음 정찰까지 " + remainingTime + " 남았습니다.");
            return;
        }

        // 4. Check if chestplate slot is empty
        ItemStack chestplate = player.getInventory().getChestplate();
        if (chestplate != null && !chestplate.getType().isAir()) {
            player.sendMessage(PREFIX + "겉날개를 장착하려면 갑옷 칸을 비워야 합니다.");
            return;
        }

        // All checks passed, activate recon mode
        player.closeInventory();
        player.getInventory().setChestplate(createReconElytra());
        clan.setLastReconFireworkTime(System.currentTimeMillis());
        plugin.getClanManager().getStorageManager().saveClan(clan);
        reconPlayers.put(player.getUniqueId(), ReconState.READY_TO_LAUNCH);

        player.sendMessage(PREFIX + "정찰 모드가 활성화되었습니다. 점프하여 발사하세요!");
    }

    @EventHandler
    public void onPlayerJump(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!reconPlayers.containsKey(player.getUniqueId()) || reconPlayers.get(player.getUniqueId()) != ReconState.READY_TO_LAUNCH) {
            return;
        }

        // 점프를 감지합니다. 플레이어가 땅에 붙어있을 때(fallDistance == 0) Y축 속도가 양수이면 점프로 간주합니다.
        // 기존의 player.isOnGround() 조건은 점프 직후 false가 되어 감지가 어려웠습니다.
        if (player.getVelocity().getY() > 0.1 && player.getFallDistance() == 0.0f) {
            launchPlayer(player);
        }
    }

    private void launchPlayer(Player leader) {
        reconPlayers.put(leader.getUniqueId(), ReconState.IN_AIR);

        Clan clan = plugin.getClanManager().getClanByPlayer(leader.getUniqueId());
        if (clan == null) return;

        List<Player> party = new ArrayList<>();
        party.add(leader);

        // 반경 5블록 내의 온라인 상태인 같은 가문원 찾기
        final double radiusSquared = 5 * 5;
        clan.getMembers().stream()
                .map(Bukkit::getPlayer)
                .filter(p -> p != null && p.isOnline() && !p.equals(leader))
                .filter(p -> p.getWorld().equals(leader.getWorld()) && p.getLocation().distanceSquared(leader.getLocation()) <= radiusSquared)
                .forEach(party::add);

        List<UUID> validPartyUUIDs = new ArrayList<>();
        Location launchLocation = leader.getLocation(); // 발사 기준점

        // 1. 참여 가능한 멤버를 필터링합니다.
        for (Player member : party) {
            ItemStack chest = member.getInventory().getChestplate();

            if (!member.equals(leader) && chest != null && !chest.getType().isAir()) {
                member.sendMessage(PREFIX + "갑옷을 벗지 않아 정찰에 참여할 수 없습니다.");
                leader.sendMessage(PREFIX + member.getName() + "님이 갑옷을 입고 있어 정찰에 참여하지 못했습니다.");
                continue; // 이 멤버는 파티에서 제외
            }
            validPartyUUIDs.add(member.getUniqueId());
        }

        // 2. 유효한 파티원들을 텔레포트 및 발사 준비시킵니다.
        for (int i = 0; i < validPartyUUIDs.size(); i++) {
            Player member = Bukkit.getPlayer(validPartyUUIDs.get(i));
            if (member == null) continue;

            reconPlayers.put(member.getUniqueId(), ReconState.IN_AIR);
            member.getInventory().setChestplate(createReconElytra());

            // 리더가 아닌 멤버들을 리더 위로 텔레포트하여 쌓습니다.
            if (!member.equals(leader)) {
                member.teleport(launchLocation.clone().add(0, i * 1.2, 0));
            }
        }

        // 3. 모든 파티원이 위치한 후, 동시에 발사 효과를 적용합니다.
        leader.getWorld().playSound(launchLocation, org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.5f, 1.0f);

        for (UUID memberUUID : validPartyUUIDs) {
            Player member = Bukkit.getPlayer(memberUUID);
            if (member == null) continue;
            
            // 위쪽으로 강한 벡터를 적용하여 발사되는 느낌을 줍니다.
            member.setVelocity(new org.bukkit.util.Vector(0, 4, 0));

            // 1틱 후에 활공 상태로 만듭니다.
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (member.isOnline() && !member.isOnGround()) {
                        member.setGliding(true);
                    }
                }
            }.runTaskLater(plugin, 1L);

            member.sendMessage(PREFIX + "발사!");
        }

        if (validPartyUUIDs.size() > 1) {
            reconParties.put(leader.getUniqueId(), validPartyUUIDs);
        }
    }

    @EventHandler
    public void onPlayerLand(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!reconPlayers.containsKey(player.getUniqueId()) || reconPlayers.get(player.getUniqueId()) != ReconState.IN_AIR) {
            return;
        }

        // 착지 감지
        if (player.isOnGround() && event.getTo().getY() <= event.getFrom().getY()) {
            // 착지한 플레이어가 파티의 리더인 경우
            if (reconParties.containsKey(player.getUniqueId())) {
                handlePartyLanding(player);
            } else { // 일반 참여자인 경우
                handlePassengerLanding(player);
            }
        }
    }

    /**
     * 정찰대 리더가 착지했을 때 호출됩니다. 파티원 전체의 귀환 타이머를 시작합니다.
     * @param leader 착지한 리더
     */
    private void handlePartyLanding(Player leader) {
        List<UUID> partyUUIDs = reconParties.remove(leader.getUniqueId());
        if (partyUUIDs == null) return; // 이미 처리된 경우

        int returnMinutes = plugin.getGameConfigManager().getConfig().getInt("pylon.recon-firework.return-duration-minutes", 1);

        for (UUID memberUUID : partyUUIDs) {
            reconPlayers.put(memberUUID, ReconState.LANDED);
            long returnTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(returnMinutes);
            returnTimers.put(memberUUID, returnTime);

            Player member = Bukkit.getPlayer(memberUUID);
            if (member != null) {
                member.getInventory().setChestplate(null); // 겉날개 제거
                member.sendMessage(PREFIX + "착지했습니다. " + returnMinutes + "분 후에 파일런으로 귀환합니다.");
            }
        }
    }

    /**
     * 정찰대 참여자(리더 제외)가 착지했을 때 호출됩니다.
     * @param passenger 착지한 참여자
     */
    private void handlePassengerLanding(Player passenger) {
        reconPlayers.put(passenger.getUniqueId(), ReconState.LANDED);
        passenger.getInventory().setChestplate(null); // 겉날개 제거
        passenger.sendMessage(PREFIX + "착지했습니다. 가문 대표가 착지하면 귀환 타이머가 시작됩니다.");
    }

    private void startReturnTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (returnTimers.isEmpty()) return;

                long now = System.currentTimeMillis();
                returnTimers.entrySet().removeIf(entry -> {
                    if (now >= entry.getValue()) {
                        Player player = Bukkit.getPlayer(entry.getKey());
                        if (player != null) {
                            // 귀환 시점에는 reconPlayers 맵에서 제거되었을 수 있으므로, 상태와 무관하게 귀환 처리
                            Clan clan = plugin.getClanManager().getClanByPlayer(entry.getKey());
                            if (clan != null && !clan.getPylonLocations().isEmpty()) {
                                String locString = clan.getPylonLocations().iterator().next();
                                Location pylonLoc = PluginUtils.deserializeLocation(locString);
                                if (pylonLoc != null) {
                                    player.teleport(pylonLoc.add(0.5, 1, 0.5));
                                    player.sendMessage(PREFIX + "파일런으로 귀환했습니다.");
                                }
                            }
                        }
                        reconPlayers.remove(entry.getKey());
                        return true; // Remove from map
                    }
                    return false;
                });
            }
        }.runTaskTimer(plugin, 0L, 20L); // Check every second
    }

    private ItemStack createReconElytra() {
        ItemStack elytra = new ItemStack(Material.ELYTRA);
        ItemMeta meta = elytra.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b정찰용 겉날개");
            meta.setLore(Collections.singletonList("§7하늘을 정찰하기 위한 특수 겉날개."));
            meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
            elytra.setItemMeta(meta);
        }
        return elytra;
    }
}