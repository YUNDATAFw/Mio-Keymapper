package io.takamiya.mio.floating.toolBoxFragment.tool.leather;

public class ArmorDyeMixerItem {
    private final String sequence;
    private final String displayName;
    private final int[] dyeRgb;

    public ArmorDyeMixerItem(String sequence, String displayName, int[] dyeRgb) {
        this.sequence = sequence;
        this.displayName = displayName;
        this.dyeRgb = dyeRgb;
    }

    public String getSequence() { return sequence; }
    public String getDisplayName() { return displayName; }
    public int[] getDyeRgb() { return dyeRgb; }

    public int getColor() {
        return (dyeRgb[0] << 16) | (dyeRgb[1] << 8) | dyeRgb[2];
    }

    public String getHex() {
        return String.format("#%02X%02X%02X", dyeRgb[0], dyeRgb[1], dyeRgb[2]);
    }
}
