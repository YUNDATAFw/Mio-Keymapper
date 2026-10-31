package io.takamiya.mio.floating.toolBoxFragment.tool.beacon;

public class BeaconColorMixerItem {
    private final String sequence;
    private final String displayName;
    private final int[] resultRgb;

    public BeaconColorMixerItem(String sequence, String displayName, int[] resultRgb) {
        this.sequence = sequence;
        this.displayName = displayName;
        this.resultRgb = resultRgb;
    }

    public String getSequence() { return sequence; }
    public String getDisplayName() { return displayName; }
    public int[] getResultRgb() { return resultRgb; }

    public int getColor() {
        return (resultRgb[0] << 16) | (resultRgb[1] << 8) | resultRgb[2];
    }

    public String getHex() {
        return String.format("#%02X%02X%02X", resultRgb[0], resultRgb[1], resultRgb[2]);
    }
}
