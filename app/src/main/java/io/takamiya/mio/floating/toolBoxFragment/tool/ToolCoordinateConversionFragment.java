package io.takamiya.mio.floating.toolBoxFragment.tool;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import io.takamiya.mio.databinding.FragmentToolCoordinateConversionBinding;
import io.takamiya.mio.floating.toolBoxFragment.ToolboxBaseFragment;

public class ToolCoordinateConversionFragment extends ToolboxBaseFragment {

    private FragmentToolCoordinateConversionBinding binding;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container) {
        binding = FragmentToolCoordinateConversionBinding.inflate(inflater, container, false);

        binding.btnToNether.setOnClickListener(v -> convertToNether());
        binding.btnToWorld.setOnClickListener(v -> convertToWorld());

        return binding.getRoot();
    }

    private void convertToNether() {
        int x = parseInput(binding.etWorldX);
        int y = parseInput(binding.etWorldY);
        int z = parseInput(binding.etWorldZ);

        binding.etNetherX.setText(String.valueOf(Math.floorDiv(x, 8)));
        binding.etNetherY.setText(String.valueOf(y));
        binding.etNetherZ.setText(String.valueOf(Math.floorDiv(z, 8)));
    }

    private void convertToWorld() {
        int x = parseInput(binding.etNetherX);
        int y = parseInput(binding.etNetherY);
        int z = parseInput(binding.etNetherZ);

        binding.etWorldX.setText(String.valueOf(x * 8));
        binding.etWorldY.setText(String.valueOf(y));
        binding.etWorldZ.setText(String.valueOf(z * 8));
    }

    private int parseInput(com.google.android.material.textfield.TextInputEditText et) {
        String text = et.getText().toString().trim();
        if (text.isEmpty()) return 0;
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            showToast("输入无效: " + text);
            return 0;
        }
    }

    @Override
    public void onDetach() {
        binding = null;
        super.onDetach();
    }
}
