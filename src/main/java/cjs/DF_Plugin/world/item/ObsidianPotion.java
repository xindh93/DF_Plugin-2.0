package cjs.DF_Plugin.world.item;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;

public class ObsidianPotion {

    public static ItemStack createObsidianPotion() {
        ItemStack potion = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§5흑요석 포션");
            meta.addCustomEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 216000, 0, false, true), true); // 3 hours
            meta.setColor(Color.fromRGB(75, 0, 130)); // Indigo/Purple
            meta.setLore(Arrays.asList("§7워든의 힘이 담긴 포션입니다.", "§7마시면 3시간 동안 지옥의 불길을 견딜 수 있습니다."));
            potion.setItemMeta(meta);
        }
        return potion;
    }
}