package io.takamiya.mio.utils;

import java.util.Calendar;

import o.s.o.l;

public final class SecureStrings {
    private static volatile String[] interfaceStrings;
    private static volatile String[] libInfoStrings;

    private SecureStrings() {
    }

    private static String[] a() {
        if (interfaceStrings == null) {
            synchronized (SecureStrings.class) {
                if (interfaceStrings == null) interfaceStrings = l.get().a();
            }
        }
        return interfaceStrings;
    }

    private static String[] b() {
        if (libInfoStrings == null) {
            synchronized (SecureStrings.class) {
                if (libInfoStrings == null) libInfoStrings = l.get().b();
            }
        }
        return libInfoStrings;
    }

    public static String appId() {
        return a()[0];
    }

    public static String secret() {
        return a()[1];
    }

    public static String baseUrl() {
        return a()[99];
    }

    public static String path(int index) {
        return a()[index];
    }

    public static String shizukuDownloadUrl() {
        return a()[17];
    }

    public static String dataManageValue(int index) {
        return a()[18 + index];
    }

    public static String libName() {
        return b()[0];
    }

    public static String libVersion() {
        return b()[1];
    }

    public static String[] allStrings() {
        return a();
    }
}
