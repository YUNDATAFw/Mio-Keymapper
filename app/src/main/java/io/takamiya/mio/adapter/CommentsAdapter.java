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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

import io.noties.markwon.Markwon;
import io.takamiya.mio.R;
import io.takamiya.mio.UserProfileActivity;
import io.takamiya.mio.bean.Comments;
import io.takamiya.mio.bean.Replies;
import io.takamiya.mio.databinding.ItemCommentsBinding;
import io.takamiya.mio.utils.MarkdownProvider;
import io.takamiya.mio.utils.RelativeTimeUtil;

public class CommentsAdapter extends RecyclerView.Adapter<CommentsAdapter.ViewHolder> {

    public interface OnReplyClickListener {
        void onReplyClick(int commentId, String replyTargetNickname);
    }

    public interface OnReplyItemClickListener {
        void onReplyItemClick(Comments comment);
    }

    public interface OnCommentActionListener {
        void onCommentLike(Comments comment);

        void onCommentMore(Comments comment);
    }

    private static final int MAX_REPLIES_SHOWN = 3;

    private final List<Comments> list = new ArrayList<>();
    private Markwon markwon;
    private OnReplyClickListener replyClickListener;
    private OnReplyItemClickListener replyItemClickListener;
    private OnCommentActionListener commentActionListener;
    private RepliesAdapter.OnReplyActionListener replyActionListener;
    private RepliesAdapter.OnReplyTargetClickListener replyTargetClickListener;

    public void setData(List<Comments> incoming) {
        list.clear();
        if (incoming != null) {
            list.addAll(incoming);
        }
        notifyDataSetChanged();
    }

    public void setOnReplyClickListener(OnReplyClickListener listener) {
        this.replyClickListener = listener;
    }

    public void setOnReplyItemClickListener(OnReplyItemClickListener listener) {
        this.replyItemClickListener = listener;
    }

    public void setOnCommentActionListener(OnCommentActionListener listener) {
        this.commentActionListener = listener;
    }

    public void setOnReplyActionListener(RepliesAdapter.OnReplyActionListener listener) {
        this.replyActionListener = listener;
    }

