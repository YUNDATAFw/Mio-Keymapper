package io.takamiya.mio.floating.toolBoxFragment.tool.beacon;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import io.takamiya.mio.R;

public class BeaconColorMixerAdapter extends RecyclerView.Adapter<BeaconColorMixerAdapter.ViewHolder> {

    private final List<BeaconColorMixerItem> items;

    public BeaconColorMixerAdapter(List<BeaconColorMixerItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tool_beacon_color_calculation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BeaconColorMixerItem item = items.get(position);
        holder.tvName.setText(item.getDisplayName());
        holder.tvHex.setText(item.getHex());

        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(item.getColor());
        circle.setStroke(2, 0xFFB0B0B0);
        holder.colorView.setBackground(circle);
        holder.colorView.setClipToOutline(true);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View colorView;
        final android.widget.TextView tvName;
        final android.widget.TextView tvHex;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            colorView = itemView.findViewById(R.id.color_view);
            tvName = itemView.findViewById(R.id.tv_glass_name);
            tvHex = itemView.findViewById(R.id.tv_glass_hex);
        }
    }
}
