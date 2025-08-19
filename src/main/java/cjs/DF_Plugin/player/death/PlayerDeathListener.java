package cjs.DF_Plugin.player.death;

import org.bukkit.GameRule;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class PlayerDeathListener implements Listener {

    // 다른 플러그인이 인벤토리를 조작하기 전에 실행되도록 우선순위를 높게 설정
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Boolean keepInventory = player.getWorld().getGameRuleValue(GameRule.KEEP_INVENTORY);

        // keepInventory가 true일 때만 소실 저주를 수동으로 처리
        if (keepInventory != null && keepInventory) {
            PlayerInventory inventory = player.getInventory();
            ItemStack[] contents = inventory.getContents(); // 실제 인벤토리 배열

            for (int i = 0; i < contents.length; i++) {
                ItemStack item = contents[i];
                if (item != null && item.hasItemMeta() && item.getEnchantments().containsKey(Enchantment.VANISHING_CURSE)) {
                    inventory.setItem(i, null);
                }
            }
        }
    }
}