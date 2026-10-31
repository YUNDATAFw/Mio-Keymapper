package io.takamiya.mio.config;

public class PluginConfig {
    private String name;
    private String packageName;
    private String description;
    private String author;
    private String updateUrl;
    private String versionName;
    private Integer versionCode;
    private Integer minEngine;
    private boolean enable;
    private Long keyHoldDuration;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getUpdateUrl() { return updateUrl; }
    public void setUpdateUrl(String updateUrl) { this.updateUrl = updateUrl; }

    public String getVersionName() { return versionName; }
    public void setVersionName(String versionName) { this.versionName = versionName; }

    public Integer getVersionCode() { return versionCode; }
    public void setVersionCode(Integer versionCode) { this.versionCode = versionCode; }

    public Integer getMinEngine() { return minEngine; }
    public void setMinEngine(Integer minEngine) { this.minEngine = minEngine; }

    public boolean isEnable() { return enable; }
    public void setEnable(boolean enable) { this.enable = enable; }

    public Long getKeyHoldDuration() { return keyHoldDuration; }
    public void setKeyHoldDuration(Long keyHoldDuration) { this.keyHoldDuration = keyHoldDuration; }
}
