package io.takamiya.mio.floating.toolBoxFragment.tool;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.Locale;

import io.takamiya.mio.databinding.FragmentToolDistanceCalculationBinding;
import io.takamiya.mio.floating.toolBoxFragment.ToolboxBaseFragment;

public class ToolDistanceCalculationFragment extends ToolboxBaseFragment {

    private FragmentToolDistanceCalculationBinding binding;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container) {
        binding = FragmentToolDistanceCalculationBinding.inflate(inflater, container, false);

        binding.btnCalculate.setOnClickListener(v -> calculate());

        return binding.getRoot();
    }

    private void calculate() {
        int x1 = parseInput(binding.etStartX);
        int y1 = parseInput(binding.etStartY);
        int z1 = parseInput(binding.etStartZ);
        int x2 = parseInput(binding.etEndX);
        int y2 = parseInput(binding.etEndY);
        int z2 = parseInput(binding.etEndZ);

        double dx = x1 - x2;
        double dy = y1 - y2;
        double dz = z1 - z2;

        double euclidean = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int manhattan = Math.abs(x1 - x2) + Math.abs(y1 - y2) + Math.abs(z1 - z2);
        int chebyshev = Math.max(Math.abs(x1 - x2),
                Math.max(Math.abs(y1 - y2), Math.abs(z1 - z2)));

        binding.tvEuclidean.setText(String.format(Locale.US, "%.2f", euclidean));
        binding.tvManhattan.setText(String.valueOf(manhattan));
        binding.tvChebyshev.setText(String.valueOf(chebyshev));
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
