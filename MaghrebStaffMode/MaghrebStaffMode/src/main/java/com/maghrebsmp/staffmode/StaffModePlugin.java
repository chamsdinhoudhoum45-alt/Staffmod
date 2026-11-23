package com.maghrebsmp.staffmode;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class StaffModePlugin extends JavaPlugin {

    private Msg msg;
    private StaffTools tools;
    private StaffManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.msg = new Msg(this);
        this.tools = new StaffTools(this);
        this.manager = new StaffManager(this);

        getServer().getPluginManager().registerEvents(new StaffListener(this), this);

        Commands executor = new Commands(this);
        for (String name : new String[]{"staff", "vanish", "freeze", "invsee"}) {
            PluginCommand cmd = getCommand(name);
            if (cmd == null) {
                getLogger().severe("Command missing from plugin.yml: " + name);
                continue;
            }
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        manager.startFreezeReminder();
        // Recover anyone already online (e.g. after /reload)
        getServer().getOnlinePlayers().forEach(manager::handleJoin);
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.shutdown();
        }
    }

    public void reloadAll() {
        reloadConfig();
        manager.startFreezeReminder();
    }

    public Msg msg() { return msg; }
    public StaffTools tools() { return tools; }
    public StaffManager manager() { return manager; }
}
