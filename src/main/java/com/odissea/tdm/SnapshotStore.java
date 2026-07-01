package com.odissea.tdm;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.bukkit.entity.Player;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

final class SnapshotStore {
    private static final String EXTENSION = ".tdm-snapshot";

    private final Path directory;

    SnapshotStore(Path directory) {
        this.directory = directory;
    }

    void init() throws IOException {
        Files.createDirectories(directory);
    }

    boolean hasPending(UUID uuid) {
        return Files.exists(path(uuid));
    }

    void save(PlayerSnapshot snapshot) throws IOException {
        Files.createDirectories(directory);
        Path target = path(snapshot.uuid());
        Path temp = directory.resolve(snapshot.uuid() + EXTENSION + ".tmp");

        try (OutputStream fileOut = Files.newOutputStream(temp);
             GZIPOutputStream gzipOut = new GZIPOutputStream(fileOut);
             BukkitObjectOutputStream objectOut = new BukkitObjectOutputStream(gzipOut)) {
            objectOut.writeObject(snapshot);
        }

        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicMoveFailure) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    int restoreAllOnline(Iterable<? extends Player> players) {
        int restored = 0;
        for (Player player : players) {
            if (restoreIfPending(player)) {
                restored++;
            }
        }
        return restored;
    }

    boolean restoreIfPending(Player player) {
        Path file = path(player.getUniqueId());
        if (!Files.exists(file)) {
            return false;
        }

        try {
            PlayerSnapshot snapshot = load(file);
            apply(player, snapshot);
            Files.delete(file);
            return true;
        } catch (Exception exception) {
            player.sendMessage(TDMEventPlugin.message("TDM could not restore your saved inventory yet. Staff have been notified."));
            throw new SnapshotRestoreException("Failed to restore " + player.getName(), exception);
        }
    }

    List<UUID> pendingUuids() throws IOException {
        List<UUID> uuids = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return uuids;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*" + EXTENSION)) {
            for (Path file : stream) {
                String fileName = file.getFileName().toString();
                String uuidText = fileName.substring(0, fileName.length() - EXTENSION.length());
                try {
                    uuids.add(UUID.fromString(uuidText));
                } catch (IllegalArgumentException ignored) {
                    // Leave unknown files alone.
                }
            }
        }
        return uuids;
    }

    private PlayerSnapshot load(Path file) throws IOException, ClassNotFoundException {
        try (InputStream fileIn = Files.newInputStream(file);
             GZIPInputStream gzipIn = new GZIPInputStream(fileIn);
             BukkitObjectInputStream objectIn = new BukkitObjectInputStream(gzipIn)) {
            return (PlayerSnapshot) objectIn.readObject();
        }
    }

    private void apply(Player player, PlayerSnapshot snapshot) {
        player.getInventory().clear();
        player.getInventory().setStorageContents(snapshot.storageContents());
        player.getInventory().setArmorContents(snapshot.armorContents());
        player.getInventory().setExtraContents(snapshot.extraContents());

        player.setTotalExperience(0);
        player.setLevel(0);
        player.setExp(0.0F);
        player.setTotalExperience(snapshot.totalExperience());
        player.setLevel(snapshot.level());
        player.setExp(snapshot.exp());

        player.setGameMode(snapshot.gameMode());
        player.teleport(snapshot.location().toLocation());
        player.updateInventory();
        player.sendMessage(TDMEventPlugin.message("Your pre-TDM inventory and state have been restored."));
    }

    private Path path(UUID uuid) {
        return directory.resolve(uuid + EXTENSION);
    }
}
