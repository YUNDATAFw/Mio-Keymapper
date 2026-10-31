package io.takamiya.mio.config;

public enum ActionType {
    INPUT_KEY,
    INPUT_KEY_GROUP,
    INPUT_TEXT,
    TOAST;

    public static ActionType fromString(String value) {
        if (value == null) return INPUT_KEY;
        String upper = value.toUpperCase().replace("组", "_GROUP");
        try {
            return valueOf(upper);
        } catch (IllegalArgumentException e) {
            return INPUT_KEY;
        }
    }
}
