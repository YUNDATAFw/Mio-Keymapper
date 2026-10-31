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

import java.util.ArrayList;
import java.util.List;

import io.noties.markwon.Markwon;
import io.takamiya.mio.R;
import io.takamiya.mio.UserProfileActivity;
import io.takamiya.mio.bean.Replies;
import io.takamiya.mio.databinding.ItemRepliesBinding;
import io.takamiya.mio.utils.MarkdownProvider;

public class RepliesAdapter extends RecyclerView.Adapter<RepliesAdapter.ViewHolder> {

    public interface OnReplyClickListener {
        void onReplyClick(Replies reply);
    }

    public interface OnReplyActionListener {
        void onReplyLike(Replies reply);

        void onReplyMore(Replies reply);
    }

    public interface OnReplyTargetClickListener {
        void onReplyTargetClick(Replies reply);
    }

    private final List<Replies> list = new ArrayList<>();
    private Markwon markwon;
    private OnReplyClickListener replyClickListener;
    private OnReplyActionListener actionListener;
    private OnReplyTargetClickListener replyTargetClickListener;
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

    public void setOnReplyClickListener(OnReplyClickListener listener) {
        this.replyClickListener = listener;
    }

    public void setOnReplyActionListener(OnReplyActionListener listener) {
        this.actionListener = listener;
    }

    public void setOnReplyTargetClickListener(OnReplyTargetClickListener listener) {
        this.replyTargetClickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemRepliesBinding binding = ItemRepliesBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        if (markwon == null) {
            markwon = MarkdownProvider.create(parent.getContext());
        }
        return new ViewHolder(binding, replyClickListener, actionListener, replyTargetClickListener);
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
        private final ItemRepliesBinding binding;
        private OnReplyClickListener replyClickListener;
        private OnReplyActionListener actionListener;
        private OnReplyTargetClickListener replyTargetClickListener;

        ViewHolder(ItemRepliesBinding binding, OnReplyClickListener replyClickListener,
                   OnReplyActionListener actionListener, OnReplyTargetClickListener replyTargetClickListener) {
            super(binding.getRoot());
            this.binding = binding;
            this.replyClickListener = replyClickListener;
            this.actionListener = actionListener;
            this.replyTargetClickListener = replyTargetClickListener;
        }

        void bind(Replies reply, boolean isLast, Markwon markwon, int parentCommentId) {
            if (reply == null) return;

            Context ctx = itemView.getContext();
            binding.replyAuthorNickname.setText(reply.getAuthorNickname());
            if (reply.getAuthorMemberLevel() > 0) {
                binding.replyAuthorNickname.setTextColor(
                        ContextCompat.getColor(ctx, R.color.colorPrimary));
            } else {
                binding.replyAuthorNickname.setTextColor(
                        ContextCompat.getColor(ctx, R.color.colorOnSecondaryContainer));
            }

            if (reply.getUserId() > 0) {
                View.OnClickListener toProfile = v -> {
                    Intent intent = new Intent(ctx, UserProfileActivity.class);
                    intent.putExtra(UserProfileActivity.EXTRA_USER_ID, reply.getUserId());
                    intent.putExtra(UserProfileActivity.EXTRA_USERNAME, reply.getAuthorNickname());
                    ctx.startActivity(intent);
                };
                binding.replyAuthorNickname.setOnClickListener(toProfile);
            }

            binding.replyAuthorLevelTitle.setText(reply.getAuthorUserLevelTitle());
            String content = reply.getContent();
            if (content != null && !content.isEmpty()) {
                markwon.setMarkdown(binding.replyContent, content);
                binding.replyContent.setVisibility(View.VISIBLE);
            } else {
                binding.replyContent.setText(null);
                binding.replyContent.setVisibility(View.GONE);
            }

            boolean isReplyToComment = parentCommentId > 0
                    && reply.getParentId() == parentCommentId;
            String prefixNickname = reply.getReplyToUserNickname();
            if (!isReplyToComment && prefixNickname != null && !prefixNickname.isEmpty()) {
                binding.replyTargetName.setText(prefixNickname);
                binding.replyPrefixText.setVisibility(View.VISIBLE);
                binding.replyTargetName.setVisibility(View.VISIBLE);
                binding.replyColon.setVisibility(View.VISIBLE);
                binding.replyTargetName.setOnClickListener(v -> {
                    if (replyTargetClickListener != null) {
                        replyTargetClickListener.onReplyTargetClick(reply);
                    }
                });
            } else {
                binding.replyPrefixText.setVisibility(View.GONE);
                binding.replyTargetName.setVisibility(View.GONE);
                binding.replyColon.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> {
                if (replyClickListener != null) {
                    replyClickListener.onReplyClick(reply);
                }
            });

            applyLikeState(reply);
            binding.replyLikeBtn.setOnClickListener(v -> {
                reply.setLiked(!reply.isLiked());
                reply.setLikeCount(reply.getLikeCount() + (reply.isLiked() ? 1 : -1));
                applyLikeState(reply);
                if (actionListener != null) {
                    actionListener.onReplyLike(reply);
                }
            });
        }

        private void applyLikeState(Replies reply) {
            binding.replyLikeCount.setText(String.valueOf(reply.getLikeCount()));
            binding.replyLikeBtn.setImageResource(reply.isLiked()
                    ? R.drawable.material_symbols_favorite_rounded
                    : R.drawable.material_symbols_favorite_outline_rounded);
            int color = reply.isLiked()
                    ? R.color.colorPrimary
                    : R.color.colorOnSecondaryContainer;
            ImageViewCompat.setImageTintList(binding.replyLikeBtn,
                    ColorStateList.valueOf(itemView.getContext().getColor(color)));
        }
    }
}