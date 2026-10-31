package io.takamiya.mio.floating.toolBoxFragment.tool;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.Locale;

import io.takamiya.mio.databinding.FragmentToolTimeConversionBinding;
import io.takamiya.mio.floating.toolBoxFragment.ToolboxBaseFragment;

public class ToolTimeConversionFragment extends ToolboxBaseFragment {

    private FragmentToolTimeConversionBinding binding;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container) {
        binding = FragmentToolTimeConversionBinding.inflate(inflater, container, false);

        binding.btnSeconds.setOnClickListener(v -> convertFromSeconds());
        binding.btnGameDays.setOnClickListener(v -> convertFromGameDays());
        binding.btnGameTicks.setOnClickListener(v -> convertFromGameTicks());
        binding.btnRedstoneTicks.setOnClickListener(v -> convertFromRedstoneTicks());
        binding.btnRepeater.setOnClickListener(v -> convertFromRepeater());
        binding.btnHopper.setOnClickListener(v -> convertFromHopper());

        return binding.getRoot();
    }

    private void convertFromSeconds() {
        float seconds = parseInput(binding.etSeconds);
        setAll(seconds);
    }

    private void convertFromGameDays() {
        float days = parseInput(binding.etGameDays);
        float seconds = days * 300f;
        setAll(seconds);
    }

    private void convertFromGameTicks() {
        float ticks = parseInput(binding.etGameTicks);
        float seconds = ticks / 20f;
        setAll(seconds);
    }

    private void convertFromRedstoneTicks() {
        float rt = parseInput(binding.etRedstoneTicks);
        float seconds = rt / 10f;
        setAll(seconds);
    }

    private void convertFromRepeater() {
        float repeater = parseInput(binding.etRepeater);
        float seconds = repeater * 0.1f;
        setAll(seconds);
    }

    private void convertFromHopper() {
        float hopper = parseInput(binding.etHopper);
        float seconds = hopper * 0.4f;
        setAll(seconds);
    }

    private void setAll(float seconds) {
        binding.etSeconds.setText(format(seconds));
        binding.etGameDays.setText(format(seconds / 300f));
        binding.etGameTicks.setText(format(seconds * 20f));
        binding.etRedstoneTicks.setText(format(seconds * 10f));
        binding.etRepeater.setText(format(seconds / 0.1f));
        binding.etHopper.setText(format(seconds / 0.4f));
    }

    private float parseInput(com.google.android.material.textfield.TextInputEditText et) {
        String text = et.getText().toString().trim();
        if (text.isEmpty()) return 0f;
        try {
            return Float.parseFloat(text);
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    private String format(float value) {
        if (Float.isInfinite(value) || Float.isNaN(value)) return "∞";
        if (value == (long) value) return String.format(Locale.US, "%d", (long) value);
        if (Math.abs(value) >= 1000) return String.format(Locale.US, "%.1f", value);
        if (Math.abs(value) >= 1) return String.format(Locale.US, "%.2f", value);
        return String.format(Locale.US, "%.4f", value);
    }

    @Override
    public void onDetach() {
        binding = null;
        super.onDetach();
    }
}
