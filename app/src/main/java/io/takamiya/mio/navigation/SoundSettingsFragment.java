package io.takamiya.mio.navigation;

import android.content.Intent;
import android.database.Cursor;
import android.media.MediaPlayer;
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
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.Registry.Registry;
import com.google.android.material.button.MaterialButton;
import io.takamiya.mio.R;
import io.takamiya.mio.databinding.FragmentSettingSoundBinding;
import io.takamiya.mio.floating.FloatingWindowService;
import io.takamiya.mio.utils.DataManage;
import io.takamiya.mio.utils.RegistryUtils;

public class SoundSettingsFragment extends Fragment {

    private static final String TAG = "SoundSettings";

    private FragmentSettingSoundBinding binding;

    private final List<String> soundLibrary = new ArrayList<>();
    private final Set<String> selectedSounds = new LinkedHashSet<>();
    private boolean manageMode;
    /** null=仅入库；0=快捷键开启槽；1=快捷键关闭槽 */
    private Integer pendingSlot;

    private MediaPlayer previewPlayer;
    private View previewButton;

    private final ActivityResultLauncher<String[]> soundPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onSoundPicked);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingSoundBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initShortcutSound();
        initLibrary();
    }

    @Override
    public void onDestroyView() {
        stopPreview();
        super.onDestroyView();
    }

    private void initShortcutSound() {
        View.OnClickListener openOn = v -> showSlotPicker(true);
        binding.cardShortcutSoundOn.setOnClickListener(openOn);
        binding.settingBtnShortcutSoundOn.setOnClickListener(openOn);

        View.OnClickListener openOff = v -> showSlotPicker(false);
        binding.cardShortcutSoundOff.setOnClickListener(openOff);
        binding.settingBtnShortcutSoundOff.setOnClickListener(openOff);

        updateSlotSummary(binding.tvShortcutSoundOnSummary,
                DataManage.Tag.APP_CONFIG_SHORTCUT_SOUND_ON_URI);
        updateSlotSummary(binding.tvShortcutSoundOffSummary,
                DataManage.Tag.APP_CONFIG_SHORTCUT_SOUND_OFF_URI);
    }

    private void initLibrary() {
        binding.btnImportSound.setOnClickListener(v -> {
            pendingSlot = null;
            soundPickerLauncher.launch(new String[]{"audio/*", "*/*"});
        });
        binding.btnManageSound.setOnClickListener(v -> setManageMode(!manageMode));
        binding.btnCancelManage.setOnClickListener(v -> setManageMode(false));
        binding.btnDeleteSounds.setOnClickListener(v -> deleteSelectedSounds());

        loadLibrary();
        migrateSlotUrisIntoLibrary();
        renderLibrary();
    }

    // ---------- 槽位选择（首项"无"） ----------

    private void showSlotPicker(boolean isOn) {
        DataManage.Tag tag = isOn
                ? DataManage.Tag.APP_CONFIG_SHORTCUT_SOUND_ON_URI
                : DataManage.Tag.APP_CONFIG_SHORTCUT_SOUND_OFF_URI;
        String current = savedSlotValue(tag);

        List<String> items = new ArrayList<>();
        items.add("无");
        items.add("默认音效");
        for (String uri : soundLibrary) {
            items.add(soundDisplayName(uri));
        }
        final int importIndex = items.size();
        items.add("导入音频文件…");

        int checked;
        if (DataManage.SOUND_NONE.equals(current)) {
            checked = 0;
        } else if (current.isEmpty()) {
            checked = 1;
        } else {
            int i = soundLibrary.indexOf(current);
            checked = i >= 0 ? 2 + i : 1;
        }

        final int checkedFinal = checked;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(isOn ? "快捷键开启音效" : "快捷键关闭音效")
                .setSingleChoiceItems(items.toArray(new String[0]), checkedFinal, (dialog, which) -> {
                    if (which == importIndex) {
                        pendingSlot = isOn ? 0 : 1;
                        soundPickerLauncher.launch(new String[]{"audio/*", "*/*"});
                    } else {
                        String value;
                        if (which == 0) {
                            value = DataManage.SOUND_NONE;
                        } else if (which == 1) {
                            value = "";
                        } else {
                            value = soundLibrary.get(which - 2);
                        }
                        persistSlotValue(tag, value);
                        updateSlotSummary(isOn
                                ? binding.tvShortcutSoundOnSummary
                                : binding.tvShortcutSoundOffSummary, tag);
                        FloatingWindowService.applyShortcutSound();
                        previewValue(isOn, value);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateSlotSummary(TextView summary, DataManage.Tag tag) {
        String value = savedSlotValue(tag);
        if (value.isEmpty()) {
            summary.setText("默认音效");
        } else if (DataManage.SOUND_NONE.equals(value)) {
            summary.setText("无");
        } else {
            String name = soundDisplayName(Uri.parse(value));
            summary.setText(name.isEmpty() ? "自定义音效" : name);
        }
    }

    private String savedSlotValue(DataManage.Tag tag) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return "";
        try {
            if (reg.exists(tag.getValue())) {
                String s = reg.getString(tag.getValue());
                if (s != null) return s;
            }
        } catch (Exception e) {
            AppLogger.w(TAG, "failed to load shortcut sound value: " + e.getMessage());
        }
        return "";
    }

    private void persistSlotValue(DataManage.Tag tag, String value) {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.set(tag.getValue(), value);
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "save shortcut sound failed: " + e.getMessage(), e);
        }
    }

    // ---------- 音效库 ----------

    private void loadLibrary() {
        soundLibrary.clear();
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            if (reg.exists(DataManage.Tag.APP_CONFIG_SOUND_LIBRARY.getValue())) {
                String raw = reg.getString(DataManage.Tag.APP_CONFIG_SOUND_LIBRARY.getValue());
                if (raw != null && !raw.isEmpty()) {
                    for (String line : raw.split("\n")) {
                        String uri = line.trim();
                        if (!uri.isEmpty() && !soundLibrary.contains(uri)) {
                            soundLibrary.add(uri);
                        }
                    }
                }
            }
        } catch (Exception e) {
            AppLogger.w(TAG, "failed to load sound library: " + e.getMessage());
        }
    }

    private void saveLibrary() {
        Registry reg = RegistryUtils.get();
        if (reg == null) return;
        try {
            reg.set(DataManage.Tag.APP_CONFIG_SOUND_LIBRARY.getValue(),
                    String.join("\n", soundLibrary));
            reg.saveAsync();
        } catch (Exception e) {
            AppLogger.e(TAG, "save sound library failed: " + e.getMessage(), e);
        }
    }

    /** 历史槽位直接存过 URI 但未入库时，补进音效库保证可选择/可管理。 */
    private void migrateSlotUrisIntoLibrary() {
        boolean changed = false;
        DataManage.Tag[] tags = {
                DataManage.Tag.APP_CONFIG_SHORTCUT_SOUND_ON_URI,
                DataManage.Tag.APP_CONFIG_SHORTCUT_SOUND_OFF_URI
        };
        for (DataManage.Tag tag : tags) {
            String v = savedSlotValue(tag);
            if (!v.isEmpty() && !DataManage.SOUND_NONE.equals(v) && !soundLibrary.contains(v)) {
                soundLibrary.add(v);
                changed = true;
            }
        }
        if (changed) saveLibrary();
    }

    private void renderLibrary() {
        if (binding == null) return;
        stopPreview();
        binding.layoutSoundLibrary.removeAllViews();
        binding.layoutSoundLibrary.addView(buildRow(null));
        for (String uri : soundLibrary) {
            binding.layoutSoundLibrary.addView(buildRow(uri));
        }
    }

    /** uri 为 null 时渲染固定的"无"行（不可选、不可删、无试听）。 */
    private View buildRow(@Nullable final String uri) {
        View row = getLayoutInflater().inflate(R.layout.item_sound_library,
                binding.layoutSoundLibrary, false);
        CheckBox cb = row.findViewById(R.id.cbSoundSelect);
        TextView name = row.findViewById(R.id.tvSoundName);
        TextView summary = row.findViewById(R.id.tvSoundSummary);
        ImageView icon = row.findViewById(R.id.ivSoundIcon);
        View play = row.findViewById(R.id.btnSoundPlay);
        View card = row.findViewById(R.id.cardSoundItem);

        if (uri == null) {
            name.setText("无");
            summary.setText("不使用音效");
            icon.setImageResource(R.drawable.material_symbols_block_outline);
            play.setVisibility(View.GONE);
            cb.setVisibility(View.GONE);
            card.setClickable(false);
            return row;
        }

        name.setText(soundDisplayName(uri));
        summary.setText(manageMode ? "勾选后可批量删除" : "点击试听");
        play.setVisibility(manageMode ? View.GONE : View.VISIBLE);
        cb.setVisibility(manageMode ? View.VISIBLE : View.GONE);
        cb.setChecked(selectedSounds.contains(uri));
        cb.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) {
                selectedSounds.add(uri);
            } else {
                selectedSounds.remove(uri);
            }
        });
        play.setOnClickListener(v -> toggleRowPreview(uri, play));
        card.setOnClickListener(v -> {
            if (manageMode) {
                cb.toggle();
            } else {
                toggleRowPreview(uri, play);
            }
        });
        return row;
    }

    private void setManageMode(boolean on) {
        manageMode = on;
        selectedSounds.clear();
        binding.btnManageSound.setText(on ? "完成" : "批量管理");
        binding.layoutManageActions.setVisibility(on ? View.VISIBLE : View.GONE);
        binding.btnImportSound.setEnabled(!on);
        renderLibrary();
    }

    private void deleteSelectedSounds() {
        if (selectedSounds.isEmpty()) {
            Toast.makeText(requireContext(), "请先勾选要删除的音效", Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> removed = new ArrayList<>(selectedSounds);
        DataManage.Tag onTag = DataManage.Tag.APP_CONFIG_SHORTCUT_SOUND_ON_URI;
        DataManage.Tag offTag = DataManage.Tag.APP_CONFIG_SHORTCUT_SOUND_OFF_URI;
        for (String uri : removed) {
            soundLibrary.remove(uri);
            releaseUriPermission(uri);
            // 被删除的音效若正被槽位使用，回落为"无"
            if (uri.equals(savedSlotValue(onTag))) {
                persistSlotValue(onTag, DataManage.SOUND_NONE);
            }
            if (uri.equals(savedSlotValue(offTag))) {
                persistSlotValue(offTag, DataManage.SOUND_NONE);
            }
        }
        saveLibrary();
        selectedSounds.clear();
        updateSlotSummary(binding.tvShortcutSoundOnSummary, onTag);
        updateSlotSummary(binding.tvShortcutSoundOffSummary, offTag);
        FloatingWindowService.applyShortcutSound();
        setManageMode(false);
        Toast.makeText(requireContext(), "已删除 " + removed.size() + " 个音效",
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

    private void onSoundPicked(@Nullable Uri uri) {
        if (uri == null) return;
        try {
            requireContext().getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception e) {
            AppLogger.w(TAG, "failed to take persistable permission: " + e.getMessage());
        }
        String value = uri.toString();
        if (!soundLibrary.contains(value)) {
            soundLibrary.add(value);
            saveLibrary();
        }
        Integer slot = pendingSlot;
        pendingSlot = null;
        if (slot != null) {
            DataManage.Tag tag = slot == 0
                    ? DataManage.Tag.APP_CONFIG_SHORTCUT_SOUND_ON_URI
                    : DataManage.Tag.APP_CONFIG_SHORTCUT_SOUND_OFF_URI;
            persistSlotValue(tag, value);
            updateSlotSummary(slot == 0
                    ? binding.tvShortcutSoundOnSummary
                    : binding.tvShortcutSoundOffSummary, tag);
            FloatingWindowService.applyShortcutSound();
            Toast.makeText(requireContext(), "已设置快捷键音效", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(), "已导入音效库", Toast.LENGTH_SHORT).show();
        }
        renderLibrary();
    }

    // ---------- 试听 ----------

    private void previewValue(boolean isOn, String value) {
        if (value == null || DataManage.SOUND_NONE.equals(value)) {
            stopPreview();
            return;
        }
        if (value.isEmpty()) {
            startPreview(createDefaultPlayer(isOn), null);
        } else {
            startPreview(createUriPlayer(Uri.parse(value)), null);
        }
    }

    // ---------- 试听播放控制 ----------

    private void toggleRowPreview(String uri, View button) {
        if (previewButton == button && previewPlayer != null) {
            stopPreview();
            return;
        }
        MediaPlayer player = createUriPlayer(Uri.parse(uri));
        if (player == null) {
            Toast.makeText(requireContext(), "无法播放该音效", Toast.LENGTH_SHORT).show();
            return;
        }
        startPreview(player, button);
    }

    /** 接管播放：停止上一个预览并把按钮图标切换为暂停。 */
    private void startPreview(@Nullable MediaPlayer player, @Nullable View button) {
        stopPreview();
        if (player == null) return;
        previewPlayer = player;
        previewButton = button;
        setPlayIcon(button, false);
        player.setOnCompletionListener(mp -> stopPreview());
        player.setOnErrorListener((mp, what, extra) -> {
            stopPreview();
            return true;
        });
        player.start();
    }

    private void stopPreview() {
        if (previewPlayer != null) {
            try {
                previewPlayer.stop();
            } catch (Exception ignored) {
            }
            previewPlayer.release();
            previewPlayer = null;
        }
        setPlayIcon(previewButton, true);
        previewButton = null;
    }

    /** true=播放图标，false=暂停图标。 */
    private void setPlayIcon(@Nullable View button, boolean playing) {
        if (button instanceof MaterialButton) {
            ((MaterialButton) button).setIconResource(playing
                    ? R.drawable.material_symbols_play_arrow_rounded
                    : R.drawable.material_symbols_pause_rounded);
        }
    }

    @Nullable
    private MediaPlayer createDefaultPlayer(boolean isOn) {
        try {
            return MediaPlayer.create(requireContext(),
                    isOn ? R.raw.click_down : R.raw.click_up);
        } catch (Exception e) {
            AppLogger.w(TAG, "preview default sound failed: " + e.getMessage());
            return null;
        }
    }

    @Nullable
    private MediaPlayer createUriPlayer(Uri uri) {
        try {
            return MediaPlayer.create(requireContext(), uri);
        } catch (Exception e) {
            AppLogger.w(TAG, "preview sound failed: " + e.getMessage());
            return null;
        }
    }

    private String soundDisplayName(Uri uri) {
        try (Cursor c = requireContext().getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String n = c.getString(idx);
                    if (n != null && !n.isEmpty()) return n;
                }
            }
        } catch (Exception e) {
            AppLogger.w(TAG, "failed to query sound display name: " + e.getMessage());
        }
        return "";
    }

    private String soundDisplayName(String uri) {
        String name = soundDisplayName(Uri.parse(uri));
        return name.isEmpty() ? "自定义音效" : name;
    }
}
