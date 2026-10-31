package io.takamiya.mio.adapter;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

import io.takamiya.mio.R;
import io.takamiya.mio.config.ActionItem;
import io.takamiya.mio.config.ShortcutEventType;
import io.takamiya.mio.config.TriggerModel;
import io.takamiya.mio.databinding.ItemVisualEditActionBinding;

public class ActionItemAdapter extends RecyclerView.Adapter<ActionItemAdapter.ViewHolder> {

    private final Activity activity;
    private List<ActionItem> actionList;
    private OnToggleListener toggleListener;
    private OnActionEditListener actionEditListener;
    private OnActionItemChangedListener changedListener;
    private OnEventEditListener eventEditListener;

    public interface OnToggleListener {
        void onToggle(int position, boolean expanded);
    }

    public interface OnActionEditListener {
        void onActionEdit(ActionItem item);
    }

    public interface OnActionItemChangedListener {
        void onActionItemChanged(int position, ActionItem item);
        void onActionItemDeleteRequested(int position, ActionItem item);
    }

    public interface OnEventEditListener {
        void onEventEdit(ActionItem item, ShortcutEventType eventType);
    }

    public ActionItemAdapter(Activity activity, List<ActionItem> actionList) {
        this.activity = activity;
        this.actionList = actionList;
    }

    public void setOnToggleListener(OnToggleListener listener) {
        this.toggleListener = listener;
    }

    public void setOnActionEditListener(OnActionEditListener listener) {
        this.actionEditListener = listener;
    }

    public void setOnActionItemChangedListener(OnActionItemChangedListener listener) {
        this.changedListener = listener;
    }

    public void setOnEventEditListener(OnEventEditListener listener) {
        this.eventEditListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemVisualEditActionBinding binding = ItemVisualEditActionBinding.inflate(
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

    public void updateData(List<ActionItem> newList) {
        this.actionList = newList;
        notifyDataSetChanged();
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemVisualEditActionBinding binding;
        private boolean expanded = false;
        private TextWatcher wName, wDescription, wId, wGroup, wHoldThreshold, wReleaseDelay;

        ViewHolder(@NonNull ItemVisualEditActionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ActionItem item, int position) {
            removeWatchers();

            binding.tvHeaderTitle.setText(item.getTitle());
            binding.tvDescription.setText(item.getDescription());
            binding.etActionName.setText(item.getTitle());
            binding.etActionDescription.setText(item.getDescription());
            binding.etActionId.setText(item.getId());
            binding.etActionGroup.setText(item.getGroup());
            updateGroupBadge(item);

            binding.chipGroupTrigger.setOnCheckedChangeListener(null);
            if (item.getTriggerModel() == TriggerModel.SWITCH) {
                binding.chipSwitch.setChecked(true);
            } else if (item.getTriggerModel() == TriggerModel.PRESSURE_PLATE) {
                binding.chipPressurePlate.setChecked(true);
            } else {
                binding.chipButton.setChecked(true);
            }
            updatePressurePlateVisibility(item);
            binding.chipGroupTrigger.setOnCheckedChangeListener((group, checkedId) -> {
                if (checkedId == R.id.chipSwitch) {
                    item.setTriggerModel(TriggerModel.SWITCH);
                } else if (checkedId == R.id.chipPressurePlate) {
                    item.setTriggerModel(TriggerModel.PRESSURE_PLATE);
                } else {
                    item.setTriggerModel(TriggerModel.BUTTON);
                }
                updatePressurePlateVisibility(item);
                notifyChanged(item, position);
            });

            wName = new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    item.setTitle(s.toString());
                    binding.tvHeaderTitle.setText(s.toString());
                    notifyChanged(item, position);
                }
            };
            binding.etActionName.addTextChangedListener(wName);

            wDescription = new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    item.setDescription(s.toString());
                    binding.tvDescription.setText(s.toString());
                    notifyChanged(item, position);
                }
            };
            binding.etActionDescription.addTextChangedListener(wDescription);

