package com.odissea.tdm;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.ChatColor;

enum Team {
    RED("Red", TextColor.color(0xFF5FB7), ChatColor.RED),
    BLUE("Blue", TextColor.color(0x61E786), ChatColor.BLUE);

    private final String displayName;
    private final TextColor color;
    private final ChatColor chatColor;

    Team(String displayName, TextColor color, ChatColor chatColor) {
        this.displayName = displayName;
        this.color = color;
        this.chatColor = chatColor;
    }

    String displayName() {
        return displayName;
    }

    TextColor color() {
        return color;
    }

    ChatColor chatColor() {
        return chatColor;
    }
}
