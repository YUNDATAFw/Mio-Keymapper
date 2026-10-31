package io.takamiya.mio.floating.wss;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.takamiya.mio.databinding.ItemWsChatBinding;
import io.takamiya.mio.databinding.LayoutWsPageChatBinding;
import io.takamiya.mio.ws.WsViewModel;

public class WsChatFragment extends WsBaseFragment {

    private LayoutWsPageChatBinding binding;
    private WsViewModel viewModel;
    private ChatAdapter adapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container) {
        binding = LayoutWsPageChatBinding.inflate(inflater, container, false);

        adapter = new ChatAdapter();
        binding.rvWsChat.setLayoutManager(new LinearLayoutManager(inflater.getContext()));
        binding.rvWsChat.setAdapter(adapter);

        binding.btnWsChatSend.setOnClickListener(v -> sendChat());
        binding.etWsChatMsg.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendChat();
                return true;
            }
            return false;
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

        viewModel.connected().observe(owner, v -> syncEnabled());
        viewModel.chatLines().observe(owner, lines -> {
            if (binding == null || adapter == null) return;
            adapter.updateData(lines);
            int count = adapter.getItemCount();
            if (count > 0) {
                binding.rvWsChat.scrollToPosition(count - 1);
            }
        });
        syncEnabled();
    }

    @Override
    public void onDetach() {
        viewModel = null;
        binding = null;
        adapter = null;
        super.onDetach();
    }

    private void sendChat() {
        if (binding == null || viewModel == null) {
            showToast("服务未就绪");
            return;
        }
        String msg = binding.etWsChatMsg.getText() == null
                ? "" : binding.etWsChatMsg.getText().toString().trim();
        if (msg.isEmpty()) {
            showToast("请输入消息");
            return;
        }
        if (!viewModel.sendChat(msg)) {
            showToast("未连接，无法发送消息");
            return;
        }
        binding.etWsChatMsg.setText("");
    }

    private void syncEnabled() {
        if (binding == null || viewModel == null) return;
        boolean connected = Boolean.TRUE.equals(viewModel.connected().getValue());
        binding.btnWsChatSend.setEnabled(connected);
        binding.etWsChatMsg.setEnabled(connected);
    }

    private static class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

        private final List<WsViewModel.ChatLine> lines = new ArrayList<>();

        static class ViewHolder extends RecyclerView.ViewHolder {
            final ItemWsChatBinding binding;

            ViewHolder(@NonNull ItemWsChatBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(ItemWsChatBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            WsViewModel.ChatLine line = lines.get(position);
            holder.binding.tvChatName.setText(line.name);
            holder.binding.tvChatMessage.setText(line.message);
        }

        @Override
        public int getItemCount() {
            return lines.size();
        }

        void updateData(List<WsViewModel.ChatLine> newLines) {
            final List<WsViewModel.ChatLine> target = (newLines == null)
                    ? new ArrayList<>() : new ArrayList<>(newLines);
            androidx.recyclerview.widget.DiffUtil.DiffResult diff =
                    androidx.recyclerview.widget.DiffUtil.calculateDiff(
                            new androidx.recyclerview.widget.DiffUtil.Callback() {
                                @Override
                                public int getOldListSize() {
                                    return lines.size();
                                }

                                @Override
                                public int getNewListSize() {
                                    return target.size();
                                }

                                @Override
                                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                                    return lines.get(oldItemPosition) == target.get(newItemPosition)
                                            || lines.get(oldItemPosition).equals(target.get(newItemPosition));
                                }

                                @Override
                                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                                    return true;
                                }
                            });
            lines.clear();
            lines.addAll(target);
            diff.dispatchUpdatesTo(this);
        }
    }
}