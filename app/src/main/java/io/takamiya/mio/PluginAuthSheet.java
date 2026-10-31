package io.takamiya.mio;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import cn.fsy.plugin.sdl.library.SdlEngine;
import cn.fsy.plugin.sdl.library.SdlException;
import cn.fsy.plugin.sdl.library.plugin.Permissions;
import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.databinding.ItemPluginAuthPermissionBinding;
import io.takamiya.mio.databinding.SheetPluginAuthBinding;
import io.takamiya.mio.dsl.DslPluginManager;

/**
 * 插件权限授权 BottomSheet（Material3）。
 * 安装成功后弹出：列表展示插件 config.json 声明的权限（名称/描述 + MaterialSwitch），
 * 底部「保存授权」按钮将选中的权限持久化并记录授权状态。
 *
 * 各权限等级的交互规则：
 * - NORMAL：默认授予、开关锁定（引擎安装时自动授予）
 * - DANGEROUS：用户可开关（初始状态读回引擎已保存的授权）
 * - SIGNATURE：需与插件同签名，锁定不可勾选
 * - SYSTEM：需 Root 权限；运行时无 Root 时锁定（提示需要 Root）
 */
public class PluginAuthSheet {

    /** 授权完成回调 */
    public interface OnGrantListener {
        void onGranted(int grantedCount);
    }

    private static final String TAG = "PluginAuthSheet";

    private final Context context;
    private final BottomSheetDialog dialog;
    private final SheetPluginAuthBinding binding;
    private final String pluginPackage;
    private final String pluginName;

    private final List<JSONObject> permissions = new ArrayList<>();
    private final boolean[] checked;
    private final boolean[] toggleable;
    private final String[] notes;
    private final boolean[] granted;
    private final PermAdapter adapter;

    private OnGrantListener grantListener;

    private static Boolean sRootChecked;

    public PluginAuthSheet(Context context, String pluginPackage, String pluginName) {
        this.context = context;
        this.pluginPackage = pluginPackage;
        this.pluginName = pluginName;

        binding = SheetPluginAuthBinding.inflate(LayoutInflater.from(context));
        dialog = new BottomSheetDialog(context);
        dialog.setContentView(binding.getRoot());

        loadPermissions();
        checked = new boolean[permissions.size()];
        toggleable = new boolean[permissions.size()];
        notes = new String[permissions.size()];
        granted = new boolean[permissions.size()];
        loadGrantedState();
        computeRowStates();
        adapter = new PermAdapter();

        String displayName = (pluginName == null || pluginName.isEmpty()) ? pluginPackage : pluginName;
        int grantable = 0;
        for (boolean b : toggleable) {
            if (b) grantable++;
        }
        binding.tvAuthPluginInfo.setText(permissions.isEmpty()
                ? "插件「" + displayName + "」未声明任何权限"
                : "插件「" + displayName + "」申请 " + permissions.size() + " 项权限，其中 " + grantable
                        + " 项可授权（其余为默认授予或需特殊权限）。");

        binding.rvPermissions.setLayoutManager(new LinearLayoutManager(context));
        binding.rvPermissions.setAdapter(adapter);
        binding.btnGrant.setOnClickListener(v -> onGrantClick());
    }

    public Context getContext() {
        return context;
    }

    public void setOnGrantListener(OnGrantListener listener) {
        this.grantListener = listener;
    }

    public void show() {
        dialog.show();
    }

    public void dismiss() {
        dialog.dismiss();
    }

    public boolean isShowing() {
        return dialog.isShowing();
    }

