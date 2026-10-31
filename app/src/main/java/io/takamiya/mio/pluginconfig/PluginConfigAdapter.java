package io.takamiya.mio.pluginconfig;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;

import java.util.List;

import io.takamiya.mio.R;
import io.takamiya.mio.databinding.ItemPluginCfgButtonBinding;
import io.takamiya.mio.databinding.ItemPluginCfgCheckboxBinding;
import io.takamiya.mio.databinding.ItemPluginCfgChipBinding;
import io.takamiya.mio.databinding.ItemPluginCfgProgressBinding;
import io.takamiya.mio.databinding.ItemPluginCfgRadioBinding;
import io.takamiya.mio.databinding.ItemPluginCfgSwitchBinding;
import io.takamiya.mio.databinding.ItemPluginCfgTextBinding;
import io.takamiya.mio.databinding.ItemPluginCfgTextviewBinding;

public class PluginConfigAdapter extends RecyclerView.Adapter<PluginConfigAdapter.VH> {

    private static final int[] TYPE_ICON = {
            R.drawable.material_symbols_sync_rounded,
            R.drawable.material_symbols_edit_outline_rounded,
            R.drawable.material_symbols_history_rounded,
            R.drawable.material_symbols_check_circle_unread_outline_rounded,
            R.drawable.material_symbols_description_outline_rounded,
            R.drawable.material_symbols_rocket_launch_outline_rounded,
            R.drawable.material_symbols_filter_list_rounded,
            R.drawable.material_symbols_check_circle_outline_rounded
    };

    private static final int[] TYPE_CHIP_BG = {
            R.color.colorSecondaryContainer,
            R.color.colorPrimaryContainer,
            R.color.colorTertiaryContainer,
            R.color.colorPrimaryContainer,
            R.color.colorSurfaceVariant,
            R.color.colorTertiaryContainer,
            R.color.colorSecondaryContainer,
            R.color.colorPrimaryContainer
    };

    private static final int[] TYPE_CHIP_FG = {
            R.color.colorOnSecondaryContainer,
            R.color.colorOnPrimaryContainer,
            R.color.colorOnTertiaryContainer,
            R.color.colorOnPrimaryContainer,
            R.color.colorOnSurfaceVariant,
            R.color.colorOnTertiaryContainer,
            R.color.colorOnSecondaryContainer,
            R.color.colorOnPrimaryContainer
    };

    private List<PluginConfigScheme.Item> items = java.util.Collections.emptyList();

    /** 按钮点击回调(key, title)。由宿主注入,用于调用插件的 onButton(key)。 */
    public interface OnButtonAction {
        void onButton(String key, String title);
    }

    private OnButtonAction buttonAction;

    public void setOnButtonAction(@Nullable OnButtonAction action) {
        this.buttonAction = action;
    }

