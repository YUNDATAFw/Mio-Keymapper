package io.takamiya.mio.floating.toolBoxFragment.tool;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.slider.Slider;

import io.takamiya.mio.databinding.FragmentToolItemStatisticsBinding;
import io.takamiya.mio.floating.toolBoxFragment.ToolboxBaseFragment;

public class ToolItemStatisticsFragment extends ToolboxBaseFragment {

    private FragmentToolItemStatisticsBinding binding;
    private int stackSize = 64;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container) {
        binding = FragmentToolItemStatisticsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(View view) {
        binding.sliderStackSize.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                stackSize = (int) value;
                binding.tvStackSize.setText(String.valueOf(stackSize));
                calculate();
            }
        });

        binding.etItemCount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                calculate();
            }
        });
    }

    private void calculate() {
        String text = binding.etItemCount.getText().toString().trim();
        if (text.isEmpty()) {
            binding.tvItemCountDisplay.setText("0");
            binding.tvStacks.setText("--");
            binding.tvChest.setText("--");
            binding.tvDoubleChest.setText("--");
            binding.tvDispenser.setText("--");
            binding.tvHopper.setText("--");
            binding.tvInventory.setText("--");
            binding.tvShulkerBox.setText("--");
            return;
        }

        long items;
        try {
            items = Long.parseLong(text);
        } catch (NumberFormatException e) {
            return;
        }
        if (items < 0) items = 0;

        binding.tvItemCountDisplay.setText(String.valueOf(items));

        long stacks = items / stackSize;
        long remainder = items % stackSize;
        double exactStacks = items / (double) stackSize;

        binding.tvStacks.setText(String.format("%d 组 + %d 个（%.1f 组）", stacks, remainder, exactStacks));
        binding.tvChest.setText(formatContainer(items, 27, "箱"));
        binding.tvDoubleChest.setText(formatContainer(items, 54, "大箱"));
        binding.tvDispenser.setText(formatContainer(items, 9, "台"));
        binding.tvHopper.setText(formatContainer(items, 5, "斗"));
        binding.tvInventory.setText(formatContainer(items, 36, "栏"));

        // 装满潜影盒的箱子: 27 潜影盒 × 27 格 = 729 格
        long totalSlots = calcSlots(items);
        long shulkerBoxes = calcSlots(items, 27);
        long shulkerChests = calcSlots(items, 27 * 27);
        long shulkerRemainder = shulkerBoxes % 27;
        if (items == 0) {
            binding.tvShulkerBox.setText("--");
        } else if (shulkerChests == 0) {
            binding.tvShulkerBox.setText(String.format("%d 盒 + %d 组 + %d 个",
                    shulkerBoxes, remainder > 0 ? 1 : 0, remainder));
        } else {
            binding.tvShulkerBox.setText(String.format("%d 箱盒 + %d 盒 + %d 组 + %d 个",
                    shulkerChests, shulkerRemainder, remainder > 0 ? 1 : 0, remainder));
        }
    }

    private long calcSlots(long items) {
        return (items + stackSize - 1) / stackSize;
    }

    private long calcSlots(long items, int containerSlots) {
        long totalStacks = calcSlots(items);
        return (totalStacks + containerSlots - 1) / containerSlots;
    }

    private String formatContainer(long items, int slots, String unit) {
        if (items == 0) return "--";
        long count = calcSlots(items, slots);
        long totalStacks = calcSlots(items);
        long usedSlots = totalStacks;
        long totalCapacitySlots = count * slots;
        double utilization = totalCapacitySlots > 0 ? Math.round((usedSlots * 100.0 / totalCapacitySlots) * 10.0) / 10.0 : 0;
        long fullStacks = items / stackSize;
        long rem = items % stackSize;
        return String.format("%d %s（%.1f%%）  %d 组 + %d 个", count, unit, utilization, fullStacks, rem);
    }
}