    public void setOnReplyTargetClickListener(RepliesAdapter.OnReplyTargetClickListener listener) {
        this.replyTargetClickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCommentsBinding binding = ItemCommentsBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        if (markwon == null) {
            markwon = MarkdownProvider.create(parent.getContext());
        }
        return new ViewHolder(binding, replyClickListener, replyItemClickListener,
                commentActionListener, replyActionListener, replyTargetClickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(list.get(position), position == getItemCount() - 1, markwon);
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemCommentsBinding binding;
        private final RepliesAdapter repliesAdapter = new RepliesAdapter();
        private Comments currentComment;
        private OnReplyClickListener replyClickListener;
        private OnReplyItemClickListener replyItemClickListener;
        private OnCommentActionListener commentActionListener;

        ViewHolder(ItemCommentsBinding binding, OnReplyClickListener replyClickListener,
                   OnReplyItemClickListener replyItemClickListener,
                   OnCommentActionListener commentActionListener,
                   RepliesAdapter.OnReplyActionListener replyActionListener,
                   RepliesAdapter.OnReplyTargetClickListener replyTargetClickListener) {
            super(binding.getRoot());
            this.binding = binding;
            this.replyClickListener = replyClickListener;
            this.replyItemClickListener = replyItemClickListener;
            this.commentActionListener = commentActionListener;

            binding.repliesRecyclerView.setLayoutManager(new LinearLayoutManager(itemView.getContext()));
            binding.repliesRecyclerView.setNestedScrollingEnabled(false);
            binding.repliesRecyclerView.setAdapter(repliesAdapter);
            repliesAdapter.setOnReplyActionListener(replyActionListener);
            repliesAdapter.setOnReplyTargetClickListener(replyTargetClickListener);

            binding.replyToggle.setOnClickListener(v ->
                    notifyReplyItemClick(currentComment));

            binding.commentReplyBtn.setOnClickListener(v -> notifyReply(currentComment.getId(), currentComment.getAuthorNickname()));

            repliesAdapter.setOnReplyClickListener(reply ->
                    notifyReplyItemClick(currentComment));

            binding.commentLikeBtn.setOnClickListener(v -> {
                Comments c = currentComment;
                c.setLiked(!c.isLiked());
                c.setLikeCount(c.getLikeCount() + (c.isLiked() ? 1 : -1));
                applyLikeState(c);
                if (commentActionListener != null) {
                    commentActionListener.onCommentLike(c);
                }
            });
            binding.commentMoreBtn.setOnClickListener(v -> {
                if (commentActionListener != null) {
                    commentActionListener.onCommentMore(currentComment);
                }
            });
        }

        void bind(Comments comment, boolean isLast, Markwon markwon) {
            if (comment == null) return;

            currentComment = comment;

            Context ctx = itemView.getContext();
            Glide.with(ctx)
                    .load(comment.getAuthorAvatar())
                    .placeholder(R.mipmap.ic_launcher_round)
                    .into(binding.commentAuthorAvatar);
            binding.commentAuthorNickname.setText(comment.getAuthorNickname());
            if (comment.getAuthorMemberLevel() > 0) {
                binding.commentAuthorNickname.setTextColor(
                        ContextCompat.getColor(ctx, R.color.colorPrimary));
            } else {
                binding.commentAuthorNickname.setTextColor(
                        ContextCompat.getColor(ctx, R.color.colorOnSecondaryContainer));
            }

            if (comment.getUserId() > 0) {
                View.OnClickListener toProfile = v -> {
                    Intent intent = new Intent(ctx, UserProfileActivity.class);
                    intent.putExtra(UserProfileActivity.EXTRA_USER_ID, comment.getUserId());
                    intent.putExtra(UserProfileActivity.EXTRA_USERNAME, comment.getAuthorNickname());
                    ctx.startActivity(intent);
                };
                binding.commentAuthorAvatar.setOnClickListener(toProfile);
                binding.commentAuthorNickname.setOnClickListener(toProfile);
            }

            binding.commentAuthorLevelTitle.setText(comment.getAuthorUserLevelTitle());
            String content = comment.getContent();
            if (content != null && !content.isEmpty()) {
                markwon.setMarkdown(binding.commentContent, content);
                binding.commentContent.setVisibility(View.VISIBLE);
            } else {
                binding.commentContent.setText(null);
                binding.commentContent.setVisibility(View.GONE);
            }
            binding.commentTime.setText(RelativeTimeUtil.toRelativeTime(comment.getCreatedAt()));

            binding.commentDivider.setVisibility(isLast ? View.GONE : View.VISIBLE);

            applyLikeState(comment);

            List<Replies> replies = comment.getRepliesList();
            int replyCount = comment.getReplyCount();
            boolean hasReplies = replies != null && !replies.isEmpty();
            if (replyCount > MAX_REPLIES_SHOWN) {
                binding.repliesCard.setVisibility(View.VISIBLE);
                binding.replyToggle.setVisibility(View.VISIBLE);
                binding.replyToggleText.setText("共" + replyCount + "条回复");
                binding.replyToggleArrow.setVisibility(View.GONE);
                binding.repliesRecyclerView.setVisibility(View.GONE);
            } else if (hasReplies) {
                binding.repliesCard.setVisibility(View.VISIBLE);
                binding.replyToggle.setVisibility(View.GONE);
                binding.repliesRecyclerView.setVisibility(View.VISIBLE);
                repliesAdapter.setParentCommentId(comment.getId());
                repliesAdapter.setData(replies);
            } else {
                binding.repliesCard.setVisibility(View.GONE);
            }
        }

        private void applyLikeState(Comments comment) {
            binding.commentLikeCount.setText(String.valueOf(comment.getLikeCount()));
            binding.commentLikeBtn.setImageResource(comment.isLiked()
                    ? R.drawable.material_symbols_favorite_rounded
                    : R.drawable.material_symbols_favorite_outline_rounded);
            int color = comment.isLiked()
                    ? R.color.colorPrimary
                    : R.color.colorOnSecondaryContainer;
            ImageViewCompat.setImageTintList(binding.commentLikeBtn,
                    ColorStateList.valueOf(itemView.getContext().getColor(color)));
        }

        private void notifyReply(int commentId, String nickname) {
            if (replyClickListener != null) {
                replyClickListener.onReplyClick(commentId, nickname);
            }
        }

        private void notifyReplyItemClick(Comments comment) {
            if (replyItemClickListener != null) {
                replyItemClickListener.onReplyItemClick(comment);
            }
        }
    }
}