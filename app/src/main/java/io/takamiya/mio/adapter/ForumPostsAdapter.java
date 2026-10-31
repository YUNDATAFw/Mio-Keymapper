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
import io.takamiya.mio.bean.ForumPost;
import io.takamiya.mio.databinding.ItemLoadingFooterBinding;
import io.takamiya.mio.databinding.ItemPostBinding;
import io.takamiya.mio.utils.RelativeTimeUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ForumPostsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_ITEM = 0;
    private static final int TYPE_LOADING = 1;

    private List<ForumPost> list = new ArrayList<>();
    private boolean loadingMore;
    private boolean profileClickEnabled = true;
    private OnItemClickListener listener;
    private OnItemLongClickListener longListener;

    public interface OnItemClickListener {
        void onItemClick(ForumPost post);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(ForumPost post);
    }

    public void setData(List<ForumPost> incoming) {
        boolean hadFooter = loadingMore;
        loadingMore = false;
        List<ForumPost> newList = incoming != null ? incoming : new ArrayList<>();
        List<ForumPost> oldList = list;
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
                ForumPost oldItem = oldList.get(oldItemPosition);
                ForumPost newItem = newList.get(newItemPosition);
                return oldItem.getId() == newItem.getId()
                        && oldItem.isTop() == newItem.isTop()
                        && oldItem.getViews() == newItem.getViews()
                        && oldItem.getLikes() == newItem.getLikes()
                        && oldItem.getFavorites() == newItem.getFavorites()
                        && oldItem.getCoins() == newItem.getCoins()
                        && oldItem.getHeat() == newItem.getHeat()
                        && oldItem.getAuditStatus() == newItem.getAuditStatus()
                        && Objects.equals(oldItem.getTitle(), newItem.getTitle())
                        && Objects.equals(oldItem.getContentSummary(), newItem.getContentSummary())
                        && Objects.equals(oldItem.getSectionName(), newItem.getSectionName())
                        && Objects.equals(oldItem.getAuthorName(), newItem.getAuthorName())
                        && Objects.equals(oldItem.getAuthorNickname(), newItem.getAuthorNickname())
                        && Objects.equals(oldItem.getCreatedAt(), newItem.getCreatedAt())
                        && Objects.equals(oldItem.getTagsText(), newItem.getTagsText());
            }
        }, false);
        list = newList;
        if (hadFooter) {
            notifyDataSetChanged();
        } else {
            diff.dispatchUpdatesTo(this);
        }
    }

    public void appendData(List<ForumPost> incoming) {
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
        ItemPostBinding binding = ItemPostBinding.inflate(inflater, parent, false);
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

    private static String buildPreview(String content) {
        if (content == null || content.trim().isEmpty()) return null;
        String s = content
                .replaceAll("(?m)^\\s*[#>]+\\s*", "")
                .replace("```", "")
                .replaceAll("\\*\\*?([^*]+)\\*\\*?", "$1")
                .replaceAll("!\\[[^]]*]\\([^)]*\\)", "[图片]")
                .replaceAll("\\[([^]]*)]\\([^)]*\\)", "$1")
                .replaceAll("[~`|_]", "");
        s = s.replaceAll("\\s+", " ").trim();
        if (s.isEmpty()) return null;
        return s.length() > 120 ? s.substring(0, 120) + "…" : s;
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemPostBinding binding;

        ViewHolder(ItemPostBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (listener != null && pos != RecyclerView.NO_POSITION) {
                    listener.onItemClick(list.get(pos));
                }
            });
            itemView.setOnLongClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (longListener != null && pos != RecyclerView.NO_POSITION) {
                    longListener.onItemLongClick(list.get(pos));
                    return true;
                }
                return false;
            });
        }

        void bind(ForumPost post) {
            if (post == null) return;

            binding.tvTitle.setText(post.getTitle());

            binding.cardVip.setVisibility(post.isTop() == 1 ? View.VISIBLE : View.GONE);
            binding.cardRecommend.setVisibility(post.isRecommend() == 1 ? View.VISIBLE : View.GONE);
            List<String> tags = post.getTags();
            if (tags != null && !tags.isEmpty()) {
                binding.tvTag.setText(tags.get(0));
                binding.cardTag.setVisibility(View.VISIBLE);
            } else {
                binding.cardTag.setVisibility(View.GONE);
            }

            String preview = buildPreview(post.getContentSummary());
            if (preview == null) {
                binding.tvContent.setVisibility(View.GONE);
            } else {
                binding.tvContent.setVisibility(View.VISIBLE);
                binding.tvContent.setText(preview);
            }
            binding.tvViews.setText(post.getViews() + " 浏览");
            binding.tvLikes.setText(post.getLikes() + " 赞");
            binding.tvTime.setText(RelativeTimeUtil.toRelativeTime(post.getCreatedAt()));

            Glide.with(itemView.getContext())
                    .load(post.getAuthorAvatar())
                    .placeholder(R.mipmap.ic_launcher_round)
                    .into(binding.ivAuthorAvatar);

            binding.tvAuthor.setText(post.getAuthorNickname());
            binding.tvAuthor.setTextColor(ContextCompat.getColor(itemView.getContext(),
                    post.getAuthorMemberLevel() > 0
                            ? R.color.colorPrimary
                            : R.color.colorOnSecondaryContainer));

            Context ctx = itemView.getContext();
            if (profileClickEnabled && post.getUserId() > 0) {
                View.OnClickListener toProfile = v -> {
                    Intent intent = new Intent(ctx, UserProfileActivity.class);
                    intent.putExtra(UserProfileActivity.EXTRA_USER_ID, post.getUserId());
                    intent.putExtra(UserProfileActivity.EXTRA_USERNAME, post.getAuthorNickname());
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
