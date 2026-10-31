package io.takamiya.mio.adapter;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

import io.noties.markwon.Markwon;
import io.takamiya.mio.R;
import io.takamiya.mio.UserProfileActivity;
import io.takamiya.mio.bean.Replies;
import io.takamiya.mio.databinding.ItemRepliesDetailBinding;
import io.takamiya.mio.utils.MarkdownProvider;
import io.takamiya.mio.utils.RelativeTimeUtil;

public class RepliesDetailAdapter extends RecyclerView.Adapter<RepliesDetailAdapter.ViewHolder> {

    public interface OnReplyActionListener {
        void onReplyLike(Replies reply);

        void onReplyMore(Replies reply);
    }

    public interface OnReplyKeepClickListener {
        void onKeep(Replies reply);
    }

    public interface OnTargetClickListener {
        void onTargetClick(Replies reply);
    }

    private final List<Replies> list = new ArrayList<>();
    private Markwon markwon;
    private OnReplyActionListener actionListener;
    private OnReplyKeepClickListener keepClickListener;
    private OnTargetClickListener targetClickListener;
    private int parentCommentId;

    public void setParentCommentId(int parentCommentId) {
        this.parentCommentId = parentCommentId;
    }

    public void setData(List<Replies> incoming) {
        list.clear();
        if (incoming != null) {
            list.addAll(incoming);
        }
        notifyDataSetChanged();
    }

    public void setOnReplyActionListener(OnReplyActionListener listener) {
        this.actionListener = listener;
    }

    public void setOnReplyKeepClickListener(OnReplyKeepClickListener listener) {
        this.keepClickListener = listener;
    }

    public void setOnTargetClickListener(OnTargetClickListener listener) {
        this.targetClickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemRepliesDetailBinding binding = ItemRepliesDetailBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        if (markwon == null) {
            markwon = MarkdownProvider.create(parent.getContext());
        }
        return new ViewHolder(binding, actionListener, keepClickListener, targetClickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(list.get(position), position == getItemCount() - 1, markwon, parentCommentId);
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemRepliesDetailBinding binding;
        private OnReplyActionListener actionListener;
        private OnReplyKeepClickListener keepClickListener;
        private OnTargetClickListener targetClickListener;

        ViewHolder(ItemRepliesDetailBinding binding, OnReplyActionListener actionListener,
                   OnReplyKeepClickListener keepClickListener, OnTargetClickListener targetClickListener) {
            super(binding.getRoot());
            this.binding = binding;
            this.actionListener = actionListener;
            this.keepClickListener = keepClickListener;
            this.targetClickListener = targetClickListener;
        }

        void bind(Replies reply, boolean isLast, Markwon markwon, int parentCommentId) {
            if (reply == null) return;

            Context ctx = itemView.getContext();
            Glide.with(ctx)
                    .load(reply.getAuthorAvatar())
                    .placeholder(R.mipmap.ic_launcher_round)
                    .into(binding.rdAuthorAvatar);

            binding.rdAuthorNickname.setText(reply.getAuthorNickname());
            if (reply.getAuthorMemberLevel() > 0) {
                binding.rdAuthorNickname.setTextColor(
                        ContextCompat.getColor(ctx, R.color.colorPrimary));
            } else {
                binding.rdAuthorNickname.setTextColor(
                        ContextCompat.getColor(ctx, R.color.colorOnSecondaryContainer));
            }

            if (reply.getUserId() > 0) {
                View.OnClickListener toProfile = v -> {
                    Intent intent = new Intent(ctx, UserProfileActivity.class);
                    intent.putExtra(UserProfileActivity.EXTRA_USER_ID, reply.getUserId());
                    intent.putExtra(UserProfileActivity.EXTRA_USERNAME, reply.getAuthorNickname());
                    ctx.startActivity(intent);
                };
                binding.rdAuthorAvatar.setOnClickListener(toProfile);
                binding.rdAuthorNickname.setOnClickListener(toProfile);
            }

            binding.rdAuthorLevelTitle.setText(reply.getAuthorUserLevelTitle());
            String content = reply.getContent();
            if (content != null && !content.isEmpty()) {
                markwon.setMarkdown(binding.rdContent, content);
                binding.rdContent.setVisibility(View.VISIBLE);
            } else {
                binding.rdContent.setText(null);
                binding.rdContent.setVisibility(View.GONE);
            }

            boolean isReplyToComment = parentCommentId > 0
                    && reply.getParentId() == parentCommentId;
            String prefixNickname = reply.getReplyToUserNickname();
            if (!isReplyToComment && prefixNickname != null && !prefixNickname.isEmpty()) {
                binding.rdTargetName.setText(prefixNickname);
                binding.rdPrefixText.setVisibility(View.VISIBLE);
                binding.rdTargetName.setVisibility(View.VISIBLE);
                binding.rdColon.setVisibility(View.VISIBLE);
                binding.rdTargetName.setOnClickListener(v -> {
                    if (targetClickListener != null) {
                        targetClickListener.onTargetClick(reply);
                    }
                });
            } else {
                binding.rdPrefixText.setVisibility(View.GONE);
                binding.rdTargetName.setVisibility(View.GONE);
                binding.rdColon.setVisibility(View.GONE);
            }

            binding.rdTime.setText(RelativeTimeUtil.toRelativeTime(reply.getCreatedAt()));
            binding.rdDivider.setVisibility(isLast ? View.GONE : View.VISIBLE);

            applyLikeState(reply);
            binding.rdLikeBtn.setOnClickListener(v -> {
                reply.setLiked(!reply.isLiked());
                reply.setLikeCount(reply.getLikeCount() + (reply.isLiked() ? 1 : -1));
                applyLikeState(reply);
                if (actionListener != null) {
                    actionListener.onReplyLike(reply);
                }
            });
            binding.rdReplyBtn.setOnClickListener(v -> {
                if (keepClickListener != null) {
                    keepClickListener.onKeep(reply);
                }
            });
            binding.rdMoreBtn.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onReplyMore(reply);
                }
            });
        }

        private void applyLikeState(Replies reply) {
            binding.rdLikeCount.setText(String.valueOf(reply.getLikeCount()));
            binding.rdLikeBtn.setImageResource(reply.isLiked()
                    ? R.drawable.material_symbols_favorite_rounded
                    : R.drawable.material_symbols_favorite_outline_rounded);
            int color = reply.isLiked()
                    ? R.color.colorPrimary
                    : R.color.colorOnSecondaryContainer;
            ImageViewCompat.setImageTintList(binding.rdLikeBtn,
                    ColorStateList.valueOf(itemView.getContext().getColor(color)));
        }
    }
}