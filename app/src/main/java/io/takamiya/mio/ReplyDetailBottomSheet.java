package io.takamiya.mio;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.List;

import io.noties.markwon.Markwon;
import io.takamiya.mio.adapter.RepliesDetailAdapter;
import io.takamiya.mio.bean.Comments;
import io.takamiya.mio.bean.Replies;
import io.takamiya.mio.databinding.SheetReplyDetailBinding;
import io.takamiya.mio.utils.MarkdownProvider;
import io.takamiya.mio.utils.RelativeTimeUtil;

/**
 * 回复详情 BottomSheet 封装类
 * 展示一条评论的完整信息 + 该评论的全部回复，并支持点赞/回复/更多操作。
 */
public class ReplyDetailBottomSheet {

    public interface OnActionListener {
        void onCommentLike(Comments comment);

        void onCommentReply(Comments comment);

        void onCommentMore(Comments comment);

        void onReplyLike(Replies reply);

        void onReplyMore(Replies reply);

        void onReply(Replies reply);
    }

    private final Context context;
    private final BottomSheetDialog dialog;
    private final SheetReplyDetailBinding binding;
    private final Markwon markwon;
    private RepliesDetailAdapter repliesAdapter;
    private Comments currentComment;
    private OnActionListener listener;

    public ReplyDetailBottomSheet(@NonNull Context context) {
        this.context = context;
        binding = SheetReplyDetailBinding.inflate(LayoutInflater.from(context));
        dialog = new BottomSheetDialog(context);
        dialog.setContentView(binding.getRoot());
        markwon = MarkdownProvider.create(context);

        repliesAdapter = new RepliesDetailAdapter();
        binding.sheetRepliesRecyclerView.setLayoutManager(new LinearLayoutManager(context));
        binding.sheetRepliesRecyclerView.setNestedScrollingEnabled(false);
        binding.sheetRepliesRecyclerView.setAdapter(repliesAdapter);

        repliesAdapter.setOnReplyActionListener(new RepliesDetailAdapter.OnReplyActionListener() {
            @Override
            public void onReplyLike(Replies reply) {
                if (listener != null) {
                    listener.onReplyLike(reply);
                }
            }

            @Override
            public void onReplyMore(Replies reply) {
                if (listener != null) {
                    listener.onReplyMore(reply);
                }
            }
        });
        repliesAdapter.setOnReplyKeepClickListener(reply -> {
            if (listener != null) {
                listener.onReply(reply);
            }
        });
        repliesAdapter.setOnTargetClickListener(reply -> {
            if (listener != null) {
                listener.onReply(reply);
            }
        });

        initHeaderListeners();
    }

    public void setOnActionListener(OnActionListener listener) {
        this.listener = listener;
    }

    private void initHeaderListeners() {
        binding.sheetCommentLikeBtn.setOnClickListener(v -> {
            Comments c = currentComment;
            c.setLiked(!c.isLiked());
            c.setLikeCount(c.getLikeCount() + (c.isLiked() ? 1 : -1));
            applyCommentLikeState();
            if (listener != null) {
                listener.onCommentLike(c);
            }
        });
        binding.sheetCommentReplyBtn.setOnClickListener(v -> {
            if (listener != null && currentComment != null) {
                listener.onCommentReply(currentComment);
            }
        });
        binding.sheetCommentMoreBtn.setOnClickListener(v -> {
            if (listener != null && currentComment != null) {
                listener.onCommentMore(currentComment);
            }
        });
    }

    public void show(Comments comment) {
        if (comment == null) return;
        currentComment = comment;

        bindCommentHeader(comment);

        List<Replies> replies = comment.getRepliesList();
        repliesAdapter.setParentCommentId(comment.getId());
        repliesAdapter.setData(replies);
        if (replies != null && !replies.isEmpty()) {
            binding.sheetRepliesRecyclerView.setVisibility(View.VISIBLE);
        } else {
            binding.sheetRepliesRecyclerView.setVisibility(View.GONE);
        }

        dialog.show();
    }

    public void dismiss() {
        dialog.dismiss();
    }

    public boolean isShowing() {
        return dialog.isShowing();
    }

    private void bindCommentHeader(Comments comment) {
        Context ctx = context;
        Glide.with(ctx)
                .load(comment.getAuthorAvatar())
                .placeholder(io.takamiya.mio.R.mipmap.ic_launcher_round)
                .into(binding.sheetCommentAvatar);

        binding.sheetCommentNickname.setText(comment.getAuthorNickname());
        if (comment.getAuthorMemberLevel() > 0) {
            binding.sheetCommentNickname.setTextColor(
                    ContextCompat.getColor(ctx, io.takamiya.mio.R.color.colorPrimary));
        } else {
            binding.sheetCommentNickname.setTextColor(
                    ContextCompat.getColor(ctx, io.takamiya.mio.R.color.colorOnSecondaryContainer));
        }

        if (comment.getUserId() > 0) {
            View.OnClickListener toProfile = v -> {
                Intent intent = new Intent(ctx, UserProfileActivity.class);
                intent.putExtra(UserProfileActivity.EXTRA_USER_ID, comment.getUserId());
                intent.putExtra(UserProfileActivity.EXTRA_USERNAME, comment.getAuthorNickname());
                ctx.startActivity(intent);
            };
            binding.sheetCommentAvatar.setOnClickListener(toProfile);
            binding.sheetCommentNickname.setOnClickListener(toProfile);
        }

        binding.sheetCommentLevelTitle.setText(comment.getAuthorUserLevelTitle());
        String content = comment.getContent();
        if (content != null && !content.isEmpty()) {
            markwon.setMarkdown(binding.sheetCommentContent, content);
            binding.sheetCommentContent.setVisibility(View.VISIBLE);
        } else {
            binding.sheetCommentContent.setText(null);
            binding.sheetCommentContent.setVisibility(View.GONE);
        }
        binding.sheetCommentTime.setText(RelativeTimeUtil.toRelativeTime(comment.getCreatedAt()));
        applyCommentLikeState();
    }

    private void applyCommentLikeState() {
        Comments comment = currentComment;
        if (comment == null) return;
        binding.sheetCommentLikeCount.setText(String.valueOf(comment.getLikeCount()));
        binding.sheetCommentLikeBtn.setImageResource(comment.isLiked()
                ? io.takamiya.mio.R.drawable.material_symbols_favorite_rounded
                : io.takamiya.mio.R.drawable.material_symbols_favorite_outline_rounded);
        int color = comment.isLiked()
                ? io.takamiya.mio.R.color.colorPrimary
                : io.takamiya.mio.R.color.colorOnSecondaryContainer;
        ImageViewCompat.setImageTintList(binding.sheetCommentLikeBtn,
                ColorStateList.valueOf(context.getColor(color)));
    }
}