package com.odissea.tdm;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class PlayerSnapshot implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID uuid;
    private final String name;
    private final ItemStack[] storageContents;
    private final ItemStack[] armorContents;
    private final ItemStack[] extraContents;
    private final int level;
    private final float exp;
    private final int totalExperience;
    private final GameMode gameMode;
    private final StoredLocation location;

    PlayerSnapshot(Player player) {
        this.uuid = player.getUniqueId();
        this.name = player.getName();
        this.storageContents = player.getInventory().getStorageContents();
        this.armorContents = player.getInventory().getArmorContents();
        this.extraContents = player.getInventory().getExtraContents();
        this.level = player.getLevel();
        this.exp = player.getExp();
        this.totalExperience = player.getTotalExperience();
        this.gameMode = player.getGameMode();
        this.location = new StoredLocation(player.getLocation());
    }

    UUID uuid() {
        return uuid;
    }

    String name() {
        return name;
    }

    ItemStack[] storageContents() {
        return storageContents;
    }

    ItemStack[] armorContents() {
        return armorContents;
    }

    ItemStack[] extraContents() {
        return extraContents;
    }

    int level() {
        return level;
    }

    float exp() {
        return exp;
    }

    int totalExperience() {
        return totalExperience;
    }

    GameMode gameMode() {
        return gameMode;
    }

    StoredLocation location() {
        return location;
    }
}
