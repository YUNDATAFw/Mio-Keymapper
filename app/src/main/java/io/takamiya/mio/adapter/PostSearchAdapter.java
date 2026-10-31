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
import io.takamiya.mio.databinding.ItemPostSearchBinding;

import java.util.ArrayList;
import java.util.List;

public class PostSearchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_ITEM = 0;
    private static final int TYPE_LOADING = 1;

    public static class PostItem {
        public final int id;
        public final String title;
        public final String contentSummary;
        public final String authorName;
        public final String authorAvatar;
        public final int authorUserId;
        public final int authorMemberLevel;
        public final String sectionName;
        public final String createdAt;
        public final int views;
        public final int likes;
        public final int favorites;
        public final int coins;

        public PostItem(int id, String title, String contentSummary, String authorName,
                        String authorAvatar, int authorUserId, int authorMemberLevel,
                        String sectionName, String createdAt,
                        int views, int likes, int favorites, int coins) {
            this.id = id;
            this.title = title;
            this.contentSummary = contentSummary;
            this.authorName = authorName;
            this.authorAvatar = authorAvatar;
            this.authorUserId = authorUserId;
            this.authorMemberLevel = authorMemberLevel;
            this.sectionName = sectionName;
            this.createdAt = createdAt;
            this.views = views;
            this.likes = likes;
            this.favorites = favorites;
            this.coins = coins;
        }
    }

    private final List<PostItem> list = new ArrayList<>();
    private boolean loadingMore;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(PostItem item);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<PostItem> incoming) {
        list.clear();
        if (incoming != null) list.addAll(incoming);
        notifyDataSetChanged();
    }

    public void appendData(List<PostItem> incoming) {
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

    @Override
    public int getItemViewType(int position) {
        return position >= list.size() ? TYPE_LOADING : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_LOADING) {
            View view = inflater.inflate(R.layout.item_loading_footer, parent, false);
            return new LoadingHolder(view);
        }
        ItemPostSearchBinding binding = ItemPostSearchBinding.inflate(inflater, parent, false);
        return new PostViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof LoadingHolder) return;
        ((PostViewHolder) holder).bind(list.get(position));
    }

    @Override
    public int getItemCount() {
        return list.size() + (loadingMore ? 1 : 0);
    }

    class PostViewHolder extends RecyclerView.ViewHolder {
        private final ItemPostSearchBinding binding;

        PostViewHolder(ItemPostSearchBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (listener != null && pos != RecyclerView.NO_POSITION && pos < list.size()) {
                    listener.onItemClick(list.get(pos));
                }
            });
        }

        void bind(PostItem item) {
            binding.tvTitle.setText(item.title);
            binding.tvAuthor.setText(item.authorName);
            binding.tvTime.setText(item.createdAt != null ? item.createdAt : "");
            binding.tvViews.setText(item.views + " 浏览");
            binding.tvLikes.setText(item.likes + " 赞");
            binding.tvFavorites.setText(item.favorites + " 收藏");
            binding.tvCoins.setText(item.coins + " 投币");

            Context ctx = binding.getRoot().getContext();
            binding.tvAuthor.setTextColor(ContextCompat.getColor(ctx,
                    item.authorMemberLevel > 0
                            ? R.color.colorPrimary
                            : R.color.colorOnSecondaryContainer));

            View.OnClickListener toProfile = v -> {
                if (item.authorUserId > 0) {
                    Intent intent = new Intent(ctx, UserProfileActivity.class);
                    intent.putExtra(UserProfileActivity.EXTRA_USER_ID, item.authorUserId);
                    intent.putExtra(UserProfileActivity.EXTRA_USERNAME, item.authorName);
                    ctx.startActivity(intent);
                }
            };
            binding.ivAuthorAvatar.setOnClickListener(toProfile);
            binding.tvAuthor.setOnClickListener(toProfile);

            String summary = item.contentSummary;
            binding.tvSummary.setText(
                    (summary == null || summary.isEmpty()) ? "" : summary);

            String section = item.sectionName;
            if (section != null && !section.isEmpty()) {
                binding.cardSection.setVisibility(View.VISIBLE);
                binding.tvSection.setText(section);
            } else {
                binding.cardSection.setVisibility(View.GONE);
            }

            if (item.authorAvatar != null && !item.authorAvatar.isEmpty()) {
                Glide.with(ctx)
                        .load(item.authorAvatar)
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
