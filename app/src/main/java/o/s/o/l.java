package o.s.o;

import android.content.Context;

public final class l {
    static {
        System.loadLibrary("secure-string-lib");
    }

    private static volatile l i;
    private final Context c;

    private l(Context c) {
        this.c = c.getApplicationContext();
    }

    public static l init(Context c) {
        if (c == null) throw new IllegalArgumentException("Context cannot be null!");
        if (i == null) {
            synchronized (l.class) {
                if (i == null) i = new l(c);
            }
        }
        return i;
    }

    public static l get() {
        if (i == null)
            throw new IllegalStateException("Not initialized! Call init(Context) first.");
        return i;
    }

    private native String[] nativeGetDecryptedStrings(String k);

    public String[] a() {
        return nativeGetDecryptedStrings("Interface");
    }

    public String[] b() {
        return nativeGetDecryptedStrings("LibInfo");
    }
}