    public void submit(List<PluginConfigScheme.Item> list) {
        items = list == null ? java.util.Collections.emptyList() : list;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type.ordinal();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case 0:
                return new VH(ItemPluginCfgSwitchBinding.inflate(inflater, parent, false));
            case 1:
                return new VH(ItemPluginCfgTextBinding.inflate(inflater, parent, false));
            case 2:
                return new VH(ItemPluginCfgProgressBinding.inflate(inflater, parent, false));
            case 3:
                return new VH(ItemPluginCfgRadioBinding.inflate(inflater, parent, false));
            case 4:
                return new VH(ItemPluginCfgTextviewBinding.inflate(inflater, parent, false));
            case 5:
                return new VH(ItemPluginCfgButtonBinding.inflate(inflater, parent, false));
            case 6:
                return new VH(ItemPluginCfgChipBinding.inflate(inflater, parent, false));
            default:
                return new VH(ItemPluginCfgCheckboxBinding.inflate(inflater, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        PluginConfigScheme.Item item = items.get(position);
        holder.currentItem = item;
        switch (item.type) {
            case SWITCH:
                bindCommon(holder, holder.switchBinding.header.ivCfgIconCard,
                        holder.switchBinding.header.ivCfgIcon, item);
                bindSwitch(holder, item);
                break;
            case TEXT_FIELD:
                bindCommon(holder, holder.textBinding.header.ivCfgIconCard,
                        holder.textBinding.header.ivCfgIcon, item);
                bindText(holder, item);
                break;
            case PROGRESS:
                bindCommon(holder, holder.progressBinding.header.ivCfgIconCard,
                        holder.progressBinding.header.ivCfgIcon, item);
                bindProgress(holder, item);
                break;
            case RADIO:
                bindCommon(holder, holder.radioBinding.header.ivCfgIconCard,
                        holder.radioBinding.header.ivCfgIcon, item);
                bindRadio(holder, item);
                break;
            case TEXT_VIEW:
                bindCommon(holder, holder.textviewBinding.header.ivCfgIconCard,
                        holder.textviewBinding.header.ivCfgIcon, item);
                bindTextview(holder, item);
                break;
            case BUTTON:
                bindCommon(holder, holder.buttonBinding.header.ivCfgIconCard,
                        holder.buttonBinding.header.ivCfgIcon, item);
                bindButton(holder, item);
                break;
            case CHIP:
                bindCommon(holder, holder.chipBinding.header.ivCfgIconCard,
                        holder.chipBinding.header.ivCfgIcon, item);
                bindChip(holder, item);
                break;
            default:
                bindCommon(holder, holder.checkboxBinding.header.ivCfgIconCard,
                        holder.checkboxBinding.header.ivCfgIcon, item);
                bindCheckbox(holder, item);
                break;
        }
    }

    private void bindCommon(VH h, MaterialCardView card, ShapeableImageView icon, PluginConfigScheme.Item item) {
        int ordinal = item.type.ordinal();
        card.setCardBackgroundColor(h.context.getColor(TYPE_CHIP_BG[ordinal]));
        icon.setImageResource(TYPE_ICON[ordinal]);
        icon.setImageTintList(ColorStateList.valueOf(h.context.getColor(TYPE_CHIP_FG[ordinal])));
    }

    private void bindTexts(MaterialTextView title, MaterialTextView subtitle, PluginConfigScheme.Item item) {
        title.setText(item.title);
        if (item.subtitle == null || item.subtitle.isEmpty()) {
            subtitle.setVisibility(View.GONE);
        } else {
            subtitle.setVisibility(View.VISIBLE);
            subtitle.setText(item.subtitle);
        }
    }

    private void bindSwitch(VH h, PluginConfigScheme.Item item) {
        var b = h.switchBinding;
        b.swCfgValue.setOnCheckedChangeListener(null);
        b.swCfgValue.setChecked(item.boolValue);
        b.swCfgValue.setOnCheckedChangeListener((v, checked) -> item.boolValue = checked);
        bindTexts(b.header.tvCfgTitle, b.header.tvCfgSubtitle, item);
    }

    private void bindCheckbox(VH h, PluginConfigScheme.Item item) {
        var b = h.checkboxBinding;
        b.cbCfgValue.setOnCheckedChangeListener(null);
        b.cbCfgValue.setChecked(item.boolValue);
        b.cbCfgValue.setOnCheckedChangeListener((v, checked) -> item.boolValue = checked);
        bindTexts(b.header.tvCfgTitle, b.header.tvCfgSubtitle, item);
    }

    private void bindText(VH h, PluginConfigScheme.Item item) {
        var b = h.textBinding;
        TextInputLayout til = b.tilCfgText;
        TextInputEditText et = b.etCfgText;
        til.setHint(item.hint == null || item.hint.isEmpty() ? "请输入" : item.hint);
        String inputType = item.inputType == null ? "" : item.inputType;
        switch (inputType) {
            case "int":
                et.setInputType(InputType.TYPE_CLASS_NUMBER);
                et.setFilters(new InputFilter[]{new InputFilter.LengthFilter(12)});
                break;
            case "decimal":
                et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                et.setFilters(new InputFilter[]{new DecimalFilter()});
                break;
            case "digits":
                et.setInputType(InputType.TYPE_CLASS_NUMBER);
                break;
            case "english":
                et.setInputType(InputType.TYPE_CLASS_TEXT);
                et.setFilters(new InputFilter[]{new EnglishFilter()});
                break;
            default:
                et.setInputType(InputType.TYPE_CLASS_TEXT);
                et.setFilters(new InputFilter[0]);
                break;
        }
        et.setText(item.textValue);
        bindTexts(b.header.tvCfgTitle, b.header.tvCfgSubtitle, item);
    }

    private void bindProgress(VH h, PluginConfigScheme.Item item) {
        var b = h.progressBinding;
        CircularProgressIndicator pci = b.pciCfgProgress;
        pci.setIndeterminate(item.indeterminate);
        if (!item.indeterminate) {
            int pct = item.progressMax > 0
                    ? Math.round(item.progress * 100f / item.progressMax)
                    : item.progress;
            pci.setMax(100);
            pci.setProgressCompat(pct, false);
        }
        b.tvCfgProgressText.setText(item.progressText);
        bindTexts(b.header.tvCfgTitle, b.header.tvCfgSubtitle, item);
    }

    private void bindRadio(VH h, PluginConfigScheme.Item item) {
        var b = h.radioBinding;
        RadioGroup rg = b.rgCfgValue;
        rg.setOnCheckedChangeListener(null);
        rg.removeAllViews();
        int pad = Math.round(h.context.getResources().getDisplayMetrics().density * 8);
        for (int i = 0; i < item.options.size(); i++) {
            MaterialRadioButton rb = new MaterialRadioButton(h.context);
            rb.setId(View.generateViewId());
            rb.setText(item.options.get(i).label);
            rb.setPadding(pad, 0, 0, 0);
            rg.addView(rb, new RadioGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        int checked = -1;
        for (int i = 0; i < item.options.size(); i++) {
            if (item.options.get(i).value != null && item.options.get(i).value.equals(item.radioValue)) {
                checked = i;
                break;
            }
        }
        if (checked >= 0) {
            rg.check(rg.getChildAt(checked).getId());
        }
        rg.setOnCheckedChangeListener((group, checkedId) -> {
            int idx = -1;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (group.getChildAt(i).getId() == checkedId) {
                    idx = i;
                    break;
                }
            }
            if (idx >= 0 && idx < item.options.size()) {
                item.radioValue = item.options.get(idx).value;
            }
        });
        bindTexts(b.header.tvCfgTitle, b.header.tvCfgSubtitle, item);
    }

    private void bindTextview(VH h, PluginConfigScheme.Item item) {
        var b = h.textviewBinding;
        b.tvCfgBody.setText(item.bodyText);
        bindTexts(b.header.tvCfgTitle, b.header.tvCfgSubtitle, item);
    }

    private void bindButton(VH h, PluginConfigScheme.Item item) {
        var b = h.buttonBinding;
        String label = item.buttonLabel == null || item.buttonLabel.isEmpty() ? item.title : item.buttonLabel;
        b.btnCfgAction.setText(label);
        String key = item.key == null ? "" : item.key;
        String title = item.title == null ? "" : item.title;
        b.btnCfgAction.setOnClickListener(v -> {
            if (buttonAction != null) {
                buttonAction.onButton(key, title);
            } else if (item.action != null) {
                item.action.run();
            } else {
                Toast.makeText(h.context, "已触发动作: " + item.title + "(示例)", Toast.LENGTH_SHORT).show();
            }
        });
        bindTexts(b.header.tvCfgTitle, b.header.tvCfgSubtitle, item);
    }

    private void bindChip(VH h, PluginConfigScheme.Item item) {
        var b = h.chipBinding;
        ChipGroup cg = b.cgCfgValue;
        cg.removeAllViews();
        for (PluginConfigScheme.Option option : item.options) {
            Chip chip = (Chip) LayoutInflater.from(h.context)
                    .inflate(R.layout.item_plugin_cfg_chip_option, cg, false);
            chip.setCheckable(true);
            chip.setText(option.label);
            chip.setChecked(item.chipValues.contains(option.label));
            chip.setOnCheckedChangeListener((c, checked) -> {
                if (checked) {
                    item.chipValues.add(option.label);
                } else {
                    item.chipValues.remove(option.label);
                }
            });
            cg.addView(chip);
        }
        bindTexts(b.header.tvCfgTitle, b.header.tvCfgSubtitle, item);
    }

    /** 输入过滤:decimal 只允许数字与小数点。 */
    private static final class DecimalFilter implements InputFilter {
        @Override
        public CharSequence filter(CharSequence source, int start, int end,
                                   android.text.Spanned dest, int dstart, int dend) {
            boolean hasDot = dest.toString().contains(".");
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < end; i++) {
                char c = source.charAt(i);
                if (Character.isDigit(c)) {
                    sb.append(c);
                } else if (c == '.' && !hasDot) {
                    sb.append(c);
                    hasDot = true;
                }
            }
            return sb.length() == end - start ? null : sb.toString();
        }
    }

    /** 输入过滤:english 只允许 A-Z a-z。 */
    private static final class EnglishFilter implements InputFilter {
        @Override
        public CharSequence filter(CharSequence source, int start, int end,
                                   android.text.Spanned dest, int dstart, int dend) {
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < end; i++) {
                char c = source.charAt(i);
                if (Character.isLetter(c)) {
                    sb.append(c);
                }
            }
            return sb.length() == end - start ? null : sb.toString();
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        final Context context;
        PluginConfigScheme.Item currentItem;
        ItemPluginCfgSwitchBinding switchBinding;
        ItemPluginCfgTextBinding textBinding;
        ItemPluginCfgProgressBinding progressBinding;
        ItemPluginCfgRadioBinding radioBinding;
        ItemPluginCfgTextviewBinding textviewBinding;
        ItemPluginCfgButtonBinding buttonBinding;
        ItemPluginCfgChipBinding chipBinding;
        ItemPluginCfgCheckboxBinding checkboxBinding;

        VH(@NonNull View itemView) {
            super(itemView);
            context = itemView.getContext();
        }

        VH(ItemPluginCfgSwitchBinding b) {
            this(b.getRoot());
            switchBinding = b;
        }

        VH(ItemPluginCfgTextBinding b) {
            this(b.getRoot());
            textBinding = b;
            b.etCfgText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (currentItem != null) {
                        currentItem.textValue = s.toString();
                    }
                }
            });
        }

        VH(ItemPluginCfgProgressBinding b) {
            this(b.getRoot());
            progressBinding = b;
        }

        VH(ItemPluginCfgRadioBinding b) {
            this(b.getRoot());
            radioBinding = b;
        }

        VH(ItemPluginCfgTextviewBinding b) {
            this(b.getRoot());
            textviewBinding = b;
        }

        VH(ItemPluginCfgButtonBinding b) {
            this(b.getRoot());
            buttonBinding = b;
        }

        VH(ItemPluginCfgChipBinding b) {
            this(b.getRoot());
            chipBinding = b;
        }

        VH(ItemPluginCfgCheckboxBinding b) {
            this(b.getRoot());
            checkboxBinding = b;
        }
    }
}