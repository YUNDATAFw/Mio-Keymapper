package io.takamiya.mio.floating.toolBoxFragment.tool;

import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.GridLayoutManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import io.takamiya.mio.R;
import io.takamiya.mio.databinding.FragmentToolBeaconColorCalculationBinding;
import io.takamiya.mio.floating.toolBoxFragment.ToolboxBaseFragment;
import io.takamiya.mio.floating.toolBoxFragment.tool.beacon.BeaconColorMixer;
import io.takamiya.mio.floating.toolBoxFragment.tool.beacon.BeaconColorMixerAdapter;
import io.takamiya.mio.floating.toolBoxFragment.tool.beacon.BeaconColorMixerItem;

public class ToolBeaconColorCalculationFragment extends ToolboxBaseFragment {

    private static final java.util.concurrent.ExecutorService CALC_EXECUTOR =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "BeaconCalc");
                t.setDaemon(true);
                return t;
            });

    private FragmentToolBeaconColorCalculationBinding binding;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container) {
        binding = FragmentToolBeaconColorCalculationBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(View view) {
        binding.rvGlassList.setLayoutManager(new GridLayoutManager(getContext(), 1));

        binding.etHexColor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String text = "#" + s.toString().replace("#", "").trim();
                if (isValidHex(text)) {
                    binding.cardInputColorPreview.setCardBackgroundColor(Color.parseColor(text));
                } else {
                    binding.cardInputColorPreview.setCardBackgroundColor(0xFFB0B0B0);
                }
            }
        });

        binding.btnCalculate.setOnClickListener(v -> calculate());
    }

    private void calculate() {
        String hex = binding.etHexColor.getText().toString().trim();
        if (hex.isEmpty()) {
            showToast("请输入目标颜色");
            return;
        }
        if (!isValidHex(hex)) {
            showToast("颜色值无效，请输入6位HEX");
            return;
        }

        binding.btnCalculate.setEnabled(false);
        binding.cardResult.setVisibility(View.GONE);
        binding.rvGlassList.setAdapter(null);

        boolean wikiMode = binding.toggleMode.getCheckedButtonId() == R.id.btn_wiki;

        CALC_EXECUTOR.execute(() -> {
            try {
                BeaconColorMixer.Result result = BeaconColorMixer.calculate(hex, wikiMode, "de2000", 200);

                if (getContext() == null) return;
                getView().post(() -> {
                    if (result == null || result.sequence == null || result.sequence.isEmpty()) {
                        binding.btnCalculate.setEnabled(true);
                        showToast("未找到匹配方案");
                        return;
                    }

                    List<BeaconColorMixerItem> items = new ArrayList<>();
                    for (int i = 0; i < result.sequence.size(); i++) {
                        items.add(new BeaconColorMixerItem(
                                result.sequence.get(i),
                                result.displayNames.get(i),
                                result.glassRgb.get(i)
                        ));
                    }

                    binding.rvGlassList.setAdapter(new BeaconColorMixerAdapter(items));

                    String resultHex = result.resultHex();
                    binding.tvResult.setText(String.format("ΔE = %.4f  %s", result.deltaE, resultHex));
                    binding.cardOutputColorPreview.setCardBackgroundColor(Color.parseColor(resultHex));
                    binding.cardResult.setVisibility(View.VISIBLE);
                    binding.btnCalculate.setEnabled(true);
                });
            } catch (Exception e) {
                if (getContext() != null) {
                    getView().post(() -> {
                        binding.btnCalculate.setEnabled(true);
                        showToast("计算出错: " + e.getMessage());
                    });
                }
            }
        });
    }

    private boolean isValidHex(String str) {
        if (str == null || str.isEmpty()) return false;
        String clean = str.replace("#", "").trim();
        if (clean.length() != 6) return false;
        try {
            Integer.parseInt(clean, 16);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
