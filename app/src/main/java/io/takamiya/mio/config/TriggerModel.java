package io.takamiya.mio.config;

public enum TriggerModel {
    BUTTON,
    SWITCH,
    PRESSURE_PLATE;

    public static TriggerModel fromString(String value) {
        if (value == null) return BUTTON;
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BUTTON;
        }
    }
}
