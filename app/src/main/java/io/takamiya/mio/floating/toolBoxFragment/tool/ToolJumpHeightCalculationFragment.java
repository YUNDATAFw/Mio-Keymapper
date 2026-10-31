package io.takamiya.mio.floating.toolBoxFragment.tool;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.slider.Slider;

import io.takamiya.mio.databinding.FragmentToolJumpHeightCalculationBinding;
import io.takamiya.mio.floating.toolBoxFragment.ToolboxBaseFragment;

public class ToolJumpHeightCalculationFragment extends ToolboxBaseFragment {

    private FragmentToolJumpHeightCalculationBinding binding;
    private int level = 0;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container) {
        binding = FragmentToolJumpHeightCalculationBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(View view) {
        binding.sliderLevel.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                level = (int) value;
                binding.etLevel.setText(String.valueOf(level));
                calculate();
            }
        });

        binding.etLevel.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString().trim();
                if (text.isEmpty()) return;
                try {
                    int val = Integer.parseInt(text);
                    if (val >= 0 && val <= 255) {
                        level = val;
                        binding.sliderLevel.setValue(val);
                        calculate();
                    }
                } catch (NumberFormatException ignored) {}
            }
        });

        calculate();
    }

    private void calculate() {
        double velocity = initialVelocity(level);
        double height = jumpHeight(level);
        double approx = approximateHeight(level);
        int ticks = jumpTicks(level);
        int fallReduce = Math.max(0, level);

        binding.tvLevel.setText(String.valueOf(level));
        binding.tvHeightDisplay.setText(String.format("%.4f", height));
        binding.tvExact.setText(String.format("%.4f 格", height));
        binding.tvApprox.setText(String.format("%.4f 格", approx));
        binding.tvVelocity.setText(String.format("%.4f 格/tick", velocity));
        binding.tvTicks.setText(String.format("%d tick", ticks));
        binding.tvFallReduce.setText(String.format("%d 格", fallReduce));
    }

    private static final double BASE_VELOCITY = 0.42;
    private static final double GRAVITY = 0.08;
    private static final double DRAG = 0.98;
    private static final double BASE_HEIGHT = 1.2522;

    private double initialVelocity(int level) {
        return BASE_VELOCITY + 0.1 * clamp(level);
    }

    private double jumpHeight(int level) {
        double v = initialVelocity(level);
        double h = 0;
        while (v > 0) {
            h += v;
            v = (v - GRAVITY) * DRAG;
        }
        return round4(h);
    }

    private double approximateHeight(int level) {
        return round4(BASE_HEIGHT * (1 + 0.5 * clamp(level)));
    }

    private int jumpTicks(int level) {
        double v = initialVelocity(level);
        int t = 0;
        while (v > 0) {
            t++;
            v = (v - GRAVITY) * DRAG;
        }
        return t;
    }

    private int clamp(int level) {
        return Math.min(255, Math.max(0, level));
    }

    private double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