    private void onGrantClick() {
        List<String> grant = new ArrayList<>();
        List<String> revoke = new ArrayList<>();
        for (int i = 0; i < permissions.size(); i++) {
            if (!toggleable[i]) continue;
            String name = permissions.get(i).optString("name");
            if (checked[i]) {
                grant.add(name);
            } else {
                revoke.add(name);
            }
        }

        if (grant.isEmpty() && revoke.isEmpty()) {
            Toast.makeText(context, "没有可保存的授权变更", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            return;
        }

        try {
            for (String name : grant) {
                SdlEngine.grantPermission(pluginPackage, name);
            }
            for (String name : revoke) {
                SdlEngine.revokePermission(pluginPackage, name);
            }
            AppLogger.i(TAG, "permissions updated for " + pluginPackage
                    + ": grant=" + grant + " revoke=" + revoke);
            String msg = "已保存「" + (pluginName == null || pluginName.isEmpty() ? pluginPackage : pluginName)
                    + "」授权：" + grant.size() + " 项授权";
            if (!revoke.isEmpty()) {
                msg += "，" + revoke.size() + " 项撤销";
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
        } catch (SdlException e) {
            AppLogger.e(TAG, "update permissions failed for " + pluginPackage + ": " + e.getMessage(), e);
            Toast.makeText(context, "保存失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            AppLogger.e(TAG, "update permissions error for " + pluginPackage + ": " + e.getMessage(), e);
            Toast.makeText(context, "保存失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        dialog.dismiss();
        if (grantListener != null) {
            grantListener.onGranted(grant.size());
        }
    }

    private void loadPermissions() {
        JSONArray perms = DslPluginManager.readDeclaredPermissions(context, pluginPackage);
        if (perms == null) {
            AppLogger.w(TAG, "no permission list for " + pluginPackage);
            return;
        }
        for (int i = 0; i < perms.length(); i++) {
            JSONObject p = perms.optJSONObject(i);
            if (p == null) continue;
            String name = p.optString("name");
            if (name.isEmpty()) continue;
            if (!p.has("desc")) {
                Permissions perm = Permissions.fromName(name, null);
                if (perm != null) {
                    try {
                        p.put("desc", perm.getDescription());
                    } catch (org.json.JSONException ignore) {
                    }
                }
            }
            permissions.add(p);
        }
    }

    /** 读回引擎已授予的权限，作为开关初始状态（可授权项才需要）。 */
    private void loadGrantedState() {
        try {
            for (String name : SdlEngine.permissionsOf(pluginPackage)) {
                for (int i = 0; i < permissions.size(); i++) {
                    if (name.equals(permissions.get(i).optString("name"))) {
                        granted[i] = true;
                    }
                }
            }
        } catch (Exception e) {
            AppLogger.w(TAG, "permissionsOf failed for " + pluginPackage + ": " + e.getMessage());
        }
    }

    private void computeRowStates() {
        for (int i = 0; i < permissions.size(); i++) {
            String name = permissions.get(i).optString("name");
            Permissions perm = Permissions.fromName(name, null);
            Permissions.Level level = perm != null ? perm.getLevel() : Permissions.Level.DANGEROUS;

            switch (level) {
                case NORMAL:
                    checked[i] = true;
                    toggleable[i] = false;
                    notes[i] = "默认授予";
                    break;
                case SIGNATURE:
                    checked[i] = false;
                    toggleable[i] = false;
                    notes[i] = "需与插件同签名";
                    break;
                case SYSTEM:
                    if (hasRootAccess()) {
                        checked[i] = granted[i];
                        toggleable[i] = true;
                        notes[i] = "需要 Root 权限";
                    } else {
                        checked[i] = false;
                        toggleable[i] = false;
                        notes[i] = "需 Root/系统权限才能授权";
                    }
                    break;
                default: // DANGEROUS、未知权限
                    checked[i] = granted[i];
                    toggleable[i] = true;
                    notes[i] = "";
                    break;
            }
        }
    }

    /**
     * 检测当前运行环境是否具备 Root 权限（结果缓存）。
     */
    static boolean hasRootAccess() {
        Boolean cached = sRootChecked;
        if (cached != null) return cached;
        boolean root = detectRoot();
        sRootChecked = root;
        AppLogger.i(TAG, "root access detected: " + root);
        return root;
    }

    private static boolean detectRoot() {
        try {
            String[] paths = {
                    "/system/bin/su",
                    "/system/xbin/su",
                    "/sbin/su",
                    "/system/app/Superuser.apk",
                    "/data/adb/magisk"
            };
            for (String p : paths) {
                if (new File(p).exists()) return true;
            }
        } catch (Exception ignore) {
        }
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"which", "su"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            process.destroy();
            return line != null && !line.trim().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 列表适配器 ====================

    private class PermAdapter extends RecyclerView.Adapter<PermAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemPluginAuthPermissionBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            JSONObject p = permissions.get(position);
            holder.binding.textPermName.setText(p.optString("name"));

            String desc = p.optString("desc", "");
            if (notes[position] != null && !notes[position].isEmpty()) {
                holder.binding.textPermDesc.setText(desc + "\n" + notes[position]);
            } else {
                holder.binding.textPermDesc.setText(desc);
            }

            holder.binding.switchPerm.setOnCheckedChangeListener(null);
            holder.binding.switchPerm.setEnabled(toggleable[position]);
            holder.binding.switchPerm.setAlpha(toggleable[position] ? 1.0f : 0.5f);
            holder.binding.switchPerm.setChecked(checked[position]);
            holder.binding.switchPerm.setOnCheckedChangeListener((button, isChecked) -> {
                if (toggleable[position]) {
                    checked[position] = isChecked;
                }
            });
        }

        @Override
        public int getItemCount() {
            return permissions.size();
        }

        private class VH extends RecyclerView.ViewHolder {
            final ItemPluginAuthPermissionBinding binding;

            VH(ItemPluginAuthPermissionBinding b) {
                super(b.getRoot());
                this.binding = b;
            }
        }
    }
}