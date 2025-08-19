package cjs.DF_Plugin.upgrade.specialability.passive;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.settings.GameConfigManager;
import cjs.DF_Plugin.upgrade.specialability.impl.LightningSpearAbility;
import cjs.DF_Plugin.upgrade.UpgradeManager;
import org.bukkit.Particle;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.persistence.PersistentDataType;

import java.util.Set;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TridentPassiveListener implements Listener {

    private final DF_Main plugin;
    public static final String TRIDENT_PASSIVE_LEVEL_KEY = "trident_passive_level";
    private final Set<UUID> launchedTridents = ConcurrentHashMap.newKeySet();

    public TridentPassiveListener(DF_Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player) || !(event.getEntity() instanceof Trident trident))
            return;

        // [버그 수정] '뇌창' 능력(좌클릭)으로 발사된 삼지창은 패시브가 발동하지 않도록 합니다.
        // '뇌창' 발사 직전에 플레이어에게 설정된 임시 메타데이터를 확인합니다.
        if (player.hasMetadata("df_is_firing_special")) {
            return;
        }

        // 중복 실행 방지
        if (launchedTridents.contains(player.getUniqueId())) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != org.bukkit.Material.TRIDENT) {
            item = player.getInventory().getItemInOffHand();
            if (item.getType() != org.bukkit.Material.TRIDENT) return;
        }

        int level = plugin.getUpgradeManager().getUpgradeLevel(item);
        if (level > 0) {
            trident.setMetadata(TRIDENT_PASSIVE_LEVEL_KEY, new FixedMetadataValue(plugin, level));
            launchAdditionalTridents(player, level, trident.getLocation(), trident.getVelocity());

            // 중복 방지를 위해 플래그 설정 후 1초 뒤 제거
            launchedTridents.add(player.getUniqueId());
            new BukkitRunnable() {
                @Override
                public void run() {
                    launchedTridents.remove(player.getUniqueId());
                }
            }.runTaskLater(plugin, 20L);
        }
    }

    @EventHandler
    public void onPlayerRiptide(PlayerRiptideEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() != org.bukkit.Material.TRIDENT) {
            return;
        }

        int level = plugin.getUpgradeManager().getUpgradeLevel(item);
        if (level > 0) {
            // 이전 버전과 동일하게 플레이어가 바라보는 방향으로 발사합니다.
            Vector velocity = player.getLocation().getDirection().multiply(2.0);
            launchAdditionalTridents(player, level, player.getLocation(), velocity);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDamageByTrident(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Trident trident)) {
            return;
        }

        // 추가 삼지창인지 확인 (메타데이터 사용)
        if (!trident.hasMetadata(TRIDENT_PASSIVE_LEVEL_KEY)) {
            return;
        }

        // 1. 시전자 자신에게 피해를 주지 않도록 설정
        if (trident.getShooter() != null && trident.getShooter().equals(event.getEntity())) {
            event.setCancelled(true);
            return;
        }

        // 2. 다른 추가 삼지창에게 피해를 주지 않도록 설정
        if (event.getEntity() instanceof Trident && event.getEntity().hasMetadata(TRIDENT_PASSIVE_LEVEL_KEY)) {
            event.setCancelled(true);
            return;
        }

        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        int level = trident.getMetadata(TRIDENT_PASSIVE_LEVEL_KEY).get(0).asInt();
        if (level <= 0) return;

        GameConfigManager configManager = plugin.getGameConfigManager();
        int duration = (int) (configManager.getConfig().getDouble("upgrade.generic-bonuses.trident.slowness-duration-seconds", 3.0) * 20);
        int amplifier = configManager.getConfig().getInt("upgrade.generic-bonuses.trident.slowness-level", 2) - 1;
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, amplifier));
    }

    public static void launchAdditionalTridents(Player player, int level, Location origin, Vector direction) {
        DF_Main plugin = DF_Main.getInstance();
        World world = origin.getWorld();
        Random random = new Random();

        GameConfigManager configManager = plugin.getGameConfigManager();
        double spreadRadius = configManager.getConfig().getDouble("upgrade.generic-bonuses.trident.spread-radius", 3.0);
        double spreadVelocity = configManager.getConfig().getDouble("upgrade.generic-bonuses.trident.spread-velocity-multiplier", 0.1);

        // 동시성 문제를 피하기 위해 동기적으로 실행합니다.
        for (int i = 0; i < level; i++) {
            double offsetX = (random.nextDouble() - 0.5) * spreadRadius;
            double offsetY = (random.nextDouble() - 0.5) * spreadRadius;
            double offsetZ = (random.nextDouble() - 0.5) * spreadRadius;

            Location spawnLocation = origin.clone().add(offsetX, offsetY, offsetZ);

            Trident additionalTrident = (Trident) world.spawnEntity(spawnLocation, EntityType.TRIDENT);
            additionalTrident.setShooter(player);
            additionalTrident.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);

            // 이름 제거
            additionalTrident.setCustomName(null);
            additionalTrident.setCustomNameVisible(false);

            // 추가 투사체에도 강화 레벨을 기록하여, 피격 시 둔화 효과가 적용되도록 합니다.
            additionalTrident.setMetadata(TRIDENT_PASSIVE_LEVEL_KEY, new FixedMetadataValue(plugin, level));

            // 기본 방향에 무작위성을 더해 투사체를 퍼뜨립니다.
            Vector finalVelocity = direction.clone();
            finalVelocity.add(new Vector(offsetX, offsetY, offsetZ).normalize().multiply(spreadVelocity));
            additionalTrident.setVelocity(finalVelocity);

            // 추가 투사체도 빠르게 사라지도록 스케줄을 겁니다.
            scheduleTridentDespawn(additionalTrident);
        }
    }

    /**
     * 삼지창이 땅이나 엔티티에 박혔을 때 빠르게 사라지도록 스케줄을 등록합니다.
     * @param trident 사라지게 할 삼지창
     */
    public static void scheduleTridentDespawn(Trident trident) {
        new BukkitRunnable() {
            @Override
            public void run() {
                // 삼지창이 유효하지 않거나, 이미 사라졌거나, 아직 공중에 떠 있다면 작업을 계속합니다.
                if (!trident.isValid() || trident.isDead()) {
                    this.cancel();
                    return;
                }

                // 물 속에 있을 때 속도 저하를 상쇄하여 물을 통과하는 것처럼 보이게 합니다.
                // 이 값은 실험적으로 조정될 수 있습니다.
                if (trident.isInWater()) {
                    trident.setVelocity(trident.getVelocity().multiply(1.2375));
                }

                // 삼지창이 땅에 박혔는지 확인합니다.
                if (trident.isOnGround()) {
                    // 땅에 박힌 후 잠시 후(예: 0.1초)에 사라지게 하여 시각적으로 자연스럽게 만듭니다.
                    trident.getWorld().spawnParticle(Particle.CRIT, trident.getLocation(), 10, 0.1, 0.1, 0.1, 0.1);
                    trident.remove();
                    this.cancel();
                }
            }
        }.runTaskTimer(DF_Main.getInstance(), 1L, 1L); // 1틱 후부터 매 틱마다 확인하여 즉각적인 반응
    }
}