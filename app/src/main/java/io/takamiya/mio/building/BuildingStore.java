package io.takamiya.mio.building;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 建筑存储全局入口：共享同一个 {@link BuildingRepository} 与 IO/主线程调度。
 * 列表页与导入侧边栏共用，保证数据一致。
 */
public final class BuildingStore {

    private static volatile BuildingStore instance;

    private final BuildingRepository repository;
    private final ExecutorService io;
    private final Handler main;

    private BuildingStore(Context context) {
        this.repository = new BuildingRepository(context.getApplicationContext());
        this.io = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "building-io");
            t.setDaemon(true);
            return t;
        });
        this.main = new Handler(Looper.getMainLooper());
    }

    public static BuildingStore get(Context context) {
        if (instance == null) {
            synchronized (BuildingStore.class) {
                if (instance == null) {
                    instance = new BuildingStore(context);
                }
            }
        }
        return instance;
    }

    public BuildingRepository repository() {
        return repository;
    }

    /** 后台执行（列表读取、删除等轻 IO）。 */
    public void io(Runnable r) {
        io.execute(r);
    }

    /** 回主线程执行（UI 更新）。已在主线程则立即执行。 */
    public void main(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            main.post(r);
        }
    }
}
