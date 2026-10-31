package io.takamiya.mio.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import io.takamiya.mio.R;
import io.takamiya.mio.UserProfileActivity;
import io.takamiya.mio.bean.ForumResource;
import io.takamiya.mio.databinding.ItemLoadingFooterBinding;
import io.takamiya.mio.databinding.ItemResourcesBinding;
import io.takamiya.mio.utils.RelativeTimeUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ForumResourcesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_ITEM = 0;
    private static final int TYPE_LOADING = 1;

    private List<ForumResource> list = new ArrayList<>();
    private boolean loadingMore;
    private boolean profileClickEnabled = true;
    private OnItemClickListener listener;
    private OnItemLongClickListener longListener;

    public interface OnItemClickListener {
        void onItemClick(ForumResource resource);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(ForumResource resource);
    }

    public void setData(List<ForumResource> incoming) {
        boolean hadFooter = loadingMore;
        loadingMore = false;
        List<ForumResource> newList = incoming != null ? incoming : new ArrayList<>();
        List<ForumResource> oldList = list;
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
                return oldList.get(oldItemPosition).getId() == newList.get(newItemPosition).getId();
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                ForumResource oldItem = oldList.get(oldItemPosition);
                ForumResource newItem = newList.get(newItemPosition);
                return oldItem.getId() == newItem.getId()
                        && oldItem.getLikes() == newItem.getLikes()
                        && oldItem.getFavorites() == newItem.getFavorites()
                        && oldItem.getCoins() == newItem.getCoins()
                        && oldItem.getHeat() == newItem.getHeat()
                        && oldItem.isTop() == newItem.isTop()
                        && oldItem.isPurchased() == newItem.isPurchased()
                        && Objects.equals(oldItem.getTitle(), newItem.getTitle())
                        && Objects.equals(oldItem.getIntroSummary(), newItem.getIntroSummary())
                        && Objects.equals(oldItem.getAuthorDisplayName(), newItem.getAuthorDisplayName())
                        && Objects.equals(oldItem.getCreatedAt(), newItem.getCreatedAt());
            }
        }, false);
        list = newList;
        if (hadFooter) {
            notifyDataSetChanged();
        } else {
            diff.dispatchUpdatesTo(this);
        }
    }

    public void appendData(List<ForumResource> incoming) {
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

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.longListener = listener;
    }

    public void setProfileClickEnabled(boolean enabled) {
        this.profileClickEnabled = enabled;
    }

    public List<ForumResource> getData() {
        return list;
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
        ItemResourcesBinding binding = ItemResourcesBinding.inflate(inflater, parent, false);
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
        private final ItemResourcesBinding binding;

        ViewHolder(ItemResourcesBinding binding) {
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

        void bind(ForumResource resource) {
            if (resource == null) return;

            binding.tvTitle.setText(resource.getTitle());

            binding.cardVip.setVisibility(resource.isTop() == 1 ? View.VISIBLE : View.GONE);
            binding.cardRecommend.setVisibility(resource.isRecommend() == 1 ? View.VISIBLE : View.GONE);
            List<ForumResource.ForumTag> tags = resource.getTags();
            if (tags != null && !tags.isEmpty()) {
                binding.tvTag.setText(tags.get(0).getName());
                binding.cardTag.setVisibility(View.VISIBLE);
            } else {
                binding.cardTag.setVisibility(View.GONE);
            }

            String intro = resource.getIntroSummary();
            if (intro == null || intro.trim().isEmpty()) {
                binding.tvContent.setVisibility(View.GONE);
            } else {
                binding.tvContent.setVisibility(View.VISIBLE);
                binding.tvContent.setText(intro);
            }

            binding.tvViews.setText(resource.getViews() + " 浏览");
            binding.tvLikes.setText(resource.getLikes() + " 赞");
            binding.tvTime.setText(RelativeTimeUtil.toRelativeTime(resource.getCreatedAt()));

            Glide.with(itemView.getContext())
                    .load(resource.getAuthorAvatar())
                    .placeholder(R.mipmap.ic_launcher_round)
                    .into(binding.ivAuthorAvatar);

            binding.tvAuthor.setText(resource.getAuthorDisplayName());
            binding.tvAuthor.setTextColor(ContextCompat.getColor(itemView.getContext(),
                    resource.getAuthorMemberLevel() > 0
                            ? R.color.colorPrimary
                            : R.color.colorOnSecondaryContainer));

            Context ctx = itemView.getContext();
            if (profileClickEnabled && resource.getUserId() > 0) {
                View.OnClickListener toProfile = v -> {
                    Intent intent = new Intent(ctx, UserProfileActivity.class);
                    intent.putExtra(UserProfileActivity.EXTRA_USER_ID, resource.getUserId());
                    intent.putExtra(UserProfileActivity.EXTRA_USERNAME, resource.getAuthorDisplayName());
                    ctx.startActivity(intent);
                };
                binding.ivAuthorAvatar.setOnClickListener(toProfile);
                binding.tvAuthor.setOnClickListener(toProfile);
            } else {
                binding.ivAuthorAvatar.setOnClickListener(null);
                binding.tvAuthor.setOnClickListener(null);
            }
        }
    }

    static class LoadingHolder extends RecyclerView.ViewHolder {
        LoadingHolder(ItemLoadingFooterBinding binding) {
            super(binding.getRoot());
        }
    }
}