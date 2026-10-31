package io.takamiya.mio.utils;

import cn.panda.utils.library.HttpUtils.interceptor.LogCat;
import cn.panda.utils.library.HttpUtils.java.HttpClientJava;

public class HttpManage {
    private static HttpClientJava INSTANCE;

    private HttpManage() {
    }

    public static HttpClientJava getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new HttpClientJava.Builder()
                    .connectTimeout(15L)
                    .readTimeout(15L)
                    .writeTimeout(15L)
                    .addInterceptor(
                            LogCat.Companion.builder().logLevel(LogCat.LogLevel.DEBUG)
                                    .getInterceptor(LogCat.InterceptorType.BASIC))
                    .build();
        }
        return INSTANCE;
    }
}
