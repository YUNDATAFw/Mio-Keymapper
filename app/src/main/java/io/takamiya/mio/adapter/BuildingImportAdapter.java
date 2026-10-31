package io.takamiya.mio.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

import io.takamiya.mio.R;
import io.takamiya.mio.building.BuildingInfo;

/**
 * 导入建筑侧边栏列表项（{@code item_building_import.xml}）。
 * 该布局不可修改且各子 View 均无 id，因此按类型遍历回溯标题/副标题。
 */
public class BuildingImportAdapter extends RecyclerView.Adapter<BuildingImportAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(BuildingInfo info);
    }

    /** 点击行右侧 add_link 图标（打开导入操作 chip）。 */
    public interface OnItemActionListener {
        void onItemAction(BuildingInfo info);
    }

    private final List<BuildingInfo> items = new ArrayList<>();
    private OnItemClickListener listener;
    private OnItemActionListener actionListener;

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setOnItemActionListener(OnItemActionListener listener) {
        this.actionListener = listener;
    }

    public void setData(List<BuildingInfo> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    public List<BuildingInfo> getData() {
        return items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_building_import, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position), listener, actionListener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTitle;
        private final TextView tvSubtitle;
        private final MaterialButton btnAction;

        ViewHolder(View itemView) {
            super(itemView);
            MaterialButton action = findAction(itemView);
            List<TextView> texts = new ArrayList<>();
            collectTexts(itemView, texts, action);
            tvTitle = texts.size() > 0 ? texts.get(0) : null;
            tvSubtitle = texts.size() > 1 ? texts.get(1) : null;
            btnAction = action;
        }

        private static MaterialButton findAction(View v) {
            if (v instanceof MaterialButton) return (MaterialButton) v;
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) {
                    MaterialButton found = findAction(g.getChildAt(i));
                    if (found != null) return found;
                }
            }
            return null;
        }

        /** 收集除按钮外的文本（标题在前、副标题在后）。 */
        private static void collectTexts(View v, List<TextView> out, MaterialButton skip) {
            if (v instanceof TextView && v != skip) {
                out.add((TextView) v);
                return;
            }
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) {
                    collectTexts(g.getChildAt(i), out, skip);
                    if (out.size() >= 2) return;
                }
            }
        }

        void bind(BuildingInfo info, OnItemClickListener listener,
                  OnItemActionListener actionListener) {
            if (tvTitle != null) tvTitle.setText(info.name);
            if (tvSubtitle != null) {
                tvSubtitle.setText(info.sx + "×" + info.sy + "×" + info.sz
                        + " · " + info.commandCount + " 条命令");
            }
            // 点击整行：打开该建筑的命令子面板
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(info);
            });
            // 右侧 add_link 图标：打开导入操作 chip
            if (btnAction != null) {
                btnAction.setOnClickListener(v -> {
                    if (actionListener != null) actionListener.onItemAction(info);
                });
            }
        }
    }
}
