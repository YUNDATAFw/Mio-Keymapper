package io.takamiya.mio.bean;

public class Config {

    public interface OnClickListener {
        void onClick(Config config);
    }

    public interface OnEnableChangeListener {
        void onEnableChanged(Config config, boolean isChecked);
    }

    private String name;
    private String description;
    private String version;
    private String packageName;
    private boolean enabled;
    private String warning;
    private boolean locked;
    private boolean incompatible;

    private OnClickListener onEditClickListener;
    private OnClickListener onDeleteClickListener;
    private OnClickListener onCheckUpdateClickListener;
    private OnClickListener onExportClickListener;
    private OnEnableChangeListener onEnableChangeListener;

    public Config() {
    }

    public Config(String name, String description, String version, String packageName, boolean enabled, String warning) {
        this.name = name;
        this.description = description;
        this.version = version;
        this.packageName = packageName;
        this.enabled = enabled;
        this.warning = warning;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public boolean isIncompatible() {
        return incompatible;
    }

    public void setIncompatible(boolean incompatible) {
        this.incompatible = incompatible;
    }

    public String getWarning() {
        return warning;
    }

    public void setWarning(String warning) {
        this.warning = warning;
    }

    public OnClickListener getOnEditClickListener() {
        return onEditClickListener;
    }

    public void setOnEditClickListener(OnClickListener onEditClickListener) {
        this.onEditClickListener = onEditClickListener;
    }

    public OnClickListener getOnDeleteClickListener() {
        return onDeleteClickListener;
    }

    public void setOnDeleteClickListener(OnClickListener onDeleteClickListener) {
        this.onDeleteClickListener = onDeleteClickListener;
    }

    public OnClickListener getOnCheckUpdateClickListener() {
        return onCheckUpdateClickListener;
    }

    public void setOnCheckUpdateClickListener(OnClickListener onCheckUpdateClickListener) {
        this.onCheckUpdateClickListener = onCheckUpdateClickListener;
    }

    public OnClickListener getOnExportClickListener() {
        return onExportClickListener;
    }

    public void setOnExportClickListener(OnClickListener onExportClickListener) {
        this.onExportClickListener = onExportClickListener;
    }

    public OnEnableChangeListener getOnEnableChangeListener() {
        return onEnableChangeListener;
    }

    public void setOnEnableChangeListener(OnEnableChangeListener onEnableChangeListener) {
        this.onEnableChangeListener = onEnableChangeListener;
    }
}
