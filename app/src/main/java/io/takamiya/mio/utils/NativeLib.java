package io.takamiya.mio.utils;

public class NativeLib {

    static {
        System.loadLibrary("mio-native");
    }

    private NativeLib() {
    }

    public static native String stringFromJNI();
}
