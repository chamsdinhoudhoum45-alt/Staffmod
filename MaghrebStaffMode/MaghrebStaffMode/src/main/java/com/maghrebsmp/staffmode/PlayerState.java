package com.maghrebsmp.staffmode;

import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;

/** Snapshot of a player's state, persisted to disk so inventories survive crashes. */
public final class PlayerState {

    private final ItemStack[] storage;
    private final ItemStack[] armor;
    private final ItemStack[] extra;
    private final GameMode mode;
    private final double health;
    private final int food;
    private final float saturation;
    private final int level;
    private final float exp;
    private final boolean allowFlight;
    private final boolean flying;
    private final boolean wasVanished;

    private PlayerState(ItemStack[] storage, ItemStack[] armor, ItemStack[] extra, GameMode mode,
                        double health, int food, float saturation, int level, float exp,
                        boolean allowFlight, boolean flying, boolean wasVanished) {
        this.storage = storage;
        this.armor = armor;
        this.extra = extra;
        this.mode = mode;
        this.health = health;
        this.food = food;
        this.saturation = saturation;
        this.level = level;
        this.exp = exp;
        this.allowFlight = allowFlight;
        this.flying = flying;
        this.wasVanished = wasVanished;
    }

    public static PlayerState capture(Player p, boolean vanished) {
        PlayerInventory inv = p.getInventory();
        return new PlayerState(
                cloneAll(inv.getStorageContents()),
                cloneAll(inv.getArmorContents()),
                cloneAll(inv.getExtraContents()),
                p.getGameMode(), p.getHealth(), p.getFoodLevel(), p.getSaturation(),
                p.getLevel(), p.getExp(), p.getAllowFlight(), p.isFlying(), vanished);
    }

    public boolean wasVanished() {
        return wasVanished;
    }

    public void apply(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setStorageContents(storage);
        inv.setArmorContents(armor);
        inv.setExtraContents(extra);

        p.setGameMode(mode);
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        double cap = max != null ? max.getValue() : 20.0;
        p.setHealth(Math.max(1.0, Math.min(health, cap)));
        p.setFoodLevel(food);
        p.setSaturation(saturation);
        p.setLevel(level);
        p.setExp(exp);
        p.setAllowFlight(allowFlight);
        p.setFlying(allowFlight && flying);
        p.updateInventory();
    }

    public void save(File file) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("storage", Arrays.asList(storage));
        yaml.set("armor", Arrays.asList(armor));
        yaml.set("extra", Arrays.asList(extra));
        yaml.set("mode", mode.name());
        yaml.set("health", health);
        yaml.set("food", food);
        yaml.set("saturation", (double) saturation);
        yaml.set("level", level);
        yaml.set("exp", (double) exp);
        yaml.set("allow-flight", allowFlight);
        yaml.set("flying", flying);
        yaml.set("was-vanished", wasVanished);

        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create " + parent);
        }
        File tmp = new File(file.getPath() + ".tmp");
        yaml.save(tmp);
        try {
            Files.move(tmp.toPath(), file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static PlayerState load(File file) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        GameMode gm;
        try {
            gm = GameMode.valueOf(y.getString("mode", "SURVIVAL"));
        } catch (IllegalArgumentException ex) {
            gm = GameMode.SURVIVAL;
        }
        return new PlayerState(
                toArray(y.getList("storage"), 36),
                toArray(y.getList("armor"), 4),
                toArray(y.getList("extra"), 1),
                gm, y.getDouble("health", 20.0), y.getInt("food", 20),
                (float) y.getDouble("saturation", 5.0), y.getInt("level", 0),
                (float) y.getDouble("exp", 0.0), y.getBoolean("allow-flight", false),
                y.getBoolean("flying", false), y.getBoolean("was-vanished", false));
    }

    private static ItemStack[] toArray(List<?> list, int length) {
        ItemStack[] out = new ItemStack[length];
        if (list != null) {
            for (int i = 0; i < Math.min(length, list.size()); i++) {
                if (list.get(i) instanceof ItemStack s) {
                    out[i] = s;
                }
            }
        }
        return out;
    }

    private static ItemStack[] cloneAll(ItemStack[] src) {
        ItemStack[] out = new ItemStack[src.length];
        for (int i = 0; i < src.length; i++) {
            out[i] = src[i] == null ? null : src[i].clone();
        }
        return out;
    }
}
