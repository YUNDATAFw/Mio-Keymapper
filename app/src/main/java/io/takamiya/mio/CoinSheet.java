package io.takamiya.mio;

import android.content.Context;
import android.view.LayoutInflater;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import io.takamiya.mio.databinding.DialogCoinSheetBinding;

/**
 * 投币数量选择 BottomSheet（Material3）
 */
public class CoinSheet {

    public interface OnCoinListener {
        void onCoinSelected(int count);
    }

    private final Context context;
    private final BottomSheetDialog dialog;
    private final DialogCoinSheetBinding binding;

    private OnCoinListener coinListener;

    public CoinSheet(Context context) {
        this.context = context;
        binding = DialogCoinSheetBinding.inflate(LayoutInflater.from(context));
        dialog = new BottomSheetDialog(context);
        dialog.setContentView(binding.getRoot());

        binding.btnCoinClose.setOnClickListener(v -> dismiss());
        binding.btnCoinConfirm.setOnClickListener(v -> {
            int count = binding.coinCountGroup.getCheckedButtonId() == R.id.btnCoinTwo ? 2 : 1;
            dialog.dismiss();
            if (coinListener != null) {
                coinListener.onCoinSelected(count);
            }
        });
    }

    public Context getContext() {
        return context;
    }

    public void setOnCoinListener(OnCoinListener listener) {
        this.coinListener = listener;
    }

    public void show(int remaining) {
        binding.coinCountGroup.check(R.id.btnCoinOne);
        binding.btnCoinTwo.setEnabled(remaining >= 2);
        dialog.show();
    }

    public void dismiss() {
        dialog.dismiss();
    }

    public boolean isShowing() {
        return dialog.isShowing();
    }
}