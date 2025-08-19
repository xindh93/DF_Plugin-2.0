package cjs.DF_Plugin.items;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.clan.Clan;
import cjs.DF_Plugin.pylon.item.PylonItemFactory;
import cjs.DF_Plugin.util.PluginUtils;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

public class SpecialItemListener implements Listener {

    private final DF_Main plugin;

    public SpecialItemListener(DF_Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        // 워든 처치 시 흑요석 포션 드롭
        if (event.getEntity() instanceof Warden) {
            event.getDrops().add(ObsidianPotion.createObsidianPotion());
        }

        // 엔더 드래곤 처치 시 마스터 컴퍼스 지급
        if (event.getEntity() instanceof EnderDragon) {
            Player killer = event.getEntity().getKiller();
            if (killer != null) {
                killer.getInventory().addItem(MasterCompass.createMasterCompass());
                killer.sendMessage(PluginUtils.colorize("&5[알림] &f드래곤의 힘이 깃든 나침반을 획득했습니다."));
            }
        }

        // 위더 처치 시
        if (event.getEntityType() == EntityType.WITHER) {
            // 기존 드롭 아이템(네더의 별)을 제거합니다.
            event.getDrops().removeIf(itemStack -> itemStack.getType() == Material.NETHER_STAR);
            // 보조 파일런 코어를 드롭합니다.
            event.getDrops().add(PylonItemFactory.createAuxiliaryCore());
            plugin.getLogger().info("위더가 처치되어 보조 파일런 코어를 드롭했습니다. (기존 네더의 별 드롭은 제거됨)");
        }

        // 엘더 가디언 처치 시
        if (event.getEntityType() == EntityType.ELDER_GUARDIAN) {
            // 기존 드롭 아이템(스펀지)을 제거합니다.
            event.getDrops().removeIf(itemStack -> itemStack.getType() == Material.SPONGE || itemStack.getType() == Material.WET_SPONGE);
            // 귀환 주문서를 드롭합니다.
            event.getDrops().add(PylonItemFactory.createReturnScroll());
            plugin.getLogger().info("엘더 가디언이 처치되어 귀환 주문서를 드롭했습니다. (기존 스펀지 드롭은 제거됨)");
        }
    }

    @EventHandler
    public void onCompassUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (!MasterCompass.isMasterCompass(item)) {
            return;
        }

        event.setCancelled(true);

        Clan playerClan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (playerClan == null) {
            player.sendMessage(PluginUtils.colorize("&c가문에 소속되어 있지 않아 사용할 수 없습니다."));
            return;
        }

        // 가장 가까운 '적' 가문의 '주 파일런' 찾기
        Optional<Location> nearestEnemyPylon = plugin.getClanManager().getAllClans().stream()
                .filter(clan -> !clan.equals(playerClan)) // 자신의 가문 제외
                .map(Clan::getMainPylonLocationObject) // 각 가문의 주 파일런 위치 가져오기
                .flatMap(Optional::stream) // Stream<Optional<Location>> -> Stream<Location>
                .filter(loc -> Objects.equals(loc.getWorld(), player.getWorld())) // 같은 월드에 있는 파일런만
                .min(Comparator.comparingDouble(loc -> player.getLocation().distanceSquared(loc))); // 가장 가까운 위치 찾기

        if (nearestEnemyPylon.isPresent()) {
            Location target = nearestEnemyPylon.get();
            item.setAmount(item.getAmount() - 1); // 아이템 소모
            player.sendMessage(PluginUtils.colorize("&a[마스터 컴퍼스] &f가장 가까운 적의 기운을 감지하여 방향을 가리킵니다."));
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
            spawnParticleTrail(player.getEyeLocation(), target);
        } else {
            player.sendMessage(PluginUtils.colorize("&c[마스터 컴퍼스] &f주변에서 적의 파일런을 찾을 수 없습니다."));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f);
        }
    }

    private void spawnParticleTrail(Location start, Location end) {
        World world = start.getWorld();
        if (world == null) return;

        // Y좌표를 무시한 수평 방향 벡터를 계산합니다.
        Vector direction = end.toVector().subtract(start.toVector());
        direction.setY(0);
        direction.normalize();

        // 나선형 및 원형 효과를 위한 수직 벡터 계산
        Vector up = new Vector(0, 1, 0);
        Vector side = direction.clone().crossProduct(up).normalize();

        new BukkitRunnable() {
            private int ticks = 0;
            private final int durationTicks = 40; // 2초 동안 파티클 효과 지속

            @Override
            public void run() {
                // 지속 시간이 지나면 작업을 취소합니다.
                if (ticks++ > durationTicks) {
                    this.cancel();
                    return;
                }

                // 시간이 지남에 따라 파티클 생성 확률을 줄여 서서히 사라지는 효과를 만듭니다.
                double spawnChance = 1.0 - ((double) ticks / durationTicks);

                // 시작점부터 목표 지점까지 파티클을 생성합니다.
                for (double d = 1.0; d < 40.0; d += 0.5) { // 최대 40블록, 0.5블록 간격
                    if (Math.random() > spawnChance) {
                        continue;
                    }

                    Location centerTrail = start.clone().add(direction.clone().multiply(d));

                    // 1. 중앙 파티클: 지름 1(반지름 0.5)의 보라색 원
                    double circleRadius = 0.5;
                    for (int i = 0; i < 6; i++) { // 6개의 점으로 원을 표현
                        double circleAngle = ((double) i / 6) * 2 * Math.PI;
                        Vector circleOffset = side.clone().multiply(circleRadius * Math.cos(circleAngle))
                                .add(up.clone().multiply(circleRadius * Math.sin(circleAngle)));
                        Particle.DustOptions dustOptions = new Particle.DustOptions(Color.PURPLE, 0.7f);
                        world.spawnParticle(Particle.DUST, centerTrail.clone().add(circleOffset), 1, 0, 0, 0, 0, dustOptions, true);
                    }

                    // 2. 나선형 파티클 (SOUL_FIRE_FLAME) - 이중 나선
                    double spiralRadius = 1.2; // 나선 반경
                    double spiralAngle = d * 0.8; // 거리에 따라 각도 변경
                    Vector spiralOffset1 = side.clone().multiply(spiralRadius * Math.cos(spiralAngle))
                            .add(up.clone().multiply(spiralRadius * Math.sin(spiralAngle)));
                    Vector spiralOffset2 = side.clone().multiply(spiralRadius * Math.cos(spiralAngle + Math.PI)) // 180도 반대편
                            .add(up.clone().multiply(spiralRadius * Math.sin(spiralAngle + Math.PI)));

                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, centerTrail.clone().add(spiralOffset1), 1, 0, 0, 0, 0);
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, centerTrail.clone().add(spiralOffset2), 1, 0, 0, 0, 0);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }
}