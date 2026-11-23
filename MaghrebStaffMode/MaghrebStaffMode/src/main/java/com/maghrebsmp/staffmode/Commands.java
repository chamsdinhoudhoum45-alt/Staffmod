package com.maghrebsmp.staffmode;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Commands implements TabExecutor {

    private final StaffModePlugin plugin;

    public Commands(StaffModePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "staff" -> handleStaff(sender, args);
            case "vanish" -> handleVanish(sender);
            case "freeze" -> handleFreeze(sender, args);
            case "invsee" -> handleInvsee(sender, args);
            default -> { return false; }
        }
        return true;
    }

    private void handleStaff(CommandSender sender, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("staffmode.admin")) {
                plugin.msg().send(sender, "no-permission");
                return;
            }
            plugin.reloadAll();
            plugin.msg().send(sender, "config-reloaded");
            return;
        }
        Player p = playerOnly(sender);
        if (p == null) return;
        if (!p.hasPermission("staffmode.use")) {
            plugin.msg().send(p, "no-permission");
            return;
        }
        plugin.manager().toggleStaff(p);
    }

    private void handleVanish(CommandSender sender) {
        Player p = playerOnly(sender);
        if (p == null) return;
        if (!p.hasPermission("staffmode.vanish")) {
            plugin.msg().send(p, "no-permission");
            return;
        }
        plugin.manager().toggleVanish(p);
    }

    private void handleFreeze(CommandSender sender, String[] args) {
        Player p = playerOnly(sender);
        if (p == null) return;
        if (!p.hasPermission("staffmode.freeze")) {
            plugin.msg().send(p, "no-permission");
            return;
        }
        if (args.length != 1) {
            plugin.msg().send(p, "usage-freeze");
            return;
        }
        Player target = find(p, args[0]);
        if (target == null) {
            plugin.msg().send(p, "player-not-found");
            return;
        }
        plugin.manager().toggleFreeze(p, target);
    }

    private void handleInvsee(CommandSender sender, String[] args) {
        Player p = playerOnly(sender);
        if (p == null) return;
        if (!p.hasPermission("staffmode.invsee")) {
            plugin.msg().send(p, "no-permission");
            return;
        }
        if (args.length != 1) {
            plugin.msg().send(p, "usage-invsee");
            return;
        }
        Player target = find(p, args[0]);
        if (target == null) {
            plugin.msg().send(p, "player-not-found");
            return;
        }
        plugin.manager().openInvsee(p, target);
    }

    private Player playerOnly(CommandSender sender) {
        if (sender instanceof Player p) {
            return p;
        }
        plugin.msg().send(sender, "players-only");
        return null;
    }

    /** Finds an online player the sender is allowed to see. */
    private Player find(Player sender, String name) {
        Player target = Bukkit.getPlayerExact(name);
        if (target != null && !sender.canSee(target)) {
            return null;
        }
        return target;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (args.length != 1) {
            return out;
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        if (name.equals("staff")) {
            if (sender.hasPermission("staffmode.admin") && "reload".startsWith(prefix)) {
                out.add("reload");
            }
        } else if ((name.equals("freeze") && sender.hasPermission("staffmode.freeze"))
                || (name.equals("invsee") && sender.hasPermission("staffmode.invsee"))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (sender instanceof Player s && !s.canSee(p)) continue;
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(p.getName());
                }
            }
        }
        return out;
    }
}
