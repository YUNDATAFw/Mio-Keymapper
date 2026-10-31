package io.takamiya.mio.configManage;

import java.util.Collections;
import java.util.List;

import io.takamiya.mio.config.Plugin;

public class InstallResult {

    public enum Status {
        SUCCESS,
        FORMAT_ERROR,
        DOWNGRADE,
        VERSION_EQUAL,
        PACKAGE_MISMATCH
    }

    private final Status status;
    private final String packageName;
    private final int installedVersionCode;
    private final int newVersionCode;
    private final String installedVersionName;
    private final String newVersionName;
    private final List<ValidationError> errors;
    private final Plugin plugin;

    private InstallResult(Builder builder) {
        this.status = builder.status;
        this.packageName = builder.packageName;
        this.installedVersionCode = builder.installedVersionCode;
        this.newVersionCode = builder.newVersionCode;
        this.installedVersionName = builder.installedVersionName;
        this.newVersionName = builder.newVersionName;
        this.errors = builder.errors != null
                ? Collections.unmodifiableList(builder.errors)
                : Collections.emptyList();
        this.plugin = builder.plugin;
    }

    public Status getStatus() { return status; }
    public String getPackageName() { return packageName; }
    public int getInstalledVersionCode() { return installedVersionCode; }
    public int getNewVersionCode() { return newVersionCode; }
    public String getInstalledVersionName() { return installedVersionName; }
    public String getNewVersionName() { return newVersionName; }
    public List<ValidationError> getErrors() { return errors; }
    public Plugin getPlugin() { return plugin; }

    public boolean isSuccess() { return status == Status.SUCCESS; }
    public boolean isBlockingError() {
        return status == Status.FORMAT_ERROR || status == Status.DOWNGRADE || status == Status.PACKAGE_MISMATCH;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Status status;
        private String packageName;
        private int installedVersionCode;
        private int newVersionCode;
        private String installedVersionName;
        private String newVersionName;
        private List<ValidationError> errors;
        private Plugin plugin;

        public Builder status(Status status) { this.status = status; return this; }
        public Builder packageName(String packageName) { this.packageName = packageName; return this; }
        public Builder installedVersionCode(int code) { this.installedVersionCode = code; return this; }
        public Builder newVersionCode(int code) { this.newVersionCode = code; return this; }
        public Builder installedVersionName(String name) { this.installedVersionName = name; return this; }
        public Builder newVersionName(String name) { this.newVersionName = name; return this; }
        public Builder errors(List<ValidationError> errors) { this.errors = errors; return this; }
        public Builder plugin(Plugin plugin) { this.plugin = plugin; return this; }
        public InstallResult build() { return new InstallResult(this); }
    }
}
