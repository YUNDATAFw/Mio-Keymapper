package io.takamiya.mio.floating.wss;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;

import io.takamiya.mio.R;
import io.takamiya.mio.databinding.LayoutWsPageLinkBinding;
import io.takamiya.mio.ws.WsViewModel;

public class WsLinkFragment extends WsBaseFragment {

    private LayoutWsPageLinkBinding binding;
    private WsViewModel viewModel;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container) {
        binding = LayoutWsPageLinkBinding.inflate(inflater, container, false);

        binding.btnWsToggle.setOnClickListener(v -> toggleService());
        binding.btnCopyCommand.setOnClickListener(v ->
                copyToClipboard(binding.tvConnectCommand.getText().toString()));
        binding.btnCopyInfo.setOnClickListener(v -> {
            String info = "主机: " + binding.tvRemoteAddr.getText()
                    + "\n端口: " + binding.tvRemotePort.getText();
            copyToClipboard(info);
        });

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

        viewModel.running().observe(owner, v -> syncState());
        viewModel.connected().observe(owner, v -> syncState());
        viewModel.remoteAddress().observe(owner, v -> syncState());
        viewModel.remotePort().observe(owner, v -> syncState());
        viewModel.port().observe(owner, v -> syncState());
        viewModel.playerNames().observe(owner, v -> syncState());
        syncState();
    }

    @Override
    public void onDetach() {
        viewModel = null;
        binding = null;
        super.onDetach();
    }

    private void toggleService() {
        if (viewModel == null) return;
        if (viewModel.isRunning()) {
            viewModel.stop();
            return;
        }
        int port = parsePortInput();
        if (port <= 0 || port > 65535) {
            showToast("端口无效");
            return;
        }
        viewModel.start(port);
    }

    private int parsePortInput() {
        if (binding == null || binding.etWsPort.getText() == null) return -1;
        try {
            return Integer.parseInt(binding.etWsPort.getText().toString().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void syncState() {
        if (binding == null || viewModel == null) return;
        boolean running = Boolean.TRUE.equals(viewModel.running().getValue());
        boolean connected = Boolean.TRUE.equals(viewModel.connected().getValue());

        if (running) {
            binding.etWsPort.setEnabled(false);
            if (connected) {
                binding.tvWsStatus.setText("已连接");
                binding.tvWsStatus.setTextColor(getColorCompat(R.color.colorWsConnected));
            } else {
                binding.tvWsStatus.setText("监听中");
                binding.tvWsStatus.setTextColor(getColorCompat(R.color.colorPrimary));
            }
            binding.btnWsToggle.setIconResource(R.drawable.material_symbols_pause_rounded);
            binding.btnWsToggle.setText("停止服务");
        } else {
            binding.etWsPort.setEnabled(true);
            if (binding.etWsPort.getText() == null
                    || binding.etWsPort.getText().toString().trim().isEmpty()) {
                binding.etWsPort.setText(String.valueOf(viewModel.getPort()));
            }
            binding.tvWsStatus.setText("未运行");
            binding.tvWsStatus.setTextColor(getColorCompat(R.color.colorSecondary));
            binding.btnWsToggle.setIconResource(R.drawable.material_symbols_play_arrow_rounded);
            binding.btnWsToggle.setText("启动服务");
        }

        binding.tvConnectCommand.setText(viewModel.getConnectCommand());
        binding.tvRemoteAddr.setText(connected ? viewModel.getRemoteAddress() : "-");
        binding.tvRemotePort.setText(connected ? String.valueOf(viewModel.getRemotePort()) : "-");
        String players = viewModel.getPlayerNames();
        binding.tvRemotePlayer.setText(connected && players != null ? players : "-");
    }

    private int getColorCompat(int resId) {
        Context context = getContext();
        if (context == null) return android.graphics.Color.GRAY;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            return context.getColor(resId);
        }
        return context.getResources().getColor(resId);
    }

    private void copyToClipboard(String text) {
        Context context = getContext();
        if (context == null || text == null || text.isEmpty()) return;
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("WsConnectInfo", text));
        showToast("已复制到剪贴板");
    }
}