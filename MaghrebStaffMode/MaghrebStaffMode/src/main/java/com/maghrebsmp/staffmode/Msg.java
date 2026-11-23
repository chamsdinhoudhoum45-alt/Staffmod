package com.maghrebsmp.staffmode;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;

/** Sends configurable MiniMessage messages from config.yml. */
public final class Msg {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final StaffModePlugin plugin;

    public Msg(StaffModePlugin plugin) {
        this.plugin = plugin;
    }

    public static TagResolver player(String name) {
        return Placeholder.unparsed("player", name);
    }

    public void send(CommandSender to, String key, TagResolver... resolvers) {
        String raw = plugin.getConfig().getString("messages." + key,
                "<red>Missing message: " + key);
        if (raw.isBlank()) {
            return;
        }
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        to.sendMessage(MM.deserialize(prefix + raw, resolvers));
    }
}
