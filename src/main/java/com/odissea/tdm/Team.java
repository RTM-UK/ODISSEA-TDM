package com.odissea.tdm;

import net.kyori.adventure.text.format.TextColor;

enum Team {
    RED("Red", TextColor.color(0xFF5FB7)),
    BLUE("Blue", TextColor.color(0x61E786));

    private final String displayName;
    private final TextColor color;

    Team(String displayName, TextColor color) {
        this.displayName = displayName;
        this.color = color;
    }

    String displayName() {
        return displayName;
    }

    TextColor color() {
        return color;
    }
}
