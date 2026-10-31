package io.takamiya.mio.floating.toolBoxFragment.tool;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import io.takamiya.mio.databinding.FragmentToolUpgradeExperienceCalculationBinding;
import io.takamiya.mio.floating.toolBoxFragment.ToolboxBaseFragment;

public class ToolUpgradeExperienceCalculationFragment extends ToolboxBaseFragment {

    private FragmentToolUpgradeExperienceCalculationBinding binding;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container) {
        binding = FragmentToolUpgradeExperienceCalculationBinding.inflate(inflater, container, false);

        binding.btnCalculate.setOnClickListener(v -> calculate());

        return binding.getRoot();
    }

    private void calculate() {
        int current = parseInput(binding.etCurrentLevel);
        int target = parseInput(binding.etTargetLevel);

        if (target <= current) {
            binding.tvExpNeeded.setText("目标需大于初始");
            binding.tvSingleLevel.setText("--");
            showToast("目标等级需大于初始等级");
            return;
        }

        int totalNeeded = getTotalExpToLevel(target) - getTotalExpToLevel(current);
        int singleLevel = getExpToNextLevel(current);

        binding.tvExpNeeded.setText(String.valueOf(totalNeeded));
        binding.tvSingleLevel.setText(String.valueOf(singleLevel));
    }

    private int getTotalExpToLevel(int level) {
        if (level <= 0) return 0;
        if (level <= 16) return level * level + 6 * level;
        if (level <= 31) return (int) (2.5 * level * level - 40.5 * level + 360);
        return (int) (4.5 * level * level - 162.5 * level + 2220);
    }

    private int getExpToNextLevel(int currentLevel) {
        if (currentLevel < 0) return 0;
        if (currentLevel >= 30) return 9 * currentLevel - 158;
        if (currentLevel >= 15) return 5 * currentLevel - 38;
        return 2 * currentLevel + 7;
    }

    private int parseInput(com.google.android.material.textfield.TextInputEditText et) {
        String text = et.getText().toString().trim();
        if (text.isEmpty()) return 0;
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public void onDetach() {
        binding = null;
        super.onDetach();
    }
}
