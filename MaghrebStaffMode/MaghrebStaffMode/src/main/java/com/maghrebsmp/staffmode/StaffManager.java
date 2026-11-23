package com.maghrebsmp.staffmode;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/** Core logic: staff mode, vanish, freeze and invsee state. */
public final class StaffManager {

    private final StaffModePlugin plugin;
    private final File dataDir;

    private final Map<UUID, PlayerState> states = new HashMap<>();
    private final Set<UUID> vanished = new HashSet<>();
    private final Set<UUID> frozen = new HashSet<>();
    /** viewer -> inspected player */
    private final Map<UUID, UUID> invsee = new HashMap<>();

    private BukkitTask reminderTask;

    public StaffManager(StaffModePlugin plugin) {
        this.plugin = plugin;
        this.dataDir = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists() && !dataDir.mkdirs()) {
            plugin.getLogger().warning("Could not create data folder: " + dataDir);
        }
    }

    // ---------------------------------------------------------------- queries

    public boolean isStaff(Player p) { return states.containsKey(p.getUniqueId()); }
    public boolean isVanished(Player p) { return vanished.contains(p.getUniqueId()); }
    public boolean isFrozen(Player p) { return frozen.contains(p.getUniqueId()); }
    public boolean hasFrozenPlayers() { return !frozen.isEmpty(); }
    public boolean isInvseeViewer(Player p) { return invsee.containsKey(p.getUniqueId()); }

    private File fileOf(UUID id) { return new File(dataDir, id + ".yml"); }

    // ------------------------------------------------------------- staff mode

    public void toggleStaff(Player p) {
        if (isStaff(p)) {
            exit(p, true);
        } else {
            enter(p);
        }
    }

    public void enter(Player p) {
        if (isStaff(p)) {
            return;
        }
        PlayerState state = PlayerState.capture(p, isVanished(p));
        try {
            state.save(fileOf(p.getUniqueId())); // inventory safety: persist BEFORE touching anything
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save state of " + p.getName(), ex);
            plugin.msg().send(p, "staff-save-failed");
            return;
        }
        states.put(p.getUniqueId(), state);

        p.getInventory().clear();
        p.setGameMode(parseGameMode());
        p.setAllowFlight(plugin.getConfig().getBoolean("staff.allow-flight", true));
        plugin.tools().give(p);

        if (plugin.getConfig().getBoolean("staff.auto-vanish", true) && !isVanished(p)) {
            setVanish(p, true);
        }
        plugin.msg().send(p, "staff-enabled");
    }

    public void exit(Player p, boolean notify) {
        PlayerState state = states.remove(p.getUniqueId());
        if (state == null) {
            return;
        }
        closeInvsee(p);
        state.apply(p);
        setVanish(p, state.wasVanished());
        File f = fileOf(p.getUniqueId());
        if (f.exists() && !f.delete()) {
            plugin.getLogger().warning("Could not delete " + f.getName());
        }
        if (notify) {
            plugin.msg().send(p, "staff-disabled");
        }
    }

    private GameMode parseGameMode() {
        try {
            return GameMode.valueOf(plugin.getConfig().getString("staff.gamemode", "SURVIVAL").toUpperCase());
        } catch (IllegalArgumentException ex) {
            return GameMode.SURVIVAL;
        }
    }

    // ----------------------------------------------------------------- vanish

    public void toggleVanish(Player p) {
        setVanish(p, !isVanished(p));
        plugin.msg().send(p, isVanished(p) ? "vanish-enabled" : "vanish-disabled");
    }

    public void setVanish(Player p, boolean on) {
        if (on) {
            vanished.add(p.getUniqueId());
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(p) && !other.hasPermission("staffmode.vanish")) {
                    other.hidePlayer(plugin, p);
                }
            }
            p.setCollidable(false);
            p.setSleepingIgnored(true);
        } else {
            vanished.remove(p.getUniqueId());
            for (Player other : Bukkit.getOnlinePlayers()) {
                other.showPlayer(plugin, p);
            }
            p.setCollidable(true);
            p.setSleepingIgnored(false);
        }
    }

    // ----------------------------------------------------------------- freeze

    public void toggleFreeze(Player staff, Player target) {
        if (staff.equals(target)) {
            plugin.msg().send(staff, "cannot-target-self");
            return;
        }
        if (frozen.remove(target.getUniqueId())) {
            plugin.msg().send(staff, "unfrozen-staff", Msg.player(target.getName()));
            plugin.msg().send(target, "unfrozen-target");
        } else {
            frozen.add(target.getUniqueId());
            plugin.msg().send(staff, "frozen-staff", Msg.player(target.getName()));
            plugin.msg().send(target, "frozen-target");
        }
    }

    public void startFreezeReminder() {
        if (reminderTask != null) {
            reminderTask.cancel();
            reminderTask = null;
        }
        int seconds = plugin.getConfig().getInt("freeze.reminder-seconds", 5);
        if (seconds <= 0) {
            return;
        }
        long period = seconds * 20L;
        reminderTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (frozen.isEmpty()) {
                return;
            }
            for (UUID id : frozen) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    plugin.msg().send(p, "frozen-reminder");
                }
            }
        }, period, period);
    }

    // ----------------------------------------------------------------- invsee

    public void openInvsee(Player viewer, Player target) {
        if (viewer.equals(target)) {
            plugin.msg().send(viewer, "cannot-target-self");
            return;
        }
        invsee.put(viewer.getUniqueId(), target.getUniqueId());
        viewer.openInventory(target.getInventory());
        plugin.msg().send(viewer, "invsee-opened", Msg.player(target.getName()));
    }

    public void onInventoryClose(Player viewer) {
        invsee.remove(viewer.getUniqueId());
    }

    private void closeInvsee(Player viewer) {
        if (invsee.remove(viewer.getUniqueId()) != null) {
            viewer.closeInventory();
        }
    }

    // ------------------------------------------------------- random teleport

    public void randomTeleport(Player staff) {
        List<Player> candidates = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.equals(staff) && !isStaff(p)) {
                candidates.add(p);
            }
        }
        if (candidates.isEmpty()) {
            plugin.msg().send(staff, "no-players-to-teleport");
            return;
        }
        Player target = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        staff.teleport(target.getLocation());
        plugin.msg().send(staff, "random-teleport", Msg.player(target.getName()));
    }

    // ------------------------------------------------------- join / quit hooks

    public void handleJoin(Player joiner) {
        // Hide vanished staff from players who cannot see them
        if (!joiner.hasPermission("staffmode.vanish")) {
            for (UUID id : vanished) {
                Player v = Bukkit.getPlayer(id);
                if (v != null && !v.equals(joiner)) {
                    joiner.hidePlayer(plugin, v);
                }
            }
        }
        // Crash recovery: a saved state exists but the player is not in Staff Mode
        File f = fileOf(joiner.getUniqueId());
        if (f.exists() && !isStaff(joiner)) {
            try {
                PlayerState.load(f).apply(joiner);
                if (!f.delete()) {
                    plugin.getLogger().warning("Could not delete " + f.getName());
                }
                plugin.msg().send(joiner, "inventory-recovered");
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE,
                        "Failed to recover inventory of " + joiner.getName() + " (file kept: " + f.getName() + ")", ex);
            }
        }
    }

    public void handleQuit(Player p) {
        UUID id = p.getUniqueId();
        exit(p, false); // restore inventory BEFORE the server saves player data
        vanished.remove(id);
        invsee.remove(id);
        // Close inspections of the player who left
        for (Map.Entry<UUID, UUID> e : new ArrayList<>(invsee.entrySet())) {
            if (e.getValue().equals(id)) {
                Player viewer = Bukkit.getPlayer(e.getKey());
                invsee.remove(e.getKey());
                if (viewer != null) {
                    viewer.closeInventory();
                }
            }
        }
        if (frozen.contains(id)) {
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("staffmode.freeze")) {
                    plugin.msg().send(staff, "frozen-quit", Msg.player(p.getName()));
                }
            }
        }
    }

    public void shutdown() {
        if (reminderTask != null) {
            reminderTask.cancel();
        }
        for (UUID id : new ArrayList<>(states.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                exit(p, false);
            }
        }
    }
}
