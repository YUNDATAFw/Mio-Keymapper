package io.takamiya.mio.bean;

import java.io.File;

public class CrashLogEntry {
    public final File file;
    public final String crashTime;
    public final String exceptionMsg;
    public final String fullContent;

    public CrashLogEntry(File file, String crashTime, String exceptionMsg, String fullContent) {
        this.file = file;
        this.crashTime = crashTime;
        this.exceptionMsg = exceptionMsg;
        this.fullContent = fullContent;
    }
}
