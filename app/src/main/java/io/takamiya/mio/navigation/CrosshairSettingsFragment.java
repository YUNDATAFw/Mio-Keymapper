package io.takamiya.mio.navigation;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.Registry.Registry;
import io.takamiya.mio.R;
import io.takamiya.mio.databinding.FragmentSettingCrosshairBinding;
import io.takamiya.mio.floating.FloatingWindowService;
import io.takamiya.mio.utils.DataManage;
import io.takamiya.mio.utils.RegistryUtils;

public class CrosshairSettingsFragment extends Fragment {

    private static final String TAG = "CrosshairSettings";

    private FragmentSettingCrosshairBinding binding;

    private final List<String> iconLibrary = new ArrayList<>();
    private final Set<String> selectedIcons = new LinkedHashSet<>();
    private boolean manageMode;
    /** true=导入后同时应用为当前准星图标 */
    private boolean pendingApply;

    private final ActivityResultLauncher<String[]> iconPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onIconPicked);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingCrosshairBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initSlot();
        initLibrary();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ---------- 图标槽位（首项"无"=默认图标） ----------

    private void initSlot() {
        View.OnClickListener open = v -> showIconPicker();
        binding.cardCrosshairIcon.setOnClickListener(open);
        binding.settingBtnCrosshairIcon.setOnClickListener(open);
        updateSlotSummary();
    }

    private void showIconPicker() {
        List<String> items = new ArrayList<>();
        items.add("无");
        for (String uri : iconLibrary) {
            items.add(iconDisplayName(uri));
        }
        final int importIndex = items.size();
        items.add("导入图片…");

        int checked = 0;
        String current = savedSlotUri();
        if (!current.isEmpty()) {
            int i = iconLibrary.indexOf(current);
            checked = i >= 0 ? i + 1 : 0;
        }
        final int checkedFinal = checked;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("准星图标")
                .setSingleChoiceItems(items.toArray(new String[0]), checkedFinal, (dialog, which) -> {
                    if (which == importIndex) {
                        pendingApply = true;
                        iconPickerLauncher.launch(new String[]{"image/*"});
                    } else if (which == 0) {
                        persistSlotUri("");
                    } else {
                        persistSlotUri(iconLibrary.get(which - 1));
                    }
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateSlotSummary() {
        if (binding == null) return;
        String uri = savedSlotUri();
        if (uri.isEmpty()) {
            binding.tvCrosshairIconSummary.setText("默认图标");
            binding.ivCrosshairSlotIcon.setImageResource(R.drawable.material_symbols_add_2_rounded);
            binding.ivCrosshairSlotIcon.setColorFilter(
                    ContextCompat.getColor(requireContext(), R.color.colorSecondary));
        } else {
            String name = iconDisplayName(Uri.parse(uri));
            binding.tvCrosshairIconSummary.setText(name.isEmpty() ? "自定义图标" : name);
            bindCustomIcon(binding.ivCrosshairSlotIcon, uri);
        }
    }

    private String savedSlotUri() {
        Registry reg = RegistryUtils.get();
        if (reg == null) return "";
        try {
            if (reg.exists(DataManage.Tag.APP_CONFIG_FLOAT_CROSSHAIR_ICON_URI.getValue())) {
                String s = reg.getString(DataManage.Tag.APP_CONFIG_FLOAT_CROSSHAIR_ICON_URI.getValue());
                if (s != null) return s;
            }
        } catch (Exception e) {
            AppLogger.w(TAG, "failed to load crosshair icon uri: " + e.getMessage());
        }
        return "";
    }

    private void persistSlotUri(String uri) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.set(DataManage.Tag.APP_CONFIG_FLOAT_CROSSHAIR_ICON_URI.getValue(), uri);
            reg.saveAsync();
            updateSlotSummary();
            FloatingWindowService.applyCrosshairIcon();
        } catch (Exception e) {
            AppLogger.e(TAG, "save crosshair icon uri failed: " + e.getMessage(), e);
        }
    }

    // ---------- 图标库 ----------

    private void initLibrary() {
        binding.btnImportCrosshair.setOnClickListener(v -> {
            pendingApply = false;
            iconPickerLauncher.launch(new String[]{"image/*"});
        });
        binding.btnManageCrosshair.setOnClickListener(v -> setManageMode(!manageMode));
        binding.btnCancelCrosshairManage.setOnClickListener(v -> setManageMode(false));
        binding.btnDeleteCrosshairs.setOnClickListener(v -> deleteSelectedIcons());

        loadLibrary();
        migrateSlotUriIntoLibrary();
        renderLibrary();
    }

    private void loadLibrary() {
        iconLibrary.clear();
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            if (reg.exists(DataManage.Tag.APP_CONFIG_CROSSHAIR_LIBRARY.getValue())) {
                String raw = reg.getString(DataManage.Tag.APP_CONFIG_CROSSHAIR_LIBRARY.getValue());
                if (raw != null && !raw.isEmpty()) {
                    for (String line : raw.split("\n")) {
                        String uri = line.trim();
                        if (!uri.isEmpty() && !iconLibrary.contains(uri)) {
                            iconLibrary.add(uri);
                        }
                    }
                }
            }
        } catch (Exception e) {
            AppLogger.w(TAG, "failed to load crosshair library: " + e.getMessage());
        }
    }

    private void saveLibrary() {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.set(DataManage.Tag.APP_CONFIG_CROSSHAIR_LIBRARY.getValue(),
                    String.join("\n", iconLibrary));
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "save crosshair library failed: " + e.getMessage(), e);
        }
    }

    /** 历史槽位直接存过 URI 但未入库时，补进图标库保证可选择/可管理。 */
    private void migrateSlotUriIntoLibrary() {
        String current = savedSlotUri();
        if (!current.isEmpty() && !iconLibrary.contains(current)) {
            iconLibrary.add(current);
            saveLibrary();
        }
    }

    private void renderLibrary() {
        if (binding == null) return;
        binding.layoutCrosshairLibrary.removeAllViews();
        binding.layoutCrosshairLibrary.addView(buildRow(null));
        for (String uri : iconLibrary) {
            binding.layoutCrosshairLibrary.addView(buildRow(uri));
        }
    }

    /** uri 为 null 时渲染固定的"无"行（不可选、不可删，代表默认图标）。 */
    private View buildRow(@Nullable final String uri) {
        View row = getLayoutInflater().inflate(R.layout.item_crosshair_library,
                binding.layoutCrosshairLibrary, false);
        CheckBox cb = row.findViewById(R.id.cbCrosshairSelect);
        TextView name = row.findViewById(R.id.tvCrosshairItemName);
        TextView summary = row.findViewById(R.id.tvCrosshairItemSummary);
        ImageView icon = row.findViewById(R.id.ivCrosshairIcon);
        View card = row.findViewById(R.id.cardCrosshairItem);

        if (uri == null) {
            name.setText("无");
            summary.setText("默认图标");
            icon.setImageResource(R.drawable.material_symbols_add_2_rounded);
            icon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.colorSecondary));
            cb.setVisibility(View.GONE);
            card.setClickable(false);
            return row;
        }

        name.setText(iconDisplayName(uri));
        summary.setText(manageMode ? "勾选后可批量删除" : "点击应用该图标");
        cb.setVisibility(manageMode ? View.VISIBLE : View.GONE);
        cb.setChecked(selectedIcons.contains(uri));
        cb.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) {
                selectedIcons.add(uri);
            } else {
                selectedIcons.remove(uri);
            }
        });
        bindCustomIcon(icon, uri);
        card.setOnClickListener(v -> {
            if (manageMode) {
                cb.toggle();
            } else {
                persistSlotUri(uri);
                Toast.makeText(requireContext(), "已设置准星图标", Toast.LENGTH_SHORT).show();
            }
        });
        return row;
    }

    private void setManageMode(boolean on) {
        manageMode = on;
        selectedIcons.clear();
        binding.btnManageCrosshair.setText(on ? "完成" : "批量管理");
        binding.layoutCrosshairManageActions.setVisibility(on ? View.VISIBLE : View.GONE);
        binding.btnImportCrosshair.setEnabled(!on);
        renderLibrary();
    }

    private void deleteSelectedIcons() {
        if (selectedIcons.isEmpty()) {
            Toast.makeText(requireContext(), "请先勾选要删除的图标", Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> removed = new ArrayList<>(selectedIcons);
        String current = savedSlotUri();
        boolean slotChanged = false;
        for (String uri : removed) {
            iconLibrary.remove(uri);
            releaseUriPermission(uri);
            // 被删除的图标若正被槽位使用，回落为"无"（默认图标）
            if (uri.equals(current)) {
                current = "";
                slotChanged = true;
            }
        }
        saveLibrary();
        selectedIcons.clear();
        if (slotChanged) {
            persistSlotUri("");
        }
        setManageMode(false);
        Toast.makeText(requireContext(), "已删除 " + removed.size() + " 个图标",
                Toast.LENGTH_SHORT).show();
    }

    private void releaseUriPermission(String uri) {
        try {
            requireContext().getContentResolver().releasePersistableUriPermission(
                    Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
    }

    // ---------- 导入 ----------

    private void onIconPicked(@Nullable Uri uri) {
        if (uri == null) return;
        try {
            requireContext().getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception e) {
            AppLogger.w(TAG, "failed to take persistable permission: " + e.getMessage());
        }
        String value = uri.toString();
        if (!iconLibrary.contains(value)) {
            iconLibrary.add(value);
            saveLibrary();
        }
        if (pendingApply) {
            pendingApply = false;
            persistSlotUri(value);
            Toast.makeText(requireContext(), "已设置准星图标", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(), "已导入图标库", Toast.LENGTH_SHORT).show();
        }
        renderLibrary();
    }

    // ---------- 展示 ----------

    /** 把自定义图标 URI 加载到 ImageView，失败时回退为损坏图标占位。 */
    private void bindCustomIcon(ImageView target, String uri) {
        try (InputStream in = requireContext().getContentResolver()
                .openInputStream(Uri.parse(uri))) {
            Drawable d = in != null ? Drawable.createFromStream(in, null) : null;
            if (d != null) {
                target.setImageDrawable(d);
                target.clearColorFilter();
                target.setImageTintList(null);
                return;
            }
        } catch (Exception e) {
            AppLogger.w(TAG, "failed to load crosshair icon image: " + e.getMessage());
        }
        target.setImageResource(R.drawable.material_symbols_broken_image_outline_rounded);
        target.setColorFilter(ContextCompat.getColor(requireContext(), R.color.colorSecondary));
    }

    private String iconDisplayName(Uri uri) {
        try (Cursor c = requireContext().getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String n = c.getString(idx);
                    if (n != null && !n.isEmpty()) return n;
                }
            }
        } catch (Exception e) {
            AppLogger.w(TAG, "failed to query icon display name: " + e.getMessage());
        }
        return "";
    }

    private String iconDisplayName(String uri) {
        String name = iconDisplayName(Uri.parse(uri));
        return name.isEmpty() ? "自定义图标" : name;
    }
}
