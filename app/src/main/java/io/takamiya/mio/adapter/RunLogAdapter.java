package io.takamiya.mio.adapter;

import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.R;

public class RunLogAdapter extends RecyclerView.Adapter<RunLogAdapter.ViewHolder> {

    private List<AppLogger.LogEntry> logs = new ArrayList<>();
    public static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault());

    public static final int[] LEVEL_COLORS = {
        0, 0,                                          // 0,1 unused
        0xFF9E9E9E, // VERBOSE=2  gray
        0xFF2196F3, // DEBUG=3    blue
        0xFF4CAF50, // INFO=4     green
        0xFFFF9800, // WARN=5     orange
        0xFFF44336, // ERROR=6    red
        0xFF9C27B0, // ASSERT=7   purple
    };

    public static final String[] LEVEL_LABELS = {
        "", "", "V", "D", "I", "W", "E", "A"
    };

    public void setData(List<AppLogger.LogEntry> logs) {
        this.logs = logs != null ? logs : new ArrayList<>();
        notifyDataSetChanged();
    }

    public List<AppLogger.LogEntry> getLogs() {
        return logs;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_run_log, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(logs.get(position));
    }

    @Override
    public int getItemCount() {
        return logs.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final View levelDot;
        private final TextView tvTag;
        private final TextView tvTimestamp;
        private final TextView tvLevel;
        private final TextView tvMessage;
        private final TextView tvThrowable;
        private final ImageView ivExpand;
        private final View cardRoot;

        private boolean expanded = false;

        ViewHolder(View itemView) {
            super(itemView);
            levelDot = itemView.findViewById(R.id.levelDot);
            tvTag = itemView.findViewById(R.id.tvTag);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvLevel = itemView.findViewById(R.id.tvLevel);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvThrowable = itemView.findViewById(R.id.tvThrowable);
            ivExpand = itemView.findViewById(R.id.ivExpand);
            cardRoot = itemView.findViewById(R.id.cardRoot);

            cardRoot.setOnClickListener(v -> {
                if (tvThrowable.getText().length() == 0) return;
                expanded = !expanded;
                tvThrowable.setVisibility(expanded ? View.VISIBLE : View.GONE);
                ivExpand.setImageResource(expanded ? R.drawable.material_symbols_arrow_drop_up_rounded
                    : R.drawable.material_symbols_arrow_drop_down_rounded);
            });
        }

        void bind(AppLogger.LogEntry entry) {
            int level = entry.level;
            if (level >= 0 && level < LEVEL_COLORS.length) {
                Drawable bg = levelDot.getBackground();
                if (bg != null) {
                    bg.setColorFilter(LEVEL_COLORS[level], PorterDuff.Mode.SRC_IN);
                }
                tvLevel.setText(LEVEL_LABELS[level]);
                tvLevel.setBackgroundTintList(android.content.res.ColorStateList.valueOf(LEVEL_COLORS[level]));
            }

            tvTag.setText(entry.tag);
            tvTimestamp.setText(TIME_FORMAT.format(new Date(entry.timestamp)));
            tvMessage.setText(entry.message);

            expanded = false;
            String tr = entry.throwable;
            if (tr != null && !tr.isEmpty()) {
                tvThrowable.setText(tr);
                tvThrowable.setVisibility(View.GONE);
                ivExpand.setVisibility(View.VISIBLE);
                ivExpand.setImageResource(R.drawable.material_symbols_arrow_drop_down_rounded);
            } else {
                tvThrowable.setText(null);
                tvThrowable.setVisibility(View.GONE);
                ivExpand.setVisibility(View.GONE);
            }
        }
    }
}
