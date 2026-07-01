package com.odissea.tdm;

import java.io.IOException;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class TDMEventPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final String ADMIN_PERMISSION = "odissea.tdm.admin";
    private static final TextColor ODISSEA_PINK = TextColor.color(0xFF5FB7);
    private static final TextColor ODISSEA_GREEN = TextColor.color(0x61E786);

    private final Map<Team, Set<UUID>> aliveByTeam = new EnumMap<>(Team.class);
    private final Map<UUID, Team> teamsByPlayer = new HashMap<>();
    private final Set<UUID> participants = new HashSet<>();

    private SnapshotStore snapshotStore;
    private File kitFile;
    private boolean active;
    private boolean stateLock;
    private boolean winCheckQueued;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        kitFile = new File(getDataFolder(), "kit.yml");
        snapshotStore = new SnapshotStore(getDataFolder().toPath().resolve("pending-restores"));
        try {
            snapshotStore.init();
        } catch (IOException exception) {
            getLogger().severe("Could not initialize snapshot storage. Disabling TDMEvent.");
            getLogger().log(java.util.logging.Level.SEVERE, exception.getMessage(), exception);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("tdm") != null) {
            getCommand("tdm").setExecutor(this);
            getCommand("tdm").setTabCompleter(this);
        }

        restorePendingOnlinePlayers("startup");
    }

    @Override
    public void onDisable() {
        if (active) {
            endEvent(null, true);
        } else if (snapshotStore != null) {
            restorePendingOnlinePlayers("shutdown");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(message("You do not have permission to use TDM commands."));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(message("Usage: /tdm <start|stop|setspawn|kitset>"));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start" -> startEvent(sender);
            case "stop" -> forceStop(sender);
            case "setspawn" -> setSpawn(sender, args);
            case "kitset" -> setKit(sender);
            default -> sender.sendMessage(message("Usage: /tdm <start|stop|setspawn|kitset>"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("start", "stop", "setspawn", "kitset");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setspawn")) {
            return List.of("red", "blue");
        }
        return List.of();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!active) {
            return;
        }
        UUID uuid = event.getEntity().getUniqueId();
        Team team = teamsByPlayer.get(uuid);
        if (team != null) {
            eliminate(uuid, team, "death");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!active) {
            return;
        }
        UUID uuid = event.getPlayer().getUniqueId();
        Team team = teamsByPlayer.get(uuid);
        if (team != null) {
            eliminate(uuid, team, "disconnect");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        try {
            if (snapshotStore.restoreIfPending(event.getPlayer())) {
                getLogger().info("Restored pending TDM snapshot for " + event.getPlayer().getName() + " on join.");
            }
        } catch (SnapshotRestoreException exception) {
            getLogger().log(java.util.logging.Level.SEVERE, exception.getMessage(), exception);
        }
    }

    private void startEvent(CommandSender sender) {
        if (stateLock) {
            sender.sendMessage(message("TDM is already changing state. Try again in a moment."));
            return;
        }
        if (active) {
            sender.sendMessage(message("A TDM event is already running."));
            return;
        }

        stateLock = true;
        List<UUID> savedSnapshotUuids = new ArrayList<>();
        boolean started = false;
        try {
            Location redSpawn = getSpawn(Team.RED);
            Location blueSpawn = getSpawn(Team.BLUE);
            if (redSpawn == null || blueSpawn == null) {
                sender.sendMessage(message("Set both spawns first with /tdm setspawn red and /tdm setspawn blue."));
                return;
            }

            TDMKit kit = loadKit();
            if (kit == null) {
                sender.sendMessage(message("Set a kit first with /tdm kitset."));
                return;
            }

            List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
            if (players.size() < 2) {
                sender.sendMessage(message("At least 2 online players are required to start TDM."));
                return;
            }

            for (Player player : players) {
                if (snapshotStore.hasPending(player.getUniqueId())) {
                    sender.sendMessage(message(player.getName() + " still has a pending TDM restore. Restore them before starting."));
                    return;
                }
            }

            Collections.shuffle(players);
            int redCount = players.size() / 2;
            if (redCount == 0 || redCount == players.size()) {
                sender.sendMessage(message("Could not create two non-empty teams."));
                return;
            }

            for (Player player : players) {
                PlayerSnapshot snapshot = new PlayerSnapshot(player);
                snapshotStore.save(snapshot);
                savedSnapshotUuids.add(player.getUniqueId());
            }

            participants.clear();
            teamsByPlayer.clear();
            aliveByTeam.clear();
            aliveByTeam.put(Team.RED, new HashSet<>());
            aliveByTeam.put(Team.BLUE, new HashSet<>());

            for (int i = 0; i < players.size(); i++) {
                Player player = players.get(i);
                Team team = i < redCount ? Team.RED : Team.BLUE;
                participants.add(player.getUniqueId());
                teamsByPlayer.put(player.getUniqueId(), team);
                aliveByTeam.get(team).add(player.getUniqueId());
                preparePlayer(player, team == Team.RED ? redSpawn : blueSpawn, team, kit);
            }

            active = true;
            started = true;
            Bukkit.broadcast(message("TDM has started: Red (" + aliveByTeam.get(Team.RED).size()
                    + ") vs Blue (" + aliveByTeam.get(Team.BLUE).size() + ")."));
        } catch (Exception exception) {
            for (UUID uuid : savedSnapshotUuids) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    try {
                        snapshotStore.restoreIfPending(player);
                    } catch (SnapshotRestoreException restoreException) {
                        getLogger().log(java.util.logging.Level.SEVERE, restoreException.getMessage(), restoreException);
                    }
                }
            }
            sender.sendMessage(message("TDM failed to start safely. Any saved participants were restored."));
            getLogger().log(java.util.logging.Level.SEVERE, "Failed to start TDM", exception);
        } finally {
            if (!started) {
                participants.clear();
                teamsByPlayer.clear();
                aliveByTeam.clear();
                winCheckQueued = false;
            }
            stateLock = false;
        }
    }

    private void forceStop(CommandSender sender) {
        if (stateLock) {
            sender.sendMessage(message("TDM is already changing state. Try again in a moment."));
            return;
        }
        if (!active) {
            sender.sendMessage(message("No TDM event is running."));
            return;
        }
        endEvent(null, false);
        sender.sendMessage(message("TDM has been force-stopped and participants are being restored."));
    }

    private void setSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(message("Only players can set TDM spawns."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(message("Usage: /tdm setspawn <red|blue>"));
            return;
        }

        Team team = parseTeam(args[1]);
        if (team == null) {
            sender.sendMessage(message("Spawn must be red or blue."));
            return;
        }

        String key = team == Team.RED ? "spawns.red" : "spawns.blue";
        getConfig().set(key, player.getLocation());
        saveConfig();
        sender.sendMessage(Component.text(team.displayName() + " spawn set.", team.color()));
    }

    private void setKit(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(message("Only players can set the TDM kit."));
            return;
        }

        try {
            saveKit(TDMKit.fromPlayer(player), player.getName());
            sender.sendMessage(message("TDM kit saved from your current inventory, armor, and offhand."));
        } catch (IOException exception) {
            sender.sendMessage(message("Could not save kit.yml. Check the console for details."));
            getLogger().log(java.util.logging.Level.SEVERE, "Could not save TDM kit", exception);
        }
    }

    private void preparePlayer(Player player, Location spawn, Team team, TDMKit kit) {
        kit.apply(player);
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(spawn);
        player.sendMessage(Component.text("You are on the " + team.displayName() + " team.", team.color()));
    }

    private void saveKit(TDMKit kit, String savedBy) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("saved-by", savedBy);
        yaml.set("saved-at", System.currentTimeMillis());
        yaml.set("storage", Arrays.asList(kit.storageContents()));
        yaml.set("armor", Arrays.asList(kit.armorContents()));
        yaml.set("extra", Arrays.asList(kit.extraContents()));

        if (!getDataFolder().isDirectory() && !getDataFolder().mkdirs()) {
            throw new IOException("Could not create plugin data folder: " + getDataFolder());
        }
        yaml.save(kitFile);
    }

    private TDMKit loadKit() {
        if (kitFile == null || !kitFile.isFile()) {
            return null;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(kitFile);
        ItemStack[] storage = contentsFromList(yaml.getList("storage"), 36);
        ItemStack[] armor = contentsFromList(yaml.getList("armor"), 4);
        ItemStack[] extra = contentsFromList(yaml.getList("extra"), 1);
        return new TDMKit(storage, armor, extra);
    }

    private ItemStack[] contentsFromList(List<?> rawContents, int expectedSize) {
        ItemStack[] contents = new ItemStack[expectedSize];
        if (rawContents == null) {
            return contents;
        }

        for (int i = 0; i < Math.min(rawContents.size(), expectedSize); i++) {
            Object value = rawContents.get(i);
            if (value instanceof ItemStack item) {
                contents[i] = item.clone();
            }
        }
        return contents;
    }

    private void eliminate(UUID uuid, Team team, String reason) {
        Set<UUID> alive = aliveByTeam.get(team);
        if (alive == null || !alive.remove(uuid)) {
            return;
        }
        teamsByPlayer.remove(uuid);
        Bukkit.broadcast(Component.text(team.displayName() + " lost a player to " + reason + ". "
                + alive.size() + " remain.", team.color()));
        queueWinCheck();
    }

    private void queueWinCheck() {
        if (winCheckQueued) {
            return;
        }
        winCheckQueued = true;
        Bukkit.getScheduler().runTask(this, this::checkWinCondition);
    }

    private void checkWinCondition() {
        winCheckQueued = false;
        if (!active || stateLock) {
            return;
        }

        int redAlive = aliveByTeam.getOrDefault(Team.RED, Set.of()).size();
        int blueAlive = aliveByTeam.getOrDefault(Team.BLUE, Set.of()).size();

        if (redAlive == 0 && blueAlive == 0) {
            endEvent(null, false);
        } else if (redAlive == 0) {
            endEvent(Team.BLUE, false);
        } else if (blueAlive == 0) {
            endEvent(Team.RED, false);
        }
    }

    private void endEvent(Team winner, boolean shuttingDown) {
        if (stateLock && !shuttingDown) {
            return;
        }

        stateLock = true;
        try {
            if (!active && !shuttingDown) {
                return;
            }

            active = false;
            if (!shuttingDown) {
                if (winner == null) {
                    Bukkit.broadcast(message("TDM ended in a draw."));
                } else {
                    Bukkit.broadcast(Component.text(winner.displayName() + " team wins TDM!", winner.color()));
                }
            }

            for (UUID uuid : new HashSet<>(participants)) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    try {
                        snapshotStore.restoreIfPending(player);
                    } catch (SnapshotRestoreException exception) {
                        getLogger().log(java.util.logging.Level.SEVERE, exception.getMessage(), exception);
                    }
                }
            }
        } finally {
            participants.clear();
            teamsByPlayer.clear();
            aliveByTeam.clear();
            winCheckQueued = false;
            stateLock = false;
        }
    }

    private void restorePendingOnlinePlayers(String reason) {
        try {
            List<UUID> pending = snapshotStore.pendingUuids();
            if (!pending.isEmpty()) {
                getLogger().warning("Found " + pending.size() + " pending TDM restore(s) on " + reason + ".");
            }
        } catch (IOException exception) {
            getLogger().log(java.util.logging.Level.SEVERE, "Could not list pending TDM restores", exception);
        }

        try {
            int restored = snapshotStore.restoreAllOnline(Bukkit.getOnlinePlayers());
            if (restored > 0) {
                getLogger().info("Restored " + restored + " pending TDM snapshot(s) on " + reason + ".");
            }
        } catch (SnapshotRestoreException exception) {
            getLogger().log(java.util.logging.Level.SEVERE, exception.getMessage(), exception);
        }
    }

    private Location getSpawn(Team team) {
        String key = team == Team.RED ? "spawns.red" : "spawns.blue";
        Object value = getConfig().get(key);
        if (!(value instanceof Location location) || location.getWorld() == null) {
            return null;
        }
        return location;
    }

    private Team parseTeam(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "red" -> Team.RED;
            case "blue" -> Team.BLUE;
            default -> null;
        };
    }

    static Component message(String text) {
        return Component.text("[ODISSEA TDM] ", ODISSEA_PINK).append(Component.text(text, ODISSEA_GREEN));
    }
}
