package io.takamiya.mio.floating;

public class SolsticeUI {

    static {
        System.loadLibrary("solstice-ui");
    }

    private SolsticeUI() {
    }

    public static native void Init();
    public static native void Resize(int width, int height);
    public static native void Render();
    public static native void Shutdown();
    public static native void SetItems(String[] titles, String[] keys, boolean[] enabled);
    public static native void SetActiveKey(String key);
    public static native void SetActiveState(String key, boolean active);
    public static native void Notify(String text, int type, float duration);
    public static native void SetNotifyColor(int argb);
    public static native void SetGlow(boolean enabled);
    public static native void SetGlowDensity(int density);
    public static native void SetGlowRadius(float radius);
    public static native void SetRightOffset(float offset);
    public static native void SetDisplay(int mode);
    public static native void SetTextShadow(boolean enabled);
    public static native void SetShadowOffset(float offset);
    public static native void SetWatermarkText(String text);
    public static native void SetFontSize(float size);
    public static native void SetFeatureListFontSize(float size);
    public static native void SetNotifyFontSize(float size);
    public static native void SetNotifyPosition(int position);
    public static native void SetNotifyGradient(boolean enabled);
}