            wId = new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    item.setId(s.toString());
                    notifyChanged(item, position);
                }
            };
            binding.etActionId.addTextChangedListener(wId);

            wGroup = new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    item.setGroup(s.toString());
                    updateGroupBadge(item);
                    notifyChanged(item, position);
                }
            };
            binding.etActionGroup.addTextChangedListener(wGroup);

            if (item.getHoldThresholdMs() != null && item.getHoldThresholdMs() > 0) {
                binding.etHoldThreshold.setText(String.valueOf(item.getHoldThresholdMs()));
            } else {
                binding.etHoldThreshold.setText("");
            }
            wHoldThreshold = new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    String text = s.toString().trim();
                    item.setHoldThresholdMs(text.isEmpty() ? null : Integer.parseInt(text));
                    notifyChanged(item, position);
                }
            };
            binding.etHoldThreshold.addTextChangedListener(wHoldThreshold);

            if (item.getReleaseDelayMs() != null && item.getReleaseDelayMs() > 0) {
                binding.etReleaseDelay.setText(String.valueOf(item.getReleaseDelayMs()));
            } else {
                binding.etReleaseDelay.setText("");
            }
            wReleaseDelay = new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    String text = s.toString().trim();
                    item.setReleaseDelayMs(text.isEmpty() ? null : Integer.parseInt(text));
                    notifyChanged(item, position);
                }
            };
            binding.etReleaseDelay.addTextChangedListener(wReleaseDelay);

            binding.collapseContainer.setVisibility(expanded ? View.VISIBLE : View.GONE);
            binding.btnToggleCollapse.setRotation(expanded ? 180f : 0f);

            binding.btnToggleCollapse.setOnClickListener(v -> {
                expanded = !expanded;
                binding.collapseContainer.setVisibility(expanded ? View.VISIBLE : View.GONE);
                ObjectAnimator.ofFloat(binding.btnToggleCollapse, "rotation", expanded ? 0f : 180f, expanded ? 180f : 0f)
                        .setDuration(200)
                        .start();
                if (toggleListener != null) {
                    toggleListener.onToggle(position, expanded);
                }
            });

            binding.btnEditAction.setOnClickListener(v -> {
                if (actionEditListener != null) {
                    actionEditListener.onActionEdit(item);
                }
            });

            binding.btnDeleteAction.setOnClickListener(v -> {
                if (changedListener != null) {
                    changedListener.onActionItemDeleteRequested(position, item);
                }
            });

            bindGlobalEventButton(binding.btnEventEngineStart, item, ShortcutEventType.ENGINE_START);
            bindGlobalEventButton(binding.btnEventEngineStop, item, ShortcutEventType.ENGINE_STOP);
            bindGlobalEventButton(binding.btnEventShortcutBarShow, item, ShortcutEventType.SHORTCUT_BAR_SHOW);
            bindGlobalEventButton(binding.btnEventShortcutBarHide, item, ShortcutEventType.SHORTCUT_BAR_HIDE);

            binding.btnEditEvents.setOnClickListener(v -> showEventPickerBottomSheet(item));
        }

        private void bindGlobalEventButton(View btn, ActionItem item, ShortcutEventType type) {
            btn.setOnClickListener(v -> {
                if (eventEditListener != null) {
                    eventEditListener.onEventEdit(item, type);
                }
            });
        }

        private List<ShortcutEventType> getAvailableEventTypes(ActionItem item) {
            List<ShortcutEventType> types = new ArrayList<>();
            types.add(ShortcutEventType.REGISTER);
            types.add(ShortcutEventType.UNREGISTER);
            TriggerModel trigger = item.getTriggerModel() != null ? item.getTriggerModel() : TriggerModel.BUTTON;
            switch (trigger) {
                case BUTTON:
                    types.add(ShortcutEventType.BUTTON_PRESS);
                    types.add(ShortcutEventType.BUTTON_RELEASE);
                    break;
                case SWITCH:
                    types.add(ShortcutEventType.KEY_ON);
                    types.add(ShortcutEventType.KEY_OFF);
                    break;
                case PRESSURE_PLATE:
                    types.add(ShortcutEventType.PRESSURE_PLATE_PRESS);
                    types.add(ShortcutEventType.PRESSURE_PLATE_LONG_PRESS);
                    types.add(ShortcutEventType.PRESSURE_PLATE_RELEASE);
                    break;
            }
            types.add(ShortcutEventType.KEY_PRESS);
            types.add(ShortcutEventType.KEY_RELEASE);
            return types;
        }

        private void showEventPickerBottomSheet(ActionItem item) {
            BottomSheetDialog dialog = new BottomSheetDialog(activity);
            float density = activity.getResources().getDisplayMetrics().density;
            int pad = (int) (16 * density);

            LinearLayout container = new LinearLayout(activity);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(pad, pad * 2, pad, pad * 2);

            TextView title = new TextView(activity);
            title.setText(item.getTitle() + " - 选择要编辑的事件");
            title.setTextSize(16);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            container.addView(title, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            for (ShortcutEventType type : getAvailableEventTypes(item)) {
                MaterialButton btn = new MaterialButton(
                        activity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                btn.setText(type.getDisplayName());
                btn.setIconResource(R.drawable.material_symbols_edit_outline_rounded);
                btn.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.topMargin = pad / 2;
                btn.setOnClickListener(v -> {
                    dialog.dismiss();
                    if (eventEditListener != null) {
                        eventEditListener.onEventEdit(item, type);
                    }
                });
                container.addView(btn, lp);
            }

            dialog.setContentView(container);
            dialog.show();
        }

        private void removeWatchers() {
            if (wName != null) binding.etActionName.removeTextChangedListener(wName);
            if (wDescription != null) binding.etActionDescription.removeTextChangedListener(wDescription);
            if (wId != null) binding.etActionId.removeTextChangedListener(wId);
            if (wGroup != null) binding.etActionGroup.removeTextChangedListener(wGroup);
            if (wHoldThreshold != null) binding.etHoldThreshold.removeTextChangedListener(wHoldThreshold);
            if (wReleaseDelay != null) binding.etReleaseDelay.removeTextChangedListener(wReleaseDelay);
        }

        private void updateGroupBadge(ActionItem item) {
            String group = item.getGroup();
            if (group != null && !group.trim().isEmpty()) {
                binding.tvGroupBadge.setText("组: " + group.trim());
                binding.groupBadgeCard.setVisibility(View.VISIBLE);
            } else {
                binding.groupBadgeCard.setVisibility(View.GONE);
            }
        }

        private void updatePressurePlateVisibility(ActionItem item) {
            binding.pressurePlateSettingsContainer.setVisibility(
                    item.getTriggerModel() == TriggerModel.PRESSURE_PLATE ? View.VISIBLE : View.GONE);
        }

        private void notifyChanged(ActionItem item, int position) {
            if (changedListener != null) {
                changedListener.onActionItemChanged(position, item);
            }
        }
    }

    private static abstract class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}