package me.gym;

public enum StatType {
    STRENGTH("strength"),
    DEFENSE("defense");

    private final String key;

    StatType(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static StatType fromString(String s) {
        if (s == null) return null;
        for (StatType t : values()) {
            if (t.key.equalsIgnoreCase(s)) return t;
        }
        return null;
    }
}
