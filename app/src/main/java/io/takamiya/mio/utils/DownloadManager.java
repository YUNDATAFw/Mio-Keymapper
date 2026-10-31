package io.takamiya.mio.utils;

import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import cn.panda.utils.library.HttpUtils.response.ProgressListener;
import io.takamiya.mio.app.Mio;
import io.takamiya.mio.bean.DownloadTask;

public class DownloadManager {

    private static volatile DownloadManager instance;

    private final CopyOnWriteArrayList<DownloadTask> tasks = new CopyOnWriteArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();
    private int nextId = 1;

    public interface Listener {
        void onTaskAdded(DownloadTask task);
        void onTaskUpdated(DownloadTask task);
        void onTaskRemoved(DownloadTask task);
    }

    private DownloadManager() {
    }

    public static DownloadManager getInstance() {
        if (instance == null) {
            synchronized (DownloadManager.class) {
                if (instance == null) {
                    instance = new DownloadManager();
                }
            }
        }
        return instance;
    }

    public void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public DownloadTask addTask(String url, String fileName) {
        int id = nextId++;
        File dir = new File(
                Mio.getAppContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "resources");
        if (!dir.exists()) dir.mkdirs();
        String filePath = new File(dir, fileName).getAbsolutePath();

        DownloadTask task = new DownloadTask(id, fileName, url, filePath);
        tasks.add(task);
        notifyAdded(task);
        startDownload(task);
        return task;
    }

    public List<DownloadTask> getActiveTasks() {
        List<DownloadTask> result = new ArrayList<>();
        for (DownloadTask t : tasks) {
            if (!t.isFinished()) result.add(t);
        }
        return result;
    }

    public List<DownloadTask> getCompletedTasks() {
        List<DownloadTask> result = new ArrayList<>();
        for (DownloadTask t : tasks) {
            if (t.getStatus() == DownloadTask.STATUS_COMPLETED) result.add(t);
        }
        return result;
    }

    public void pause(DownloadTask task) {
        if (task.getRequest() != null) {
            task.getRequest().cancel();
        }
        task.setStatus(DownloadTask.STATUS_PAUSED);
        task.setSpeed(0);
        notifyUpdated(task);
    }

    public void resume(DownloadTask task) {
        if (task.getStatus() == DownloadTask.STATUS_PAUSED || task.getStatus() == DownloadTask.STATUS_FAILED) {
            task.setStatus(DownloadTask.STATUS_PENDING);
            task.setSpeed(0);
            notifyUpdated(task);
            startDownload(task);
        }
    }

    public void remove(DownloadTask task) {
        if (task.getRequest() != null) {
            task.getRequest().cancel();
        }
        File file = new File(task.getFilePath());
        if (file.exists()) file.delete();
        tasks.remove(task);
        notifyRemoved(task);
    }

    public void clearDone() {
        List<DownloadTask> toRemove = new ArrayList<>();
        for (DownloadTask t : tasks) {
            if (t.getStatus() == DownloadTask.STATUS_COMPLETED) {
                toRemove.add(t);
            }
        }
        for (DownloadTask t : toRemove) {
            File file = new File(t.getFilePath());
            if (file.exists()) file.delete();
            tasks.remove(t);
            notifyRemoved(t);
        }
    }

    public void removeBatch(List<DownloadTask> batch) {
        for (DownloadTask t : batch) {
            if (t.getRequest() != null) t.getRequest().cancel();
            File file = new File(t.getFilePath());
            if (file.exists()) file.delete();
            tasks.remove(t);
            notifyRemoved(t);
        }
    }

    private void startDownload(DownloadTask task) {
        task.setStatus(DownloadTask.STATUS_DOWNLOADING);
        task.setSpeed(0);
        notifyUpdated(task);

        cn.panda.utils.library.HttpUtils.java.DownloadRequestJava req =
                HttpManage.getInstance().download(task.getUrl())
                        .to(task.getFilePath())
                        .resume(true)
                        .overwrite(false)
                        .progress(new ProgressListener() {
                            private long lastNotifyMs;

                            @Override
                            public void onProgress(long current, long total, boolean done) {
                                if (done) {
                                    task.setStatus(DownloadTask.STATUS_COMPLETED);
                                    task.setCompletedAt(System.currentTimeMillis());
                                    task.setPercent(100);
                                    task.setCurrentSize(total);
                                    task.setTotalSize(total);
                                    task.setSpeed(0);
                                    notifyUpdated(task);
                                } else {
                                    long now = System.currentTimeMillis();
                                    if (now - lastNotifyMs < 250) {
                                        task.setCurrentSize(current);
                                        task.setTotalSize(total);
                                        task.setPercent(total > 0 ? (int) (current * 100 / total) : 0);
                                        return;
                                    }
                                    lastNotifyMs = now;
                                    task.setCurrentSize(current);
                                    task.setTotalSize(total);
                                    task.setPercent(total > 0 ? (int) (current * 100 / total) : 0);
                                    notifyUpdated(task);
                                }
                            }

                            @Override
                            public void onSpeed(long bytesPerSecond) {
                                task.setSpeed(bytesPerSecond);
                                notifyUpdated(task);
                            }

                            @Override
                            public void onRemainingTime(long remainingSeconds) {
                            }
                        })
                        .onSuccess((code, body) -> {
                        })
                        .onFailure((code, err) -> {
                            if (code == 0 && "Canceled".equals(err)) return;
                            task.setStatus(DownloadTask.STATUS_FAILED);
                            task.setSpeed(0);
                            notifyUpdated(task);
                        });
        task.setRequest(req);
        req.execute();
    }

    private void notifyAdded(DownloadTask task) {
        for (Listener l : new ArrayList<>(listeners)) l.onTaskAdded(task);
    }

    private void notifyUpdated(DownloadTask task) {
        for (Listener l : new ArrayList<>(listeners)) l.onTaskUpdated(task);
    }

    private void notifyRemoved(DownloadTask task) {
        for (Listener l : new ArrayList<>(listeners)) l.onTaskRemoved(task);
    }
}
