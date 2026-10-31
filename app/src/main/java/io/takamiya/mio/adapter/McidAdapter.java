package io.takamiya.mio.adapter;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.takamiya.mio.R;

public class McidAdapter extends RecyclerView.Adapter<McidAdapter.ViewHolder> {

    private List<String[]> items = new ArrayList<>();

    public void setData(List<String[]> items) {
        this.items = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    public List<String[]> getItems() {
        return items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mcid, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTitle;
        private final TextView tvMsg;
        private final TextView tvCategory;

        ViewHolder(View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.title);
            tvMsg = itemView.findViewById(R.id.msg);
            tvCategory = itemView.findViewById(R.id.category);
        }

        void bind(String[] item) {
            tvTitle.setText(item[0]);
            tvMsg.setText(item[1]);
            tvCategory.setText(item[2]);

            itemView.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("mcid", item[1]);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(v.getContext(), "已复制: " + item[1], Toast.LENGTH_SHORT).show();
            });
        }
    }
}
