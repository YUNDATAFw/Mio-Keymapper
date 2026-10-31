package io.takamiya.mio.navigation;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.ref.WeakReference;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.DeviceInfoUtils;
import cn.panda.utils.library.Registry.Registry;
import cn.panda.utils.library.UrlBuilder;
import io.takamiya.mio.PluginShopActivity;
import io.takamiya.mio.GyroTestActivity;
import io.takamiya.mio.ImeTestActivity;
import io.takamiya.mio.PermissionActivity;
import io.takamiya.mio.PermissionGuideActivity;
import io.takamiya.mio.R;
import io.takamiya.mio.ShizukuManageActivity;
import io.takamiya.mio.app.Mio;
import io.takamiya.mio.databinding.DialogAnnouncementBinding;
import io.takamiya.mio.databinding.FragmentHomeBinding;
import io.takamiya.mio.databinding.ItemAnnouncementBinding;
import io.takamiya.mio.floating.FloatingWindowService;
import io.takamiya.mio.injector.ImeAutoSwitchHelper;
import io.takamiya.mio.utils.DataManage;
import io.takamiya.mio.utils.HttpManage;
import io.takamiya.mio.utils.InputModelManager;
import io.takamiya.mio.utils.RegistryUtils;
import o.s.o.l;

public class HomeFragment extends Fragment {

    private static final String KEY_INPUT_MODEL = DataManage.Tag.APP_CONFIG_INPUT_MODEL.getValue();
    private static final String[] INPUT_MODEL_NAMES = {"输入法模式", "Shizuku模式"};
    private static final int REQ_POST_NOTIFICATIONS = 100;

    private static WeakReference<HomeFragment> resumedInstance;
    private static boolean announcementLoaded;
    private final String TAG = "HomeFragment";
    private FragmentHomeBinding binding;
    private HomeViewModel viewModel;

    public static void notifyInputModelChanged() {
        HomeFragment instance = resumedInstance != null ? resumedInstance.get() : null;
        if (instance != null) {
            instance.viewModel.loadInputModel();
            instance.refreshModeText();
        }
    }

