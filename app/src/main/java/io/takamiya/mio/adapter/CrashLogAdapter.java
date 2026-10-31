package io.takamiya.mio.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.takamiya.mio.R;
import io.takamiya.mio.bean.CrashLogEntry;

public class CrashLogAdapter extends RecyclerView.Adapter<CrashLogAdapter.ViewHolder> {

    private List<CrashLogEntry> logs = new ArrayList<>();

    public interface OnItemActionListener {
        void onDelete(int position);
        void onShare(int position);
        void onViewDetail(int position);
    }

    private OnItemActionListener listener;

    public void setOnItemActionListener(OnItemActionListener listener) {
        this.listener = listener;
    }

    public void setData(List<CrashLogEntry> logs) {
        this.logs = logs != null ? logs : new ArrayList<>();
        notifyDataSetChanged();
    }

    public List<CrashLogEntry> getData() {
        return logs;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_crash_log, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(logs.get(position), position);
    }

    @Override
    public int getItemCount() {
        return logs.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final View cardRoot;
        private final TextView tvCrashTime;
        private final TextView tvMessage;
        private final View btnDelete;
        private final View btnShare;

        ViewHolder(View itemView) {
            super(itemView);
            cardRoot = itemView.findViewById(R.id.cardRoot);
            tvCrashTime = itemView.findViewById(R.id.tvCrashTime);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            btnDelete = itemView.findViewById(R.id.btnDelete);
            btnShare = itemView.findViewById(R.id.btnShare);
        }

        void bind(CrashLogEntry entry, int position) {
            tvCrashTime.setText(entry.crashTime);
            tvMessage.setText(entry.exceptionMsg);

            cardRoot.setOnClickListener(v -> {
                if (listener != null) listener.onViewDetail(position);
            });

            btnDelete.setOnClickListener(v -> {
                if (listener != null) listener.onDelete(position);
            });

            btnShare.setOnClickListener(v -> {
                if (listener != null) listener.onShare(position);
            });
        }
    }
}
