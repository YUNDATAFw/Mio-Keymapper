package io.takamiya.mio.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import io.takamiya.mio.R;
import io.takamiya.mio.bean.ManuscriptItem;
import io.takamiya.mio.databinding.ItemLoadingFooterBinding;
import io.takamiya.mio.databinding.ItemManuscriptBinding;
import io.takamiya.mio.utils.RelativeTimeUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ManuscriptAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_ITEM = 0;
    private static final int TYPE_LOADING = 1;

    private List<ManuscriptItem> list = new ArrayList<>();
    private boolean loadingMore;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(ManuscriptItem item);
    }

    /** 审核状态样式：文案、卡片底色、文字色。 */
    public static class AuditStyle {
        public final String text;
        public final int bgColorRes;
        public final int textColorRes;

        AuditStyle(String text, int bgColorRes, int textColorRes) {
            this.text = text;
            this.bgColorRes = bgColorRes;
            this.textColorRes = textColorRes;
        }
    }

    public static AuditStyle auditStyle(int status) {
        switch (status) {
            case 0:
                return new AuditStyle("待审核", R.color.colorTertiaryContainer, R.color.colorTertiary);
            case 1:
                return new AuditStyle("审核中", R.color.colorTertiaryContainer, R.color.colorTertiary);
            case 2:
                return new AuditStyle("已通过", R.color.colorPrimaryContainer, R.color.colorPrimary);
            case 3:
                return new AuditStyle("已打回", R.color.colorErrorContainer, R.color.colorError);
            case 4:
                return new AuditStyle("已锁定", R.color.colorSurfaceContainerHighest, R.color.colorOnSurfaceVariant);
            case 5:
                return new AuditStyle("复审", R.color.colorTertiaryContainer, R.color.colorTertiary);
            default:
                return new AuditStyle("未知", R.color.colorSurfaceContainerHighest, R.color.colorOnSurfaceVariant);
        }
    }

    public void setData(List<ManuscriptItem> incoming) {
        boolean hadFooter = loadingMore;
        loadingMore = false;
        List<ManuscriptItem> newList = incoming != null ? incoming : new ArrayList<>();
        List<ManuscriptItem> oldList = list;
        if (oldList.isEmpty()) {
            list = newList;
            notifyDataSetChanged();
            return;
        }
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldList.size();
            }

            @Override
            public int getNewListSize() {
                return newList.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                ManuscriptItem a = oldList.get(oldItemPosition);
                ManuscriptItem b = newList.get(newItemPosition);
                return a.getType() == b.getType() && a.getId() == b.getId();
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                ManuscriptItem a = oldList.get(oldItemPosition);
                ManuscriptItem b = newList.get(newItemPosition);
                return a.getAuditStatus() == b.getAuditStatus()
                        && a.getViews() == b.getViews()
                        && a.getLikes() == b.getLikes()
                        && Objects.equals(a.getTitle(), b.getTitle())
                        && Objects.equals(a.getSectionName(), b.getSectionName())
                        && Objects.equals(a.getCreatedAt(), b.getCreatedAt());
            }
        }, false);
        list = newList;
        if (hadFooter) {
            notifyDataSetChanged();
        } else {
            diff.dispatchUpdatesTo(this);
        }
    }

    public void appendData(List<ManuscriptItem> incoming) {
        if (incoming == null || incoming.isEmpty()) return;
        int start = list.size();
        list.addAll(incoming);
        notifyItemRangeInserted(start, incoming.size());
    }

    public void setLoadingMore(boolean loading) {
        if (this.loadingMore == loading) return;
        int footerPos = list.size();
        this.loadingMore = loading;
        if (loading) {
            notifyItemInserted(footerPos);
        } else {
            notifyItemRemoved(footerPos);
        }
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return position >= list.size() ? TYPE_LOADING : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_LOADING) {
            ItemLoadingFooterBinding binding = ItemLoadingFooterBinding.inflate(inflater, parent, false);
            return new LoadingHolder(binding);
        }
        return new ViewHolder(ItemManuscriptBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof LoadingHolder) return;
        ((ViewHolder) holder).bind(list.get(position));
    }

    @Override
    public int getItemCount() {
        return list.size() + (loadingMore ? 1 : 0);
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemManuscriptBinding binding;

        ViewHolder(ItemManuscriptBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (listener != null && pos != RecyclerView.NO_POSITION) {
                    listener.onItemClick(list.get(pos));
                }
            });
        }

        void bind(ManuscriptItem item) {
            if (item == null) return;
            binding.tvTitle.setText(item.getTitle());

            AuditStyle style = auditStyle(item.getAuditStatus());
            binding.tvAudit.setText(style.text);
            binding.cardAudit.setCardBackgroundColor(
                    ContextCompat.getColor(itemView.getContext(), style.bgColorRes));
            binding.tvAudit.setTextColor(
                    ContextCompat.getColor(itemView.getContext(), style.textColorRes));

            if (item.getSectionName().isEmpty()) {
                binding.tvSection.setVisibility(View.GONE);
            } else {
                binding.tvSection.setVisibility(View.VISIBLE);
                binding.tvSection.setText(item.getSectionName());
            }
            binding.tvViews.setText(item.getViews() + " 浏览");
            binding.tvLikes.setText(item.getLikes() + " 赞");
            binding.tvTime.setText(RelativeTimeUtil.toRelativeTime(item.getCreatedAt()));
        }
    }

    static class LoadingHolder extends RecyclerView.ViewHolder {
        LoadingHolder(ItemLoadingFooterBinding binding) {
            super(binding.getRoot());
        }
    }
}