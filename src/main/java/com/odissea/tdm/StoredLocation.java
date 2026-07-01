package com.odissea.tdm;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

final class StoredLocation implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID worldId;
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    StoredLocation(Location location) {
        World world = location.getWorld();
        this.worldId = world.getUID();
        this.worldName = world.getName();
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.yaw = location.getYaw();
        this.pitch = location.getPitch();
    }

    Location toLocation() {
        World world = Bukkit.getWorld(worldId);
        if (world == null) {
            world = Bukkit.getWorld(worldName);
        }
        if (world == null) {
            world = Bukkit.getWorlds().getFirst();
            return world.getSpawnLocation();
        }
        return new Location(world, x, y, z, yaw, pitch);
    }
}
