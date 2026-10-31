package io.takamiya.mio.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.noties.markwon.Markwon;
import io.takamiya.mio.R;
import io.takamiya.mio.databinding.ItemTerminalResultBinding;
import io.takamiya.mio.terminal.command.CommandState;

public class TerminalResultAdapter extends RecyclerView.Adapter<TerminalResultAdapter.ViewHolder> {

    private final List<Item> items = new ArrayList<>();
    private final Markwon markwon;

    public TerminalResultAdapter(Markwon markwon) {
        this.markwon = markwon;
    }

    public void addResult(String commandName, CommandState state, String output, String markdown, String error, long durationMs) {
        items.add(new Item(commandName, state, output, markdown, error, durationMs));
        notifyItemInserted(items.size() - 1);
    }

    public void updateLastState(CommandState state) {
        if (!items.isEmpty()) {
            Item last = items.get(items.size() - 1);
            last.state = state;
            notifyItemChanged(items.size() - 1);
        }
    }

    public void updateLastResult(String output, String markdown, String error, long durationMs) {
        if (!items.isEmpty()) {
            Item last = items.get(items.size() - 1);
            last.output = output;
            last.markdown = markdown;
            last.error = error;
            last.durationMs = durationMs;
            notifyItemChanged(items.size() - 1);
        }
    }

    public void clear() {
        int size = items.size();
        items.clear();
        notifyItemRangeRemoved(0, size);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTerminalResultBinding binding = ItemTerminalResultBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Item item = items.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemTerminalResultBinding binding;

        ViewHolder(ItemTerminalResultBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Item item) {
            binding.tvCommandName.setText(item.commandName);
            binding.tvState.setText(item.state.getLabel());

            int stateColor;
            switch (item.state) {
                case SUCCESS:
                    stateColor = ContextCompat.getColor(itemView.getContext(), R.color.colorTertiary);
                    break;
                case ERROR:
                    stateColor = ContextCompat.getColor(itemView.getContext(), R.color.colorError);
                    break;
                case EXECUTING:
                case PARSING:
                    stateColor = ContextCompat.getColor(itemView.getContext(), R.color.colorPrimary);
                    break;
                default:
                    stateColor = ContextCompat.getColor(itemView.getContext(), R.color.colorOutline);
                    break;
            }
            binding.tvState.setTextColor(stateColor);

            if (item.durationMs > 0) {
                binding.tvDuration.setText(item.durationMs + "ms");
                binding.tvDuration.setVisibility(View.VISIBLE);
            } else {
                binding.tvDuration.setVisibility(View.GONE);
            }

            if (item.error != null) {
                binding.tvOutput.setVisibility(View.VISIBLE);
                binding.tvMarkdown.setVisibility(View.GONE);
                binding.tvOutput.setText(item.error);
                binding.tvOutput.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.colorError));
            } else if (item.markdown != null) {
                binding.tvOutput.setVisibility(View.GONE);
                binding.tvMarkdown.setVisibility(View.VISIBLE);
                markwon.setMarkdown(binding.tvMarkdown, item.markdown);
            } else if (item.output != null) {
                binding.tvOutput.setVisibility(View.VISIBLE);
                binding.tvMarkdown.setVisibility(View.GONE);
                binding.tvOutput.setText(item.output);
                binding.tvOutput.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.colorOnSurfaceVariant));
            } else {
                binding.tvOutput.setVisibility(View.GONE);
                binding.tvMarkdown.setVisibility(View.GONE);
            }
        }
    }

    static class Item {
        String commandName;
        CommandState state;
        String output;
        String markdown;
        String error;
        long durationMs;

        Item(String commandName, CommandState state, String output, String markdown, String error, long durationMs) {
            this.commandName = commandName;
            this.state = state;
            this.output = output;
            this.markdown = markdown;
            this.error = error;
            this.durationMs = durationMs;
        }
    }
}
