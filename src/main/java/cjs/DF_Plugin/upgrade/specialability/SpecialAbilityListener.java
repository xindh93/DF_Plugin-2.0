package cjs.DF_Plugin.upgrade.specialability;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class SpecialAbilityListener implements Listener {

    private final DF_Main plugin;
    private final SpecialAbilityManager specialAbilityManager;
    private final cjs.DF_Plugin.upgrade.UpgradeManager upgradeManager;

    public SpecialAbilityListener(DF_Main plugin) {
        this.plugin = plugin;
        this.specialAbilityManager = plugin.getSpecialAbilityManager();
        this.upgradeManager = plugin.getUpgradeManager();
    }

    // --- 상태 정리(Cleanup) 이벤트 핸들러 ---

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        specialAbilityManager.cleanupPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        specialAbilityManager.cleanupPlayer(event.getEntity());
    }

    // --- 능력 발동 이벤트 핸들러 ---

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;

        // 각 능력 클래스가 어떤 클릭에 반응할지 직접 결정하도록 이벤트를 전달합니다.
        // 이 방식은 '뇌창'의 좌클릭 발동과 '역류'의 우클릭 발동을 모두 지원하는 등 유연성을 높여줍니다.
        specialAbilityManager.getAbilityFromItem(item).ifPresent(ability -> {
            // 리스너는 이벤트를 전달할 뿐, 모든 쿨다운 및 사용 조건 확인은 각 능력 클래스가 책임집니다.
            ability.onPlayerInteract(event, player, item);
        });
    }

    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHandItem = event.getMainHandItem();
        ItemStack offHandItem = event.getOffHandItem();

        ItemStack tridentToSwitch = null;

        // 주 손 또는 부 손에 10강 삼지창이 있는지 확인합니다.
        if (mainHandItem != null && mainHandItem.getType() == Material.TRIDENT && upgradeManager.getUpgradeLevel(mainHandItem) >= cjs.DF_Plugin.upgrade.UpgradeManager.MAX_UPGRADE_LEVEL) {
            tridentToSwitch = mainHandItem;
        } else if (offHandItem != null && offHandItem.getType() == Material.TRIDENT && upgradeManager.getUpgradeLevel(offHandItem) >= cjs.DF_Plugin.upgrade.UpgradeManager.MAX_UPGRADE_LEVEL) {
            tridentToSwitch = offHandItem;
        }

        // 모드를 변경할 삼지창을 찾았다면
        if (tridentToSwitch != null) {
            // 실제 아이템 스왑을 막고, 모드 변경 로직만 실행합니다.
            event.setCancelled(true);
            upgradeManager.switchTridentMode(player, tridentToSwitch);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // --- 공격자(Attacker)의 능력 처리 ---
        Player attacker = null;
        ItemStack weapon = null;

        // 공격의 주체를 찾습니다. (직접 공격 플레이어 또는 투사체 발사자)
        if (event.getDamager() instanceof Player p) {
            attacker = p;
            weapon = attacker.getInventory().getItemInMainHand();
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Player p) {
            attacker = p;
            // 투사체 종류에 따라 사용한 무기를 추정합니다.
            if (projectile instanceof Arrow) {
                ItemStack mainHand = attacker.getInventory().getItemInMainHand();
                ItemStack offHand = attacker.getInventory().getItemInOffHand();
                if (mainHand.getType() == Material.BOW || mainHand.getType() == Material.CROSSBOW) {
                    weapon = mainHand;
                } else if (offHand.getType() == Material.BOW || offHand.getType() == Material.CROSSBOW) {
                    weapon = offHand;
                }
            } else if (projectile instanceof Trident) {
                // '뇌창' 발사체인지 메타데이터로 먼저 확인합니다.
                if (projectile.hasMetadata("df_lightning_spear_projectile")) {
                    ISpecialAbility ability = specialAbilityManager.getRegisteredAbility("lightning_spear");
                    if (ability != null) {
                        // '뇌창' 능력의 onDamageByEntity를 직접 호출합니다.
                        // 이 경우, 플레이어가 손에 무엇을 들고 있는지는 중요하지 않습니다.
                        ability.onDamageByEntity(event, p, null);
                        // '뇌창' 이벤트가 처리되었으므로, 아래의 다른 삼지창 능력(역류 등) 처리를 건너뜁니다.
                        return;
                    }
                }
                
                ItemStack mainHand = attacker.getInventory().getItemInMainHand();
                ItemStack offHand = attacker.getInventory().getItemInOffHand();

                if (mainHand.getType() == Material.TRIDENT) {
                    weapon = mainHand;
                } else if (offHand.getType() == Material.TRIDENT) {
                    weapon = offHand;
                }
            }
        }

        // 공격자와 사용한 무기가 식별된 경우, 능력 발동
        if (attacker != null && weapon != null) {
            final Player finalAttacker = attacker; // 람다에서 사용하기 위해 final 변수에 할당
            final ItemStack finalWeapon = weapon; // weapon은 이미 effectively final이지만, 명확성을 위해 final로 선언합니다.
            specialAbilityManager.getAbilityFromItem(finalWeapon)
                    .ifPresent(ability -> ability.onDamageByEntity(event, finalAttacker, finalWeapon));
        }

        // --- 피격자(Victim)의 능력 처리 ---
        if (event.getEntity() instanceof Player victim) {
            // 1. 갑옷 능력 (항상 방어/유틸 능력으로 간주)
            for (ItemStack armor : victim.getInventory().getArmorContents()) {
                if (armor == null || armor.getType() == Material.AIR) continue;
                specialAbilityManager.getAbilityFromItem(armor)
                        .ifPresent(ability -> ability.onDamageByEntity(event, victim, armor));
            }

            // 2. 양손에 든 아이템의 능력 (방어/유틸리티 아이템만)
            handleVictimHeldItem(event, victim, victim.getInventory().getItemInMainHand());
            handleVictimHeldItem(event, victim, victim.getInventory().getItemInOffHand());
        }
    }

    /**
     * 피격자가 손에 든 아이템의 방어/유틸리티 능력을 처리합니다.
     * 공격용 무기에 붙은 능력은 발동되지 않도록, 특정 아이템 타입(방패, 낚싯대 등)만 허용합니다.
     */
    private void handleVictimHeldItem(EntityDamageByEntityEvent event, Player victim, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        Material itemType = item.getType();

        // 피격 시 발동해야 하는 방어/유틸리티 아이템 타입만 명시적으로 허용합니다.
        if (itemType == Material.SHIELD || itemType == Material.FISHING_ROD) {
            specialAbilityManager.getAbilityFromItem(item)
                    .ifPresent(ability -> ability.onDamageByEntity(event, victim, item));
        }
    }

    @EventHandler
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        // 플레이어가 착용한 모든 장비를 확인하여 능력을 찾습니다.
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null) {
                specialAbilityManager.getAbilityFromItem(armor).ifPresent(ability -> {
                    ability.onPlayerToggleFlight(event, player, armor);
                });
            }
        }
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();

        // 갑옷 부위의 능력을 확인합니다 (레깅스, 부츠 등).
        // 슈퍼 점프(레깅스)와 공중 대시(부츠)는 모두 웅크리기로 발동되므로,
        // 이 핸들러에서 모든 갑옷을 순회하며 처리하는 것이 효율적입니다.
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null) {
                specialAbilityManager.getAbilityFromItem(armor)
                        .ifPresent(ability -> ability.onPlayerToggleSneak(event, player, armor));
            }
        }

        // 손에 든 아이템 능력 (그래플링 훅 등)
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        specialAbilityManager.getAbilityFromItem(itemInHand)
                .ifPresent(ability -> ability.onPlayerToggleSneak(event, player, itemInHand));
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        // 이중 도약(더블 점프) 관련 비행 제어 로직을 제거합니다.
        // 새로운 '공중 대시'는 웅크리기로 발동되므로, onPlayerMove에서 비행 상태를 제어할 필요가 없습니다.
        // 이동 시 발동하는 모든 장비의 특수 능력 처리 (예: 재생, 슈퍼점프 상태 초기화)
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            specialAbilityManager.getAbilityFromItem(armor)
                    .ifPresent(ability -> ability.onPlayerMove(event, player, armor));
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        // 플레이어가 착용한 모든 장비의 능력을 확인 (예: 낙하 데미지 면역)
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            specialAbilityManager.getAbilityFromItem(armor).ifPresent(ability -> {
                ability.onEntityDamage(event, player, armor);
            });
        }
    }

    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        ItemStack rod = null;

        if (mainHand.getType() == Material.FISHING_ROD) rod = mainHand;
        else if (offHand.getType() == Material.FISHING_ROD) rod = offHand;

        if (rod == null) return;

        final ItemStack finalRod = rod;
        specialAbilityManager.getAbilityFromItem(finalRod).ifPresent(ability -> {
            ability.onPlayerFish(event, player, finalRod);
        });
    }

    @EventHandler
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack bow = event.getBow();
        if (bow == null) return;

        specialAbilityManager.getAbilityFromItem(bow).ifPresent(ability -> {
            ability.onEntityShootBow(event, player, bow);
        });
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) return;

        // 삼지창 투척의 경우, 양손을 모두 확인해야 합니다.
        if (event.getEntity() instanceof Trident) {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();

            // 주 손에 삼지창이 있고, 능력이 있다면 처리합니다.
            if (mainHand.getType() == Material.TRIDENT) {
                specialAbilityManager.getAbilityFromItem(mainHand).ifPresent(ability -> {
                    ability.onProjectileLaunch(event, player, mainHand);
                });
            }

            // 이벤트가 아직 취소되지 않았고, 부 손에 삼지창이 있다면 처리합니다.
            if (!event.isCancelled() && offHand.getType() == Material.TRIDENT) {
                specialAbilityManager.getAbilityFromItem(offHand).ifPresent(ability -> {
                    ability.onProjectileLaunch(event, player, offHand);
                });
            }
        }
    }

    @EventHandler
    public void onPlayerRiptide(PlayerRiptideEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        specialAbilityManager.getAbilityFromItem(item).ifPresent(ability -> {
            ability.onPlayerRiptide(event, player, item);
        });
    }

    @EventHandler
    public void onPlayerJump(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        // 더블 점프는 부츠에 귀속된 능력이므로 부츠 아이템을 확인합니다.
        ItemStack boots = player.getInventory().getBoots();
        specialAbilityManager.getAbilityFromItem(boots).ifPresent(ability -> {
            ability.onPlayerJump(event, player, boots);
        });
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) return;
        if (!(trident.getShooter() instanceof Player player)) return;
    }
}