package com.maghrebsmp.staffmode;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.List;
import java.util.Locale;

public final class StaffListener implements Listener {

    private final StaffModePlugin plugin;

    public StaffListener(StaffModePlugin plugin) {
        this.plugin = plugin;
    }

    private StaffManager m() { return plugin.manager(); }

    // ------------------------------------------------------------ join / quit

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        m().handleJoin(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        boolean silent = m().isVanished(p)
                && plugin.getConfig().getBoolean("staff.silent-join-quit-when-vanished", true);
        m().handleQuit(p);
        if (silent) {
            e.quitMessage(null);
        }
    }

    // -------------------------------------------------------------- tool use

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (e.getAction() == Action.PHYSICAL) {
            if (m().isVanished(p)) e.setCancelled(true);
            return;
        }
        if (!m().isStaff(p) || e.getHand() != EquipmentSlot.HAND) return;

        StaffTools.Tool tool = plugin.tools().of(e.getItem());
        if (tool == null) return;
        e.setCancelled(true);

        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        switch (tool) {
            case RANDOM_TELEPORT -> m().randomTeleport(p);
            case VANISH -> {
                if (p.hasPermission("staffmode.vanish")) {
                    m().toggleVanish(p);
                } else {
                    plugin.msg().send(p, "no-permission");
                }
            }
            default -> { }
        }
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        if (!m().isStaff(p) || e.getHand() != EquipmentSlot.HAND) return;

        StaffTools.Tool tool = plugin.tools().of(p.getInventory().getItemInMainHand());
        if (tool == null) return;
        e.setCancelled(true);

        if (!(e.getRightClicked() instanceof Player target)) return;
        switch (tool) {
            case INSPECT -> {
                if (p.hasPermission("staffmode.invsee")) {
                    m().openInvsee(p, target);
                } else {
                    plugin.msg().send(p, "no-permission");
                }
            }
            case FREEZE -> {
                if (p.hasPermission("staffmode.freeze")) {
                    m().toggleFreeze(p, target);
                } else {
                    plugin.msg().send(p, "no-permission");
                }
            }
            default -> { }
        }
    }

    // ------------------------------------------------- staff mode protections

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && m().isStaff(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && m().isStaff(p)
                && plugin.getConfig().getBoolean("staff.prevent-attacks", true)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (m().isStaff(e.getPlayer())) {
            e.setCancelled(true);
            plugin.msg().send(e.getPlayer(), "tool-locked");
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (m().isStaff(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && (m().isStaff(p) || m().isVanished(p))) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && m().isStaff(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onTarget(EntityTargetEvent e) {
        if (e.getTarget() instanceof Player p && (m().isStaff(p) || m().isVanished(p))) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (m().isStaff(e.getEntity())) {
            e.setKeepInventory(true);
            e.setKeepLevel(true);
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (m().isFrozen(p)
                || (m().isStaff(p) && plugin.getConfig().getBoolean("staff.block-building", true))) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (m().isFrozen(p)
                || (m().isStaff(p) && plugin.getConfig().getBoolean("staff.block-building", true))) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------- inventories

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && shouldLockInventory(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && shouldLockInventory(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p) {
            m().onInventoryClose(p);
        }
    }

    private boolean shouldLockInventory(Player p) {
        if (m().isStaff(p) && plugin.getConfig().getBoolean("staff.lock-inventory", true)) {
            return true;
        }
        return m().isInvseeViewer(p) && !plugin.getConfig().getBoolean("invsee.allow-edit", false);
    }

    // ------------------------------------------------------------------ freeze

    @EventHandler(priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent e) {
        if (!m().hasFrozenPlayers()) return;
        if (m().isFrozen(e.getPlayer()) && e.hasChangedPosition()) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!m().isFrozen(p)) return;
        String msg = e.getMessage();
        String cmd = msg.substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        int colon = cmd.indexOf(':');
        if (colon >= 0) cmd = cmd.substring(colon + 1);
        List<String> allowed = plugin.getConfig().getStringList("freeze.allowed-commands");
        for (String a : allowed) {
            if (a.equalsIgnoreCase(cmd)) return;
        }
        e.setCancelled(true);
        plugin.msg().send(p, "frozen-command-blocked");
    }
}
