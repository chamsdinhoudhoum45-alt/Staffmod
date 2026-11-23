package com.maghrebsmp.staffmode;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Builds and identifies the special staff items. */
public final class StaffTools {

    public enum Tool {
        RANDOM_TELEPORT("random-teleport", Material.ENDER_EYE),
        INSPECT("inspect", Material.BOOK),
        FREEZE("freeze", Material.PACKED_ICE),
        VANISH("vanish", Material.LIME_DYE);

        final String configKey;
        final Material fallback;

        Tool(String configKey, Material fallback) {
            this.configKey = configKey;
            this.fallback = fallback;
        }
    }

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final StaffModePlugin plugin;
    private final NamespacedKey key;

    public StaffTools(StaffModePlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "staff_tool");
    }

    public void give(Player player) {
        for (Tool tool : Tool.values()) {
            int slot = plugin.getConfig().getInt("items." + tool.configKey + ".slot", 0);
            if (slot < 0 || slot > 8) {
                slot = 0;
            }
            player.getInventory().setItem(slot, create(tool));
        }
        player.updateInventory();
    }

    public ItemStack create(Tool tool) {
        String path = "items." + tool.configKey;
        Material mat = Material.matchMaterial(plugin.getConfig().getString(path + ".material", ""));
        ItemStack item = new ItemStack(mat != null && mat.isItem() ? mat : tool.fallback);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(plugin.getConfig().getString(path + ".name", tool.name()))
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList(path + ".lore")) {
            lore.add(MM.deserialize(line).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, tool.name());
        item.setItemMeta(meta);
        return item;
    }

    /** Returns the tool represented by this item, or null if it is not a staff tool. */
    public Tool of(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        try {
            return Tool.valueOf(id);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
