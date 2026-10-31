package io.takamiya.mio.navigation;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.Registry.Registry;
import cn.panda.utils.library.Registry.exceptions.RegistryException;
import io.takamiya.mio.configManage.ConfigPackageManager;
import io.takamiya.mio.configManage.IPluginManager;
import io.takamiya.mio.utils.DataManage;
import io.takamiya.mio.utils.RegistryUtils;

public class HomeViewModel extends AndroidViewModel {

    private static final String TAG = "HomeViewModel";
    private static final String KEY_INPUT_MODEL = DataManage.Tag.APP_CONFIG_INPUT_MODEL.getValue();

    private int inputModel;
    private int pluginCount;
    private int configCount;

    public HomeViewModel(@NonNull Application application) {
        super(application);
    }

    public int getInputModel() {
        return inputModel;
    }

    public void setInputModel(int inputModel) {
        this.inputModel = inputModel;
    }

    public int getPluginCount() {
        return pluginCount;
    }

    public int getConfigCount() {
        return configCount;
    }

    /** 主页卡片计数：已安装插件数 + 已安装配置数。 */
    public void loadCounts() {
        try {
            IPluginManager manager = new IPluginManager(getApplication());
            pluginCount = manager.list().size();
        } catch (Exception e) {
            AppLogger.e(TAG, "Failed to load plugin count: " + e.getMessage(), e);
            pluginCount = 0;
        }
        try {
            ConfigPackageManager manager = new ConfigPackageManager(getApplication());
            configCount = manager.list().size();
        } catch (Exception e) {
            AppLogger.e(TAG, "Failed to load config count: " + e.getMessage(), e);
            configCount = 0;
        }
    }

    public void loadInputModel() {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        if (reg.exists(KEY_INPUT_MODEL)) {
            try {
                inputModel = reg.getInt(KEY_INPUT_MODEL);
            } catch (RegistryException e) {
                AppLogger.e(TAG, "failed to parse " + KEY_INPUT_MODEL + ": " + e.getMessage());
                inputModel = 0;
            }
        }
    }

    public void saveInputModel() {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.set(KEY_INPUT_MODEL, inputModel);
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "failed to save input model: " + e.getMessage(), e);
        }
    }
}
