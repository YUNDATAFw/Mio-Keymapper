package io.takamiya.mio.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import io.takamiya.mio.R;
import io.takamiya.mio.UserProfileActivity;
import io.takamiya.mio.databinding.ItemFavoriteBinding;
import io.takamiya.mio.utils.RelativeTimeUtil;

import java.util.ArrayList;
import java.util.List;

public class MyFavoritesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static class FavoriteItem {
        public final int id;
        public final int targetType;
        public final int targetId;
        public final String targetTitle;
        public final String targetAuthorName;
        public final String targetAuthorAvatar;
        public final int targetAuthorUserId;
        public final String targetSectionName;
        public final String targetContentSummary;
        public final int targetViews;
        public final int targetLikes;
        public final int targetFavorites;
        public final String createdAt;

        public FavoriteItem(int id, int targetType, int targetId, String targetTitle,
                            String targetAuthorName, String targetAuthorAvatar,
                            int targetAuthorUserId,
                            String targetSectionName, String targetContentSummary,
                            int targetViews, int targetLikes, int targetFavorites,
                            String createdAt) {
            this.id = id;
            this.targetType = targetType;
            this.targetId = targetId;
            this.targetTitle = targetTitle;
            this.targetAuthorName = targetAuthorName;
            this.targetAuthorAvatar = targetAuthorAvatar;
            this.targetAuthorUserId = targetAuthorUserId;
            this.targetSectionName = targetSectionName;
            this.targetContentSummary = targetContentSummary;
            this.targetViews = targetViews;
            this.targetLikes = targetLikes;
            this.targetFavorites = targetFavorites;
            this.createdAt = createdAt;
        }
    }

    private final List<FavoriteItem> list = new ArrayList<>();
    private boolean loadingMore;
    private OnItemClickListener listener;
    private OnItemLongClickListener longListener;

    public interface OnItemClickListener {
        void onItemClick(FavoriteItem item);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(FavoriteItem item);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.longListener = listener;
    }

    public void setData(List<FavoriteItem> incoming) {
        list.clear();
        if (incoming != null) list.addAll(incoming);
        notifyDataSetChanged();
    }

    public void appendData(List<FavoriteItem> incoming) {
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

    public List<FavoriteItem> getData() {
        return list;
    }

    @Override
    public int getItemViewType(int position) {
        return position >= list.size() ? 1 : 0;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == 1) {
            View view = inflater.inflate(R.layout.item_loading_footer, parent, false);
            return new LoadingHolder(view);
        }
        ItemFavoriteBinding binding = ItemFavoriteBinding.inflate(inflater, parent, false);
        return new ViewHolder(binding);
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
        private final ItemFavoriteBinding binding;

        ViewHolder(ItemFavoriteBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (listener != null && pos != RecyclerView.NO_POSITION && pos < list.size()) {
                    listener.onItemClick(list.get(pos));
                }
            });
            itemView.setOnLongClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (longListener != null && pos != RecyclerView.NO_POSITION && pos < list.size()) {
                    longListener.onItemLongClick(list.get(pos));
                    return true;
                }
                return false;
            });
        }

        void bind(FavoriteItem item) {
            binding.tvTitle.setText(item.targetTitle);
            binding.tvTime.setText(RelativeTimeUtil.toRelativeTime(item.createdAt));

            if (item.targetType == 2) {
                binding.tvType.setText("资源");
                binding.tvType.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.colorPrimary));
            } else {
                binding.tvType.setText("帖子");
                binding.tvType.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.colorSecondary));
            }

            String summary = item.targetContentSummary;
            binding.tvSummary.setText(
                    (summary == null || summary.isEmpty()) ? "" : summary);

            binding.tvAuthor.setText(item.targetAuthorName != null ? item.targetAuthorName : "");

            Context ctx = binding.getRoot().getContext();
            View.OnClickListener toProfile = v -> {
                if (item.targetAuthorUserId > 0) {
                    Intent intent = new Intent(ctx, UserProfileActivity.class);
                    intent.putExtra(UserProfileActivity.EXTRA_USER_ID, item.targetAuthorUserId);
                    intent.putExtra(UserProfileActivity.EXTRA_USERNAME, item.targetAuthorName);
                    ctx.startActivity(intent);
                }
            };
            binding.ivAuthorAvatar.setOnClickListener(toProfile);
            binding.tvAuthor.setOnClickListener(toProfile);

            String section = item.targetSectionName;
            if (section != null && !section.isEmpty()) {
                binding.cardSection.setVisibility(View.VISIBLE);
                binding.tvSection.setText(section);
            } else {
                binding.cardSection.setVisibility(View.GONE);
            }

            binding.tvViews.setText(item.targetViews + " 浏览");
            binding.tvLikes.setText(item.targetLikes + " 赞");
            binding.tvFavorites.setText(item.targetFavorites + " 收藏");

            if (item.targetAuthorAvatar != null && !item.targetAuthorAvatar.isEmpty()) {
                Glide.with(ctx)
                        .load(item.targetAuthorAvatar)
                        .placeholder(R.drawable.material_symbols_account_circle_outline)
                        .circleCrop()
                        .into(binding.ivAuthorAvatar);
            } else {
                binding.ivAuthorAvatar.setImageResource(R.drawable.material_symbols_account_circle_outline);
            }
        }
    }

    static class LoadingHolder extends RecyclerView.ViewHolder {
        LoadingHolder(View itemView) {
            super(itemView);
        }
    }
}
