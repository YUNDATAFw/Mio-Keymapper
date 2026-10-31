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
import io.takamiya.mio.databinding.ItemUserSearchBinding;

import java.util.ArrayList;
import java.util.List;

public class UserSearchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_ITEM = 0;
    private static final int TYPE_LOADING = 1;

    public static class UserItem {
        public final int userId;
        public final String username;
        public final String nickname;
        public final String avatar;
        public final int memberLevel;
        public final int level;
        public final String levelTitle;
        public final int fansCount;
        public final int postsCount;
        public final int resourcesCount;

        public UserItem(int userId, String username, String nickname, String avatar,
                        int memberLevel, int level, String levelTitle,
                        int fansCount, int postsCount, int resourcesCount) {
            this.userId = userId;
            this.username = username;
            this.nickname = nickname;
            this.avatar = avatar;
            this.memberLevel = memberLevel;
            this.level = level;
            this.levelTitle = levelTitle;
            this.fansCount = fansCount;
            this.postsCount = postsCount;
            this.resourcesCount = resourcesCount;
        }
    }

    private final List<UserItem> list = new ArrayList<>();
    private boolean loadingMore;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(UserItem item);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<UserItem> incoming) {
        list.clear();
        if (incoming != null) list.addAll(incoming);
        notifyDataSetChanged();
    }

    public void appendData(List<UserItem> incoming) {
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
        ItemUserSearchBinding binding = ItemUserSearchBinding.inflate(inflater, parent, false);
        return new UserViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof LoadingHolder) return;
        ((UserViewHolder) holder).bind(list.get(position));
    }

    @Override
    public int getItemCount() {
        return list.size() + (loadingMore ? 1 : 0);
    }

    class UserViewHolder extends RecyclerView.ViewHolder {
        private final ItemUserSearchBinding binding;

        UserViewHolder(ItemUserSearchBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (listener != null && pos != RecyclerView.NO_POSITION && pos < list.size()) {
                    listener.onItemClick(list.get(pos));
                }
            });
        }

        void bind(UserItem item) {
            binding.tvNickname.setText(item.nickname);
            binding.tvUsername.setText("@" + item.username);

            Context ctx = binding.getRoot().getContext();
            if (item.memberLevel > 0) {
                binding.tvNickname.setTextColor(ContextCompat.getColor(ctx, R.color.colorPrimary));
            } else {
                binding.tvNickname.setTextColor(ContextCompat.getColor(ctx, R.color.colorOnSecondaryContainer));
            }

            View.OnClickListener toProfile = v -> {
                if (item.userId > 0) {
                    Intent intent = new Intent(ctx, UserProfileActivity.class);
                    intent.putExtra(UserProfileActivity.EXTRA_USER_ID, item.userId);
                    intent.putExtra(UserProfileActivity.EXTRA_USERNAME, item.nickname);
                    ctx.startActivity(intent);
                }
            };
            binding.ivAvatar.setOnClickListener(toProfile);
            binding.tvNickname.setOnClickListener(toProfile);

            String levelText = item.levelTitle != null && !item.levelTitle.isEmpty()
                    ? item.levelTitle : "Lv." + item.level;
            binding.tvLevel.setText(levelText);

            binding.tvFansCount.setText(String.valueOf(item.fansCount));
            binding.tvPostsCount.setText(String.valueOf(item.postsCount));
            binding.tvResourcesCount.setText(String.valueOf(item.resourcesCount));

            if (item.avatar != null && !item.avatar.isEmpty()) {
                Glide.with(ctx)
                        .load(item.avatar)
                        .placeholder(R.drawable.material_symbols_account_circle_outline)
                        .circleCrop()
                        .into(binding.ivAvatar);
            } else {
                binding.ivAvatar.setImageResource(R.drawable.material_symbols_account_circle_outline);
            }
        }
    }

    static class LoadingHolder extends RecyclerView.ViewHolder {
        LoadingHolder(View itemView) {
            super(itemView);
        }
    }
}
