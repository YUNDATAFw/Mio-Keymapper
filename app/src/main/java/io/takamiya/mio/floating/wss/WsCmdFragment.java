package io.takamiya.mio.floating.wss;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;

import io.takamiya.mio.databinding.LayoutWsPageCmdBinding;
import io.takamiya.mio.ws.WsViewModel;

public class WsCmdFragment extends WsBaseFragment {

    private LayoutWsPageCmdBinding binding;
    private WsViewModel viewModel;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container) {
        binding = LayoutWsPageCmdBinding.inflate(inflater, container, false);

        binding.btnWsSend.setOnClickListener(v -> sendCommand());
        binding.btnCopyOutput.setOnClickListener(v ->
                copyToClipboard(binding.tvWsCmdOutput.getText().toString()));

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(View view) {
        LifecycleOwner owner = getLifecycleOwner();
        viewModel = (owner == null)
                ? null
                : new ViewModelProvider((androidx.lifecycle.ViewModelStoreOwner) owner)
                        .get(WsViewModel.class);
        if (viewModel == null) return;

        viewModel.connected().observe(owner, v -> syncEnabled());
        viewModel.commandResponse().observe(owner, result -> {
            if (result == null || binding == null) return;
            if (result.message == null || result.message.trim().isEmpty()) {
                binding.tvWsCmdOutput.setText("(无输出)");
            } else {
                binding.tvWsCmdOutput.setText(result.message.trim());
            }
        });
        syncEnabled();
    }

    @Override
    public void onDetach() {
        viewModel = null;
        binding = null;
        super.onDetach();
    }

    private void sendCommand() {
        if (binding == null || viewModel == null) {
            showToast("服务未就绪");
            return;
        }
        String cmd = binding.etWsCmd.getText() == null
                ? "" : binding.etWsCmd.getText().toString().trim();
        if (cmd.isEmpty()) {
            showToast("请输入命令");
            return;
        }
        if (cmd.startsWith("!")) {
            handleLocalCommand(cmd);
            return;
        }
        if (viewModel.sendCommand(cmd) == null) {
            showToast("未连接，无法发送命令");
            return;
        }
        binding.etWsCmd.setText("");
    }

    /** 本地命令（! 开头），不发给游戏服。 */
    private void handleLocalCommand(String cmd) {
        showToast("未知本地命令: " + cmd);
    }

    private void syncEnabled() {
        if (binding == null || viewModel == null) return;
        boolean connected = Boolean.TRUE.equals(viewModel.connected().getValue());
        binding.btnWsSend.setEnabled(connected);
        binding.etWsCmd.setEnabled(connected);
    }

    private void copyToClipboard(String text) {
        Context context = getContext();
        if (context == null || text == null || text.isEmpty() || "-".equals(text)) return;
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("WsCmdOutput", text));
        showToast("已复制到剪贴板");
    }
}