    public static void notifyServiceStateChanged() {
        HomeFragment instance = resumedInstance != null ? resumedInstance.get() : null;
        if (instance != null) {
            instance.updateServiceFabState();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        resolveAndApplyModel();
        NavController navController = Navigation.findNavController(requireActivity(), R.id.navHostFragment);

        binding.cardServiceStatus.setOnClickListener(v -> showInputModelDialog());
        binding.btnRunMode.setOnClickListener(v -> showInputModelDialog());

        // Shizuku权限按钮点击事件
        binding.btnShizukuPermission.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), ShizukuManageActivity.class));
        });

        // 设置键盘按钮点击事件
        binding.btnKeyboard.setOnClickListener(v -> ImeAutoSwitchHelper.showPicker(requireContext()));

        // 键盘设置按钮点击事件 → 系统键盘服务设置
        binding.btnKeyboardSettings.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));

        // 权限管理按钮点击事件
        binding.btnPermission.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), PermissionActivity.class));
        });

        // 为什么需要这些权限按钮点击事件
        binding.btnWhyPermission.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), PermissionGuideActivity.class));
        });

        // 插件管理 → 跳转到插件页面（抽屉"插件"目的地）
        binding.btnConfigManage.setOnClickListener(v -> {
            com.google.android.material.navigation.NavigationView navView =
                    requireActivity().findViewById(R.id.navigationView);
            MenuItem menuItem = navView.getMenu().findItem(R.id.plugin);
            NavigationUI.onNavDestinationSelected(menuItem, navController);
        });

        // 输入法测试
        binding.btnImeTest.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), ImeTestActivity.class));
        });

        // 陀螺仪测试
        binding.btnGyroTest.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), GyroTestActivity.class));
        });

        // 启动/关闭悬浮窗服务 FAB
        binding.fabService.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(requireContext())) {
                // 权限不完整的处理
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("服务启动失败")
                        .setMessage("原因: 启动服务的必备权限未被授予,请在\"权限管理\"中授予所需权限")
                        .setPositiveButton("权限管理", (dialog, button) -> {
                            startActivity(new Intent(requireContext(), PermissionActivity.class));
                        })
                        .setNeutralButton("关闭", null)
                        .show();
                AppLogger.d(TAG, "TODO: overlay permission not granted, cannot start floating window");
                return;
            }
            if (FloatingWindowService.isRunning()) {
                FloatingWindowService.stop(requireContext());
            } else {
                requestNotificationPermissionIfNeeded();
                FloatingWindowService.start(requireContext());
            }
            updateServiceFabState();
        });
        updateServiceFabState();
        loadAnnouncements();
    }

    @Override
    public void onResume() {
        super.onResume();
        resumedInstance = new WeakReference<>(this);
        updateServiceFabState();
        resolveAndApplyModel();
        viewModel.loadCounts();
        binding.tvPluginCount.setText("已安装插件: " + viewModel.getPluginCount());
        binding.tvConfigCount.setText("已安装配置: " + viewModel.getConfigCount());
    }

    @Override
    public void onPause() {
        super.onPause();
        if (resumedInstance != null && resumedInstance.get() == this) {
            resumedInstance = null;
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_POST_NOTIFICATIONS);
        }
    }

    private void updateServiceFabState() {
        boolean running = FloatingWindowService.isRunning();
        binding.fabService.setImageResource(running
                ? R.drawable.material_symbols_near_me_outline_rounded
                : R.drawable.material_symbols_near_me_disabled_outline_rounded);
        binding.serviceStatusCard.setCardBackgroundColor(running
                ? ContextCompat.getColor(requireContext(), R.color.colorPrimaryContainer)
                : ContextCompat.getColor(requireContext(), R.color.colorErrorContainer));
        binding.serviceStatusImage.setImageTintList(running
                ? ContextCompat.getColorStateList(requireContext(), R.color.colorPrimary)
                : ContextCompat.getColorStateList(requireContext(), R.color.colorError));
        binding.serviceStatusImage.setImageResource(running
                ? R.drawable.material_symbols_check_rounded
                : R.drawable.material_symbols_close_rounded);
    }

    private void refreshModeText() {
        int model = viewModel.getInputModel();
        if (model == InputModelManager.MODEL_NONE) {
            binding.tvServiceMode.setText("模式: 不可用");
            return;
        }
        if (model < 0 || model >= INPUT_MODEL_NAMES.length) {
            model = 0;
        }
        binding.tvServiceMode.setText("模式: " + INPUT_MODEL_NAMES[model]);
    }

    private void resolveAndApplyModel() {
        viewModel.loadInputModel();
        int resolved = InputModelManager.resolveModel(viewModel.getInputModel(), requireContext());
        if (resolved != viewModel.getInputModel()) {
            viewModel.setInputModel(resolved);
            viewModel.saveInputModel();
        }
        refreshModeText();
    }

    private void loadAnnouncements() {
        if (announcementLoaded || !isAdded()) return;
        announcementLoaded = true;
        HttpManage.getInstance().get(UrlBuilder.getInstance()
                        .longPath(l.get().a()[107])
                        .param("app_id", Mio.APP_ID)
                        .param("version_code", String.valueOf(DeviceInfoUtils.getAppVersionCode(requireContext())))
                        .build())
                .onSuccess((code, body) -> {
                    if (code != 200) return;
                    try {
                        JSONObject info = new JSONObject(body.trim());
                        if (info.getInt("code") == 200) {
                            JSONArray list = info.optJSONObject("data") != null
                                    ? info.optJSONObject("data").optJSONArray("list") : null;
                            if (list != null && list.length() > 0 && isAdded()) {
                                postOnUiThread(() -> showAnnouncementSheet(list));
                            }
                        }
                    } catch (Exception e) {
                        AppLogger.e(TAG, "announcements parse error: " + e.getMessage());
                    }
                })
                .onFailure((netCode, err) -> AppLogger.e(TAG, "announcements request failed: " + netCode + "," + err))
                .execute();
    }

    private void showAnnouncementSheet(JSONArray list) {
        DialogAnnouncementBinding b = DialogAnnouncementBinding.inflate(getLayoutInflater());
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        dialog.setContentView(b.getRoot());
        b.btnCloseAnnouncement.setOnClickListener(v -> dialog.dismiss());

        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null) continue;
            ItemAnnouncementBinding c = ItemAnnouncementBinding.inflate(getLayoutInflater());
            c.tvAnnounceTitle.setText(item.optString("title", ""));
            c.tvAnnounceContent.setText(item.optString("content", ""));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int) (20 * requireContext().getResources().getDisplayMetrics().density);
            b.layoutAnnouncementList.addView(c.getRoot(), lp);
        }

        dialog.show();
    }

    private void postOnUiThread(Runnable action) {
        if (isAdded()) requireActivity().runOnUiThread(action);
    }

    private void showInputModelDialog() {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        final int[] selected = {viewModel.getInputModel()};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("映射模式")
                .setSingleChoiceItems(INPUT_MODEL_NAMES, viewModel.getInputModel(), (dialog, which) -> selected[0] = which)
                .setPositiveButton("确定", (dialog, which) -> {
                    viewModel.setInputModel(selected[0]);
                    viewModel.saveInputModel();
                    refreshModeText();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
