package com.odissea.tdm;

import java.util.Arrays;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class TDMKit {
    private final ItemStack[] storageContents;
    private final ItemStack[] armorContents;
    private final ItemStack[] extraContents;

    TDMKit(ItemStack[] storageContents, ItemStack[] armorContents, ItemStack[] extraContents) {
        this.storageContents = cloneContents(storageContents);
        this.armorContents = cloneContents(armorContents);
        this.extraContents = cloneContents(extraContents);
    }

    static TDMKit fromPlayer(Player player) {
        return new TDMKit(
                player.getInventory().getStorageContents(),
                player.getInventory().getArmorContents(),
                player.getInventory().getExtraContents()
        );
    }

    void apply(Player player) {
        player.getInventory().clear();
        player.getInventory().setStorageContents(cloneContents(storageContents));
        player.getInventory().setArmorContents(cloneContents(armorContents));
        player.getInventory().setExtraContents(cloneContents(extraContents));
        player.updateInventory();
    }

    ItemStack[] storageContents() {
        return cloneContents(storageContents);
    }

    ItemStack[] armorContents() {
        return cloneContents(armorContents);
    }

    ItemStack[] extraContents() {
        return cloneContents(extraContents);
    }

    private static ItemStack[] cloneContents(ItemStack[] contents) {
        return Arrays.stream(contents)
                .map(item -> item == null ? null : item.clone())
                .toArray(ItemStack[]::new);
    }
}
