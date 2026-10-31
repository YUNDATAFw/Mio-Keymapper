package io.takamiya.mio.adapter;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import io.takamiya.mio.R;
import io.takamiya.mio.config.Action;
import io.takamiya.mio.config.ActionType;
import io.takamiya.mio.config.DuplicateConfiguration;
import io.takamiya.mio.config.KeyMapping;
import io.takamiya.mio.config.ToastSettings;
import io.takamiya.mio.databinding.ItemVisualEditorEventBinding;

public class ActionEventAdapter extends RecyclerView.Adapter<ActionEventAdapter.ViewHolder> {

    private final Activity activity;
    private final List<Action> actionList;
    private OnActionChangedListener listener;

    public interface OnActionChangedListener {
        void onActionChanged(int position, Action action);
        void onActionDeleteRequested(int position, Action action);
    }

    public ActionEventAdapter(Activity activity, List<Action> actionList) {
        this.activity = activity;
        this.actionList = actionList;
    }

    public void setOnActionChangedListener(OnActionChangedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemVisualEditorEventBinding binding = ItemVisualEditorEventBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(actionList.get(position), position);
    }

    @Override
    public int getItemCount() {
        return actionList == null ? 0 : actionList.size();
    }

    private void showTypePicker(Action action, int position) {
        String[] types = {"INPUT_KEY", "INPUT_KEY_GROUP", "INPUT_TEXT", "TOAST"};
        String[] labels = {"输入按键 (INPUT_KEY)", "按键组合 (INPUT_KEY_GROUP)", "输入文本 (INPUT_TEXT)", "悬浮提示 (TOAST)"};
        int checked = 0;
        if (action.getType() != null) {
            for (int i = 0; i < types.length; i++) {
                if (types[i].equals(action.getType().name())) {
                    checked = i;
                    break;
                }
            }
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle("选择动作类型")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    ActionType newType = ActionType.fromString(types[which]);
                    if (newType == ActionType.INPUT_TEXT || newType == ActionType.TOAST) {
                        action.setKey(null);
                        action.setCombinationKeys(null);
                    }
                    action.setType(newType);
                    notifyChanged(action, position);
                    notifyDataSetChanged();
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showKeyPicker(Action action, int position) {
        String[] keys = KeyMapping.getAllKeyNames();
        String current = action.getKey();
        int checked = -1;
        if (current != null) {
            for (int i = 0; i < keys.length; i++) {
                if (keys[i].equals(current)) {
                    checked = i;
                    break;
                }
            }
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle("选择按键")
                .setSingleChoiceItems(keys, checked, (dialog, which) -> {
                    action.setKey(keys[which]);
                    notifyChanged(action, position);
                    notifyDataSetChanged();
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showCombinationKeyPicker(Action action, int position) {
        String[] keys = KeyMapping.getAllKeyNames();
        List<String> currentKeys = action.getCombinationKeys();
        boolean[] checked = new boolean[keys.length];
        if (currentKeys != null) {
            Set<String> currentSet = new HashSet<>(currentKeys);
            for (int i = 0; i < keys.length; i++) {
                checked[i] = currentSet.contains(keys[i]);
            }
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle("选择组合键 (可多选)")
                .setMultiChoiceItems(keys, checked, (dialog, which, isChecked) -> {
                    checked[which] = isChecked;
                })
                .setPositiveButton("确定", (dialog, which) -> {
                    List<String> selected = new ArrayList<>();
                    for (int i = 0; i < keys.length; i++) {
                        if (checked[i]) selected.add(keys[i]);
                    }
                    action.setCombinationKeys(selected);
                    notifyChanged(action, position);
                    notifyDataSetChanged();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void ensureToastSettings(Action action) {
        if (action.getToastSettings() == null) {
            action.setToastSettings(new ToastSettings());
        }
    }

    private void showColorPicker(String title, Integer currentColor, Consumer<Integer> onPick) {
        final int[] palette = {
                0xFF000000, 0xFFFFFFFF, 0xFFF44336, 0xFFE91E63, 0xFF9C27B0,
                0xFF673AB7, 0xFF3F51B5, 0xFF2196F3, 0xFF03A9F4, 0xFF00BCD4,
                0xFF009688, 0xFF4CAF50, 0xFF8BC34A, 0xFFFFEB3B, 0xFFFF9800,
                0xFFFF5722, 0xFF795548, 0xFF607D8B, 0xFF37474F, 0xFFB0BEC5
        };
        GridLayout grid = new GridLayout(activity);
        grid.setColumnCount(5);
        int swatchSize = activity.getResources().getDimensionPixelSize(R.dimen.color_swatch_size);
        int spacing = activity.getResources().getDimensionPixelSize(R.dimen.color_swatch_spacing);
        for (int color : palette) {
            View swatch = new View(activity);
            int selected = (currentColor != null && currentColor == color) ? 1 : 0;
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(color);
            if (selected == 1) {
                bg.setStroke(activity.getResources().getDimensionPixelSize(R.dimen.color_swatch_stroke), Color.WHITE);
            }
            swatch.setBackground(bg);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = swatchSize;
            lp.height = swatchSize;
            lp.setMargins(spacing, spacing, spacing, spacing);
            grid.addView(swatch, lp);
            swatch.setOnClickListener(v -> {
                if (onPick != null) onPick.accept(color);
            });
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setView(grid)
                .setNegativeButton("清除设置", (dialog, which) -> {
                    if (onPick != null) onPick.accept(null);
                })
                .show();
    }

    private static String colorLabel(Integer color) {
        if (color == null) return "默认";
        return String.format("#%08X", color);
    }

    private void notifyChanged(Action action, int position) {
        if (listener != null) {
            listener.onActionChanged(position, action);
        }
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return 0; }
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; }
    }

    private static abstract class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemVisualEditorEventBinding binding;
        private boolean expanded = false;
        private TextWatcher wMappedInput, wBeforeDelay, wAfterDelay, wRepeatBeforeDelay, wRepeatAfterDelay, wCharDelay, wKeyHoldDuration;
        private TextWatcher wToastDuration, wToastFontSize;
        private TextWatcher wIntervalDuration, wNumberOfRepetitions;

        ViewHolder(@NonNull ItemVisualEditorEventBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Action action, int position) {
            removeWatchers();

            ActionType type = action.getType() != null ? action.getType() : ActionType.INPUT_KEY;
            boolean isKey = type == ActionType.INPUT_KEY || type == ActionType.INPUT_KEY_GROUP;
            boolean isGroup = type == ActionType.INPUT_KEY_GROUP;
            boolean isText = type == ActionType.INPUT_TEXT;
            boolean isToast = type == ActionType.TOAST;

            binding.tvHeaderTitle.setText(getActionTypeName(type));
            binding.tvDescription.setText(buildDescription(action));
            binding.tvActionType.setText(type.name());
            binding.cardActionType.setOnClickListener(v -> showTypePicker(action, position));

            if (isKey) {
                binding.tvKey.setText(action.getKey() != null ? action.getKey() : "点击选择按键");
                binding.cardKey.setOnClickListener(v -> showKeyPicker(action, position));
                binding.cardKey.setVisibility(View.VISIBLE);
            } else {
                binding.cardKey.setVisibility(View.GONE);
            }

            if (isGroup) {
                List<String> keys = action.getCombinationKeys();
                binding.tvCombinationKeys.setText(keys != null && !keys.isEmpty() ? String.join(", ", keys) : "点击选择组合键");
                binding.cardCombinationKeys.setOnClickListener(v -> showCombinationKeyPicker(action, position));
                binding.cardCombinationKeys.setVisibility(View.VISIBLE);
            } else {
                binding.cardCombinationKeys.setVisibility(View.GONE);
            }

            if (isText || isToast) {
                binding.layoutMappedInput.setHint(isToast ? "提示内容" : "映射输入内容");
                binding.etMappedInput.setText(action.getText() != null ? action.getText() : "");
                binding.layoutMappedInput.setVisibility(View.VISIBLE);
                wMappedInput = new SimpleTextWatcher() {
                    @Override public void afterTextChanged(Editable s) {
                        action.setText(s.toString());
                        notifyChanged(action, position);
                    }
                };
                binding.etMappedInput.addTextChangedListener(wMappedInput);
            } else {
                binding.layoutMappedInput.setVisibility(View.GONE);
            }

            binding.toastConfigBlock.setVisibility(isToast ? View.VISIBLE : View.GONE);
            if (isToast) {
                ensureToastSettings(action);
                binding.etToastDuration.setText(action.getToastSettings().getDurationMs() != null ? String.valueOf(action.getToastSettings().getDurationMs()) : "");
                binding.etToastFontSize.setText(action.getToastSettings().getFontSize() != null ? String.valueOf(action.getToastSettings().getFontSize()) : "");
                binding.tvToastTextColor.setText(colorLabel(action.getToastSettings().getTextColor()));
                binding.tvToastBgColor.setText(colorLabel(action.getToastSettings().getBackgroundColor()));
                binding.switchToastBold.setChecked(action.getToastSettings().getBold() != null && action.getToastSettings().getBold());
                binding.cardToastTextColor.setOnClickListener(v -> showColorPicker("文字颜色", action.getToastSettings().getTextColor(), color -> {
                    ensureToastSettings(action);
                    action.getToastSettings().setTextColor(color);
                    notifyChanged(action, position);
                    binding.tvToastTextColor.setText(colorLabel(color));
                }));
                binding.cardToastBgColor.setOnClickListener(v -> showColorPicker("背景颜色", action.getToastSettings().getBackgroundColor(), color -> {
                    ensureToastSettings(action);
                    action.getToastSettings().setBackgroundColor(color);
                    notifyChanged(action, position);
                    binding.tvToastBgColor.setText(colorLabel(color));
                }));
                binding.switchToastBold.setOnCheckedChangeListener((btn, checked) -> {
                    ensureToastSettings(action);
                    action.getToastSettings().setBold(checked);
                    notifyChanged(action, position);
                });
                wToastDuration = new SimpleTextWatcher() {
                    @Override public void afterTextChanged(Editable s) {
                        String text = s.toString().trim();
                        ensureToastSettings(action);
                        action.getToastSettings().setDurationMs(text.isEmpty() ? null : parseInt(text));
                        notifyChanged(action, position);
                    }
                };
                binding.etToastDuration.addTextChangedListener(wToastDuration);
                wToastFontSize = new SimpleTextWatcher() {
                    @Override public void afterTextChanged(Editable s) {
                        String text = s.toString().trim();
                        ensureToastSettings(action);
                        action.getToastSettings().setFontSize(text.isEmpty() ? null : parseInt(text));
                        notifyChanged(action, position);
                    }
                };
                binding.etToastFontSize.addTextChangedListener(wToastFontSize);
            }

            binding.holdConfigBlock.setVisibility(isToast ? View.GONE : View.VISIBLE);
            binding.charDelayBlock.setVisibility(isText ? View.VISIBLE : View.GONE);
            binding.keyHoldDurationBlock.setVisibility(isKey ? View.VISIBLE : View.GONE);

            binding.switchHoldMode.setChecked(action.isHold());
            binding.etBeforeDelay.setText(action.getBeforeDelayMs() != null ? String.valueOf(action.getBeforeDelayMs()) : "");
            binding.etAfterDelay.setText(action.getAfterDelayMs() != null ? String.valueOf(action.getAfterDelayMs()) : "");
            binding.etRepeatBeforeDelay.setText(action.getRepeatBeforeDelayMs() != null ? String.valueOf(action.getRepeatBeforeDelayMs()) : "");
            binding.etRepeatAfterDelay.setText(action.getRepeatAfterDelayMs() != null ? String.valueOf(action.getRepeatAfterDelayMs()) : "");
            binding.etCharDelay.setText(action.getCharDelayMs() != null ? String.valueOf(action.getCharDelayMs()) : "");
            binding.etKeyHoldDuration.setText(action.getKeyHoldDuration() != null ? String.valueOf(action.getKeyHoldDuration()) : "");

            binding.switchHoldMode.setOnCheckedChangeListener((btn, checked) -> {
                if (checked && action.getType() == ActionType.INPUT_TEXT) {
                    btn.setChecked(false);
                    Toast.makeText(activity, "按住模式不能用于文本输入", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (checked && action.getType() == ActionType.TOAST) {
                    btn.setChecked(false);
                    Toast.makeText(activity, "按住模式不能用于悬浮提示", Toast.LENGTH_SHORT).show();
                    return;
                }
                action.setHold(checked);
                notifyChanged(action, position);
            });

            wBeforeDelay = new SimpleTextWatcher() {
                @Override public void afterTextChanged(Editable s) {
                    String text = s.toString().trim();
                    action.setBeforeDelayMs(text.isEmpty() ? null : parseLong(text));
                    notifyChanged(action, position);
                }
            };
            binding.etBeforeDelay.addTextChangedListener(wBeforeDelay);

            wAfterDelay = new SimpleTextWatcher() {
                @Override public void afterTextChanged(Editable s) {
                    String text = s.toString().trim();
                    action.setAfterDelayMs(text.isEmpty() ? null : parseLong(text));
                    notifyChanged(action, position);
                }
            };
            binding.etAfterDelay.addTextChangedListener(wAfterDelay);

            wRepeatBeforeDelay = new SimpleTextWatcher() {
                @Override public void afterTextChanged(Editable s) {
                    String text = s.toString().trim();
                    action.setRepeatBeforeDelayMs(text.isEmpty() ? null : parseLong(text));
                    notifyChanged(action, position);
                }
            };
            binding.etRepeatBeforeDelay.addTextChangedListener(wRepeatBeforeDelay);

            wRepeatAfterDelay = new SimpleTextWatcher() {
                @Override public void afterTextChanged(Editable s) {
                    String text = s.toString().trim();
                    action.setRepeatAfterDelayMs(text.isEmpty() ? null : parseLong(text));
                    notifyChanged(action, position);
                }
            };
            binding.etRepeatAfterDelay.addTextChangedListener(wRepeatAfterDelay);

            wCharDelay = new SimpleTextWatcher() {
                @Override public void afterTextChanged(Editable s) {
                    String text = s.toString().trim();
                    action.setCharDelayMs(text.isEmpty() ? null : parseLong(text));
                    notifyChanged(action, position);
                }
            };
            binding.etCharDelay.addTextChangedListener(wCharDelay);

            wKeyHoldDuration = new SimpleTextWatcher() {
                @Override public void afterTextChanged(Editable s) {
                    String text = s.toString().trim();
                    action.setKeyHoldDuration(text.isEmpty() ? null : parseLong(text));
                    notifyChanged(action, position);
                }
            };
            binding.etKeyHoldDuration.addTextChangedListener(wKeyHoldDuration);

            // ── 循环/重复设置 ──
            boolean isRepeat = action.isRepeat();
            binding.switchRepeat.setChecked(isRepeat);
            binding.repeatDelayBlock.setVisibility(isRepeat ? View.VISIBLE : View.GONE);
            binding.switchRepeat.setOnCheckedChangeListener((btn, checked) -> {
                action.setRepeat(checked);
                if (checked) action.ensureDuplicateConfiguration();
                binding.repeatDetailsBlock.setVisibility(checked ? View.VISIBLE : View.GONE);
                binding.repeatDelayBlock.setVisibility(checked ? View.VISIBLE : View.GONE);
                notifyChanged(action, position);
            });
            binding.repeatDetailsBlock.setVisibility(isRepeat ? View.VISIBLE : View.GONE);

            DuplicateConfiguration dc = action.getDuplicateConfiguration();
            if (dc == null) dc = action.ensureDuplicateConfiguration();

            int stopCond = dc.getStopRepeatingConditions();
            binding.chipGroupRepeatMode.setOnCheckedStateChangeListener(null);
            switch (stopCond) {
                case 1: binding.chipRepeatClick.setChecked(true); break;
                case 2: binding.chipRepeatLimit.setChecked(true); break;
                default: binding.chipRepeatRelease.setChecked(true); break;
            }
            binding.chipGroupRepeatMode.setOnCheckedStateChangeListener((g, checkedIds) -> {
                action.ensureDuplicateConfiguration().setStopRepeatingConditions(resolveStopConditions());
                notifyChanged(action, position);
            });

            binding.tilRepeatLimit.setVisibility(
                    dc.getStopRepeatingConditions() == 2 ? View.VISIBLE : View.GONE);
            binding.tilIntervalDuration.setVisibility(isRepeat ? View.VISIBLE : View.GONE);

            binding.etIntervalDuration.setText(String.valueOf(dc.getIntervalDuration()));
            binding.etRepeatLimit.setText(String.valueOf(dc.getNumberOfRepetitions()));

            wIntervalDuration = new SimpleTextWatcher() {
                @Override public void afterTextChanged(Editable s) {
                    String text = s.toString().trim();
                    action.ensureDuplicateConfiguration().setIntervalDuration(text.isEmpty() ? 40 : parseLong(text));
                    notifyChanged(action, position);
                }
            };
            binding.etIntervalDuration.addTextChangedListener(wIntervalDuration);

            wNumberOfRepetitions = new SimpleTextWatcher() {
                @Override public void afterTextChanged(Editable s) {
                    String text = s.toString().trim();
                    action.ensureDuplicateConfiguration().setNumberOfRepetitions(text.isEmpty() ? 1 : parseInt(text));
                    notifyChanged(action, position);
                }
            };
            binding.etRepeatLimit.addTextChangedListener(wNumberOfRepetitions);

            binding.collapseContainer.setVisibility(expanded ? View.VISIBLE : View.GONE);
            binding.btnToggleCollapse.setRotation(expanded ? 180f : 0f);
            binding.btnToggleCollapse.setOnClickListener(v -> {
                expanded = !expanded;
                binding.collapseContainer.setVisibility(expanded ? View.VISIBLE : View.GONE);
                ObjectAnimator.ofFloat(binding.btnToggleCollapse, "rotation", expanded ? 0f : 180f, expanded ? 180f : 0f)
                        .setDuration(200)
                        .start();
            });

            binding.btnDeleteAction.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onActionDeleteRequested(position, action);
                }
            });
        }

        private void removeWatchers() {
            if (wMappedInput != null) binding.etMappedInput.removeTextChangedListener(wMappedInput);
            if (wBeforeDelay != null) binding.etBeforeDelay.removeTextChangedListener(wBeforeDelay);
            if (wAfterDelay != null) binding.etAfterDelay.removeTextChangedListener(wAfterDelay);
            if (wRepeatBeforeDelay != null) binding.etRepeatBeforeDelay.removeTextChangedListener(wRepeatBeforeDelay);
            if (wRepeatAfterDelay != null) binding.etRepeatAfterDelay.removeTextChangedListener(wRepeatAfterDelay);
            if (wCharDelay != null) binding.etCharDelay.removeTextChangedListener(wCharDelay);
            if (wKeyHoldDuration != null) binding.etKeyHoldDuration.removeTextChangedListener(wKeyHoldDuration);
            if (wToastDuration != null) binding.etToastDuration.removeTextChangedListener(wToastDuration);
            if (wToastFontSize != null) binding.etToastFontSize.removeTextChangedListener(wToastFontSize);
            if (wIntervalDuration != null) binding.etIntervalDuration.removeTextChangedListener(wIntervalDuration);
            if (wNumberOfRepetitions != null) binding.etRepeatLimit.removeTextChangedListener(wNumberOfRepetitions);
        }

        private int resolveStopConditions() {
            if (binding.chipRepeatLimit.isChecked()) return 2;
            if (binding.chipRepeatClick.isChecked()) return 1;
            return 0;
        }

        private String getActionTypeName(ActionType type) {
            if (type == null) return "未知";
            switch (type) {
                case INPUT_KEY: return "按键输入";
                case INPUT_KEY_GROUP: return "组合键输入";
                case INPUT_TEXT: return "文本输入";
                case TOAST: return "悬浮提示";
                default: return type.name();
            }
        }

        private String buildDescription(Action action) {
            StringBuilder sb = new StringBuilder();
            ActionType type = action.getType();
            if (type != null) {
                sb.append(getActionTypeName(type));
            }
            if (type == ActionType.INPUT_KEY || type == ActionType.INPUT_KEY_GROUP) {
                if (action.getKey() != null && !action.getKey().isEmpty()) {
                    sb.append(" | ").append(action.getKey());
                }
            }
            if (action.getText() != null && !action.getText().isEmpty()) {
                sb.append(" | \"").append(action.getText()).append("\"");
            }
            if (action.isHold()) {
                sb.append(" | 按住");
            }
            return sb.length() > 0 ? sb.toString() : "空动作";
        }
    }
}
