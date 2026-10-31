package io.takamiya.mio.bean;

import cn.panda.utils.library.HttpUtils.java.DownloadRequestJava;

public class DownloadTask {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_DOWNLOADING = 1;
    public static final int STATUS_PAUSED = 2;
    public static final int STATUS_COMPLETED = 3;
    public static final int STATUS_FAILED = 4;

    private final int id;
    private final String title;
    private final String url;
    private final String filePath;
    private long totalSize;
    private long currentSize;
    private long speed;
    private int percent;
    private int status;
    private final long createdAt;
    private long completedAt;
    private DownloadRequestJava request;

    public DownloadTask(int id, String title, String url, String filePath) {
        this.id = id;
        this.title = title;
        this.url = url;
        this.filePath = filePath;
        this.createdAt = System.currentTimeMillis();
        this.status = STATUS_PENDING;
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getUrl() { return url; }
    public String getFilePath() { return filePath; }

    public long getTotalSize() { return totalSize; }
    public void setTotalSize(long totalSize) { this.totalSize = totalSize; }

    public long getCurrentSize() { return currentSize; }
    public void setCurrentSize(long currentSize) { this.currentSize = currentSize; }

    public long getSpeed() { return speed; }
    public void setSpeed(long speed) { this.speed = speed; }

    public int getPercent() { return percent; }
    public void setPercent(int percent) { this.percent = percent; }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public long getCreatedAt() { return createdAt; }
    public long getCompletedAt() { return completedAt; }
    public void setCompletedAt(long completedAt) { this.completedAt = completedAt; }

    public DownloadRequestJava getRequest() { return request; }
    public void setRequest(DownloadRequestJava request) { this.request = request; }

    public boolean isFinished() {
        return status == STATUS_COMPLETED || status == STATUS_FAILED;
    }
}
