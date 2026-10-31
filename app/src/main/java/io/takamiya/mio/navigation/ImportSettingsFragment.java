package io.takamiya.mio.navigation;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.Registry.Registry;
import io.takamiya.mio.databinding.FragmentImportSettingsBinding;
import io.takamiya.mio.utils.DataManage;
import io.takamiya.mio.utils.RegistryUtils;

/**
 * 导入设置：各菜单点击坐标（X/Y）录入，输入即实时写入 Registry。
 * 字段与 {@link DataManage.Tag} 一一对应（顺序必须一致）。
 */
public class ImportSettingsFragment extends Fragment {

    private static final String TAG = "ImportSettings";
    /** 输入防抖：停止输入 400ms 后落盘。 */
    private static final long SAVE_DELAY_MS = 400;

    private static final DataManage.Tag[] KEYS = {
            DataManage.Tag.IMPORT_CLICK_BLOCK_TYPE_X,
            DataManage.Tag.IMPORT_CLICK_BLOCK_TYPE_Y,
            DataManage.Tag.IMPORT_CLICK_REPEAT_X,
            DataManage.Tag.IMPORT_CLICK_REPEAT_Y,
            DataManage.Tag.IMPORT_CLICK_CHAIN_X,
            DataManage.Tag.IMPORT_CLICK_CHAIN_Y,
            DataManage.Tag.IMPORT_CLICK_ACTIVATION_X,
            DataManage.Tag.IMPORT_CLICK_ACTIVATION_Y,
            DataManage.Tag.IMPORT_CLICK_KEEP_ON_X,
            DataManage.Tag.IMPORT_CLICK_KEEP_ON_Y,
            DataManage.Tag.IMPORT_CLICK_COMMAND_EDIT_X,
            DataManage.Tag.IMPORT_CLICK_COMMAND_EDIT_Y,
            DataManage.Tag.IMPORT_CLICK_CLOSE_PANEL_X,
            DataManage.Tag.IMPORT_CLICK_CLOSE_PANEL_Y,
            DataManage.Tag.IMPORT_DELAY_STEP,
            DataManage.Tag.IMPORT_DELAY_PANEL_OPEN,
            DataManage.Tag.IMPORT_DELAY_LIST_OPEN,
            DataManage.Tag.IMPORT_DELAY_EDIT_FOCUS,
            DataManage.Tag.IMPORT_PRESS_DURATION,
    };

    /** 与 KEYS 对齐：坐标默认空（不回填），时序项回填默认值。 */
    private static final String[] DEFAULTS = {
            "", "", "", "", "", "", "", "", "", "", "", "", "", "",
            String.valueOf(DataManage.DEFAULT_DELAY_STEP_MS),
            String.valueOf(DataManage.DEFAULT_DELAY_PANEL_OPEN_MS),
            String.valueOf(DataManage.DEFAULT_DELAY_LIST_OPEN_MS),
            String.valueOf(DataManage.DEFAULT_DELAY_EDIT_FOCUS_MS),
            String.valueOf(DataManage.DEFAULT_PRESS_DURATION_MS),
    };

    private FragmentImportSettingsBinding binding;
    private EditText[] fields;
    private final Handler saveHandler = new Handler(Looper.getMainLooper());
    private final Runnable saveTask = this::persist;

    private final TextWatcher watcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            saveHandler.removeCallbacks(saveTask);
            saveHandler.postDelayed(saveTask, SAVE_DELAY_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentImportSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        fields = new EditText[]{
                binding.etBlockTypeX, binding.etBlockTypeY,
                binding.etRepeatX, binding.etRepeatY,
                binding.etChainX, binding.etChainY,
                binding.etActivationX, binding.etActivationY,
                binding.etKeepOnX, binding.etKeepOnY,
                binding.etCommandEditX, binding.etCommandEditY,
                binding.etClosePanelX, binding.etClosePanelY,
                binding.etDelayStep, binding.etDelayPanelOpen,
                binding.etDelayListOpen, binding.etDelayEditFocus,
                binding.etPressDuration,
        };
        if (fields.length != KEYS.length) {
            AppLogger.w(TAG, "field/key mismatch: " + fields.length + "/" + KEYS.length);
        }

        load();
        // 回填完成后再挂监听，避免启动即触发无谓写入
        for (EditText field : fields) {
            field.addTextChangedListener(watcher);
        }
    }

    private void load() {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        for (int i = 0; i < fields.length && i < KEYS.length && i < DEFAULTS.length; i++) {
            String key = KEYS[i].getValue();
            try {
                if (reg.exists(key)) {
                    fields[i].setText(reg.getString(key));
                } else if (!DEFAULTS[i].isEmpty()) {
                    fields[i].setText(DEFAULTS[i]);
                }
            } catch (Exception e) {
                AppLogger.w(TAG, "load failed for " + key + ": " + e.getMessage());
            }
        }
    }

    /** 全量写入当前界面值并异步落盘（主线程）。 */
    private void persist() {
        if (binding == null || fields == null) return;
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            for (int i = 0; i < fields.length && i < KEYS.length; i++) {
                Editable text = fields[i].getText();
                reg.set(KEYS[i].getValue(), text != null ? text.toString() : "");
            }
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "persist failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void onDestroyView() {
        saveHandler.removeCallbacks(saveTask);
        persist(); // 落掉最后一次未达防抖窗口的输入
        super.onDestroyView();
        binding = null;
        fields = null;
    }
}
