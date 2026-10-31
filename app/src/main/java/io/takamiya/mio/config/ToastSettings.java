package io.takamiya.mio.config;

public class ToastSettings {
    private Integer durationMs;
    private Integer fontSize;
    private Integer textColor;
    private Boolean bold;
    private Integer backgroundColor;

    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer durationMs) { this.durationMs = durationMs; }

    public Integer getFontSize() { return fontSize; }
    public void setFontSize(Integer fontSize) { this.fontSize = fontSize; }

    public Integer getTextColor() { return textColor; }
    public void setTextColor(Integer textColor) { this.textColor = textColor; }

    public Boolean getBold() { return bold; }
    public void setBold(Boolean bold) { this.bold = bold; }

    public Integer getBackgroundColor() { return backgroundColor; }
    public void setBackgroundColor(Integer backgroundColor) { this.backgroundColor = backgroundColor; }
}
