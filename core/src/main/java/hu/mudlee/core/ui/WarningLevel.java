package hu.mudlee.core.ui;

import hu.mudlee.core.Color;

/** Severity levels for debug stat warnings, with display prefix and colours. */
public enum WarningLevel {
    NONE("", Color.WHITE, new Color(0f, 0f, 0f, 0.6f)),
    NOTICE("[!] ", Color.YELLOW, new Color(0.2f, 0.17f, 0f, 1f)),
    WARN("[!!] ", Color.ORANGE, new Color(0.3f, 0.15f, 0f, 1f)),
    CRITICAL("[!!!] ", Color.RED, Color.BLACK);

    /** Prefix prepended to the stat value string. */
    public final String prefix;
    /** Text colour for the prefixed value. */
    public final Color color;
    /** Shadow colour used around the text (overrides UIBatch default for CRITICAL). */
    public final Color shadowColor;

    WarningLevel(String prefix, Color color, Color shadowColor) {
        this.prefix = prefix;
        this.color = color;
        this.shadowColor = shadowColor;
    }
}
