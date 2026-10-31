package io.takamiya.mio;
import io.takamiya.mio.utils.SecureStrings;

import android.content.Intent;
import android.os.Bundle;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.panda.utils.library.HttpUtils.java.HttpClientJava;
import cn.panda.utils.library.UrlBuilder;
import io.takamiya.mio.adapter.CommentsAdapter;
import io.takamiya.mio.adapter.RepliesAdapter;
import io.takamiya.mio.bean.Comments;
import io.takamiya.mio.bean.Replies;
import io.takamiya.mio.databinding.ActivityPostDetailBinding;
import io.takamiya.mio.layoutViewModel.PostDetailViewModel;
import io.takamiya.mio.utils.AppToken;
import io.takamiya.mio.utils.HttpManage;
import io.takamiya.mio.utils.MarkdownProvider;
import io.takamiya.mio.utils.ModerationUtil;
import io.takamiya.mio.utils.RelativeTimeUtil;
import io.takamiya.mio.utils.SectionModeratorUtil;
import io.takamiya.mio.utils.UserToken;

public class PostDetailActivity extends AppCompatActivity {

    private HttpClientJava client = HttpManage.getInstance();
    private int POST_ID;
    private PostDetailViewModel viewModel;
    private ActivityPostDetailBinding binding;
    private final List<Comments> commentsList = new ArrayList<>();
    private CommentsAdapter commentsAdapter;
    private CommentEditBottomSheet commentSheet;
    private CommentActionSheet actionSheet;
    private ReplyDetailBottomSheet replyDetailSheet;
    private CoinSheet coinSheet;
    private int actionTargetId;
    private String actionTargetContent;
    private boolean postLiked;
    private boolean postFavorited;
    private int postAuthorId;
    private int postCoinCount;
    private int sectionId;
    private Boolean sectionModeratorCache;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 传递帖子id
        POST_ID = getIntent().getIntExtra("POST_ID", 0);

        ActivityPostDetailBinding binding = ActivityPostDetailBinding.inflate(getLayoutInflater());
        this.binding = binding;
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets(binding);
        viewModel = new ViewModelProvider(this).get(PostDetailViewModel.class);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        commentsAdapter = new CommentsAdapter();
        binding.commentsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.commentsRecyclerView.setNestedScrollingEnabled(false);
        binding.commentsRecyclerView.setAdapter(commentsAdapter);

        initCommentSheet();
        initCommentActions();
        initPostActions();
        binding.writeCommentFab.setOnClickListener(v -> {
            commentSheet.setReplyTo(0, null);
            commentSheet.show();
        });

        loadPostDetails();
        initView(binding);
    }

    private void initCommentSheet() {
        commentSheet = new CommentEditBottomSheet(this);
        commentSheet.setTargetType(1);
        commentSheet.setTargetId(POST_ID);
        commentSheet.setOnSendListener((content, sheet) -> sheet.submitToApi(content));
        commentSheet.setOnResultListener(new CommentEditBottomSheet.OnResultListener() {
            @Override
            public void onSuccess(String msg) {
                if (replyDetailSheet != null && replyDetailSheet.isShowing()) {
                    replyDetailSheet.dismiss();
                }
                loadComments();
            }

            @Override
            public void onFailure(String err) {
            }
        });

        commentsAdapter.setOnReplyClickListener((commentId, nickname) -> {
            commentSheet.setReplyTo(commentId, nickname);
            commentSheet.show();
        });

        commentsAdapter.setOnReplyItemClickListener(comment ->
                showReplyDetailSheet(comment));

        replyDetailSheet = new ReplyDetailBottomSheet(this);
        replyDetailSheet.setOnActionListener(new ReplyDetailBottomSheet.OnActionListener() {
            @Override
            public void onCommentLike(Comments comment) {
                toggleLike(comment.getId(), comment.isLiked());
            }

            @Override
            public void onCommentReply(Comments comment) {
                commentSheet.setReplyTo(comment.getId(), comment.getAuthorNickname());
                commentSheet.show();
            }

            @Override
            public void onCommentMore(Comments comment) {
                if (!UserToken.isLoggedIn()) {
                    Toast.makeText(PostDetailActivity.this, "请先登录", Toast.LENGTH_SHORT).show();
                    return;
                }
                actionTargetId = comment.getId();
                actionTargetContent = comment.getContent();
                boolean canDelete = comment.getUserId() > 0 && comment.getUserId() == UserToken.getSub();
                actionSheet.show("评论操作", canDelete, "删除评论", "举报评论");
            }

            @Override
            public void onReplyLike(Replies reply) {
                toggleLike(reply.getId(), reply.isLiked());
            }

            @Override
            public void onReplyMore(Replies reply) {
                if (!UserToken.isLoggedIn()) {
                    Toast.makeText(PostDetailActivity.this, "请先登录", Toast.LENGTH_SHORT).show();
                    return;
                }
                actionTargetId = reply.getId();
                actionTargetContent = reply.getContent();
                boolean canDelete = reply.getUserId() > 0 && reply.getUserId() == UserToken.getSub();
                actionSheet.show("评论操作", canDelete, "删除回复", "举报回复");
            }

            @Override
            public void onReply(Replies reply) {
                commentSheet.setReplyTo(reply.getId(), reply.getAuthorNickname());
                commentSheet.show();
            }
        });
    }

    private void showReplyDetailSheet(Comments comment) {
        replyDetailSheet.show(comment);
    }

    private void initCommentActions() {
        actionSheet = new CommentActionSheet(this);
        actionSheet.setOnActionListener(new CommentActionSheet.OnActionListener() {
            @Override
            public void onDelete() {
                confirmDeleteComment();
            }

            @Override
            public void onReport() {
                openReportPage();
            }
        });

        commentsAdapter.setOnCommentActionListener(new CommentsAdapter.OnCommentActionListener() {
            @Override
            public void onCommentLike(Comments comment) {
                toggleLike(comment.getId(), comment.isLiked());
            }

            @Override
            public void onCommentMore(Comments comment) {
                if (!UserToken.isLoggedIn()) {
                    Toast.makeText(PostDetailActivity.this, "请先登录", Toast.LENGTH_SHORT).show();
                    return;
                }
                actionTargetId = comment.getId();
                actionTargetContent = comment.getContent();
                boolean canDelete = comment.getUserId() > 0 && comment.getUserId() == UserToken.getSub();
                actionSheet.show("评论操作", canDelete, "删除评论", "举报评论");
            }
        });

        commentsAdapter.setOnReplyActionListener(new RepliesAdapter.OnReplyActionListener() {
            @Override
            public void onReplyLike(Replies reply) {
                toggleLike(reply.getId(), reply.isLiked());
            }

            @Override
            public void onReplyMore(Replies reply) {
                if (!UserToken.isLoggedIn()) {
                    Toast.makeText(PostDetailActivity.this, "请先登录", Toast.LENGTH_SHORT).show();
                    return;
                }
                actionTargetId = reply.getId();
                actionTargetContent = reply.getContent();
                boolean canDelete = reply.getUserId() > 0 && reply.getUserId() == UserToken.getSub();
                actionSheet.show("评论操作", canDelete, "删除回复", "举报回复");
            }
        });

        commentsAdapter.setOnReplyTargetClickListener(reply ->
                jumpToComment(reply.getParentId()));
    }

    private void toggleLike(int commentId, boolean like) {
        if (!UserToken.isLoggedIn()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            loadComments();
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("target_type", 3);
        body.put("target_id", commentId);
        String url = UrlBuilder.getInstance()
                .longPath(SecureStrings.path(52))
                .param("action", like ? "like" : "unlike")
                .param(SecureStrings.path(95), AppToken.getToken())
                .build();
        if (like) {
            HttpManage.getInstance().post(url)
                    .header(SecureStrings.path(96), UserToken.getToken())
                    .json(body)
                    .onFailure((code, err) -> runOnUiThread(() -> {
                        Toast.makeText(this, "点赞失败", Toast.LENGTH_SHORT).show();
                        loadComments();
                    }))
                    .execute();
        } else {
            HttpManage.getInstance().delete(url)
                    .header(SecureStrings.path(96), UserToken.getToken())
                    .json(body)
                    .onFailure((code, err) -> runOnUiThread(() -> {
                        Toast.makeText(this, "取消点赞失败", Toast.LENGTH_SHORT).show();
                        loadComments();
                    }))
                    .execute();
        }
    }

    private void initPostActions() {
        coinSheet = new CoinSheet(this);
        coinSheet.setOnCoinListener(count -> submitCoin(count));

        binding.btnLike.setOnClickListener(v -> togglePostLike());
        binding.btnFavorite.setOnClickListener(v -> toggleFavorite());
        binding.btnCoins.setOnClickListener(v -> openCoinSheet());
        binding.btnReport.setOnClickListener(v -> openPostActionSheet());
    }

    private void openPostActionSheet() {
        if (!UserToken.isLoggedIn()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        if (sectionModeratorCache != null) {
            showPostActionSheet(sectionModeratorCache);
            return;
        }
        SectionModeratorUtil.check(sectionId, result -> runOnUiThread(() -> {
            sectionModeratorCache = result;
            showPostActionSheet(result);
        }));
    }

    private void showPostActionSheet(boolean isModerator) {
        boolean isAuthor = postAuthorId > 0 && postAuthorId == UserToken.getSub();
        String title = viewModel.getTitle().getValue();
        boolean isTop = Boolean.TRUE.equals(viewModel.getIsTop().getValue());
        boolean isRecommend = Boolean.TRUE.equals(viewModel.getIsRecommend().getValue());
        ForumItemActions.show(this, "帖子", title, isAuthor, isModerator, isAuthor,
                isTop, isRecommend, new ForumItemActions.Callbacks() {
                    @Override
                    public void onEdit() {
                        Toast.makeText(PostDetailActivity.this, "修改帖子功能未开放，敬请期待", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onDelete() {
                        confirmDeletePost();
                    }

                    @Override
                    public void onReport() {
                        openPostReportPage();
                    }

                    @Override
                    public void onSetTop(boolean top) {
                        setPostTop(top);
                    }

                    @Override
                    public void onSetRecommend(boolean recommend) {
                        setPostRecommend(recommend);
                    }
                });
    }

    private void setPostTop(boolean top) {
        ModerationUtil.setPostTop(POST_ID, top, (ok, msg) -> runOnUiThread(() -> {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            if (ok) viewModel.setIsTop(top);
        }));
    }

    private void setPostRecommend(boolean recommend) {
        ModerationUtil.setPostRecommend(POST_ID, recommend, (ok, msg) -> runOnUiThread(() -> {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            if (ok) viewModel.setIsRecommend(recommend);
        }));
    }

    private void confirmDeletePost() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("删除帖子")
                .setMessage("确定删除这篇帖子吗？")
                .setPositiveButton("删除", (d, w) -> deletePost())
                .setNegativeButton("取消", null)
                .show();
    }

    private void deletePost() {
        Map<String, Object> body = new HashMap<>();
        body.put("id", POST_ID);
        String url = UrlBuilder.getInstance()
                .longPath(SecureStrings.path(50))
                .param("action", "delete")
                .param(SecureStrings.path(95), AppToken.getToken())
                .build();
        HttpManage.getInstance().delete(url)
                .header(SecureStrings.path(96), UserToken.getToken())
                .json(body)
                .onSuccess((code, resp) -> {
                    try {
                        JSONObject respJson = new JSONObject(resp.trim());
                        if (code == 200 && respJson.getInt("code") == 200) {
                            runOnUiThread(() -> {
                                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                                finish();
                            });
                        } else {
                            String msg = respJson.optString("msg", "删除失败");
                            runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
                        }
                    } catch (JSONException e) {
                        runOnUiThread(() -> Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show());
                    }
                })
                .onFailure((code, err) -> runOnUiThread(() ->
                        Toast.makeText(this, "网络异常：" + err, Toast.LENGTH_SHORT).show()))
                .execute();
    }

    private void togglePostLike() {
        if (!UserToken.isLoggedIn()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean likeNow = !postLiked;
        Map<String, Object> body = new HashMap<>();
        body.put("target_type", 1);
        body.put("target_id", POST_ID);

        applyLikeState(likeNow);
        adjustLikeCount(likeNow ? 1 : -1);
        sendStatusChange(SecureStrings.path(97), likeNow ? "like" : "unlike", body, likeNow,
                "点赞失败", "取消点赞失败", () -> {
                    applyLikeState(!likeNow);
                    adjustLikeCount(likeNow ? -1 : 1);
                });
    }

    private void adjustLikeCount(int delta) {
        viewModel.addToLike(delta);
    }

    private void applyLikeState(boolean liked) {
        if (binding == null) return;
        postLiked = liked;
        binding.btnLike.setIconResource(liked
                ? R.drawable.material_symbols_favorite_rounded
                : R.drawable.material_symbols_favorite_outline_rounded);
        binding.btnLike.setIconTint(ColorStateList.valueOf(
                ContextCompat.getColor(this, liked ? R.color.colorPrimary : R.color.colorSecondary)));
        binding.like.setTextColor(ContextCompat.getColor(this,
                liked ? R.color.colorPrimary : R.color.colorSecondary));
    }

    private void toggleFavorite() {
        if (!UserToken.isLoggedIn()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean favoriteNow = !postFavorited;
        Map<String, Object> body = new HashMap<>();
        body.put("target_type", 1);
        body.put("target_id", POST_ID);

        applyFavoriteState(favoriteNow);
        adjustFavoriteCount(favoriteNow ? 1 : -1);
        sendStatusChange(SecureStrings.path(98), favoriteNow ? "favorite" : "unfavorite", body, favoriteNow,
                "收藏失败", "取消收藏失败", () -> {
                    applyFavoriteState(!favoriteNow);
                    adjustFavoriteCount(favoriteNow ? -1 : 1);
                });
    }

    private void adjustFavoriteCount(int delta) {
        viewModel.addToFavorites(delta);
    }

    private void applyFavoriteState(boolean favorited) {
        if (binding == null) return;
        postFavorited = favorited;
        binding.btnFavorite.setIconResource(favorited
                ? R.drawable.material_symbols_kid_star
                : R.drawable.material_symbols_kid_star_outline);
        binding.btnFavorite.setIconTint(ColorStateList.valueOf(
                ContextCompat.getColor(this, favorited ? R.color.colorPrimary : R.color.colorSecondary)));
        binding.favorites.setTextColor(ContextCompat.getColor(this,
                favorited ? R.color.colorPrimary : R.color.colorSecondary));
    }

    private void applyCoinState() {
        if (binding == null) return;
        boolean coined = postCoinCount > 0;
        binding.btnCoins.setIconResource(coined
                ? R.drawable.material_symbols_paid_rounded
                : R.drawable.material_symbols_paid_outline_rounded);
        binding.btnCoins.setIconTint(ColorStateList.valueOf(
                ContextCompat.getColor(this, coined ? R.color.colorPrimary : R.color.colorSecondary)));
        binding.coins.setTextColor(ContextCompat.getColor(this,
                coined ? R.color.colorPrimary : R.color.colorSecondary));
    }

    private void jumpToComment(int commentId) {
        if (commentId <= 0) return;
        for (int i = 0; i < commentsList.size(); i++) {
            if (commentsList.get(i).getId() == commentId) {
                int pos = i;
                binding.commentsRecyclerView.scrollToPosition(pos);
                return;
            }
        }
    }

    private void sendStatusChange(String endpoint, String action, Map<String, Object> body, boolean post,
                                  String postFailMsg, String deleteFailMsg, Runnable revert) {
        String url = UrlBuilder.getInstance()
                .longPath("user-api/bbs-api/" + endpoint)
                .param("action", action)
                .param(SecureStrings.path(95), AppToken.getToken())
                .build();
        String msg = post ? postFailMsg : deleteFailMsg;
        if (post) {
            HttpManage.getInstance().post(url)
                    .header(SecureStrings.path(96), UserToken.getToken())
                    .json(body)
                    .onSuccess((code, resp) -> applyResponse(code, resp, msg, revert))
                    .onFailure((code, err) -> runOnUiThread(() -> {
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                        if (revert != null) revert.run();
                    }))
                    .execute();
        } else {
            HttpManage.getInstance().delete(url)
                    .header(SecureStrings.path(96), UserToken.getToken())
                    .json(body)
                    .onSuccess((code, resp) -> applyResponse(code, resp, msg, revert))
                    .onFailure((code, err) -> runOnUiThread(() -> {
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                        if (revert != null) revert.run();
                    }))
                    .execute();
        }
    }

    private void applyResponse(int code, String resp, String failMsg, Runnable revert) {
        runOnUiThread(() -> {
            try {
                JSONObject json = new JSONObject(resp.trim());
                if (code == 200 && json.getInt("code") == 200) {
                    Toast.makeText(this, json.optString("msg", "成功"), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, json.optString("msg", failMsg), Toast.LENGTH_SHORT).show();
                    if (revert != null) revert.run();
                }
            } catch (JSONException e) {
                Toast.makeText(this, failMsg, Toast.LENGTH_SHORT).show();
                if (revert != null) revert.run();
            }
        });
    }

    private void openCoinSheet() {
        if (!UserToken.isLoggedIn()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        if (postAuthorId > 0 && postAuthorId == UserToken.getSub()) {
            Toast.makeText(this, "不能给自己的帖子投币", Toast.LENGTH_SHORT).show();
            return;
        }
        int remaining = 2 - postCoinCount;
        if (remaining <= 0) {
            Toast.makeText(this, "已投满 2 枚，不能继续投币", Toast.LENGTH_SHORT).show();
            return;
        }
        coinSheet.show(remaining);
    }

    private void submitCoin(int count) {
        Map<String, Object> body = new HashMap<>();
        body.put("target_type", 1);
        body.put("target_id", POST_ID);
        body.put("coin_num", count);

        String url = UrlBuilder.getInstance()
                .longPath(SecureStrings.path(54))
                .param("action", "coin")
                .param(SecureStrings.path(95), AppToken.getToken())
                .build();
        HttpManage.getInstance().post(url)
                .header(SecureStrings.path(96), UserToken.getToken())
                .json(body)
                .onSuccess((code, resp) -> runOnUiThread(() -> {
                    try {
                        JSONObject json = new JSONObject(resp.trim());
                        if (code == 200 && json.getInt("code") == 200) {
                            Toast.makeText(this, json.optString("msg", "投币成功"), Toast.LENGTH_SHORT).show();
                            viewModel.addToCoins(count);
                            postCoinCount += count;
                            applyCoinState();
                            double remainingGold = json.optJSONObject("data") == null
                                    ? 0 : json.optJSONObject("data").optDouble("remaining_gold", 0);
                            if (remainingGold > 0) {
                                Toast.makeText(this, String.format("剩余金币:%.2f", remainingGold), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(this, json.optString("msg", "投币失败"), Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(this, "投币失败", Toast.LENGTH_SHORT).show();
                    }
                }))
                .onFailure((code, err) -> runOnUiThread(() ->
                        Toast.makeText(this, "网络异常：" + err, Toast.LENGTH_SHORT).show()))
                .execute();
    }

    private void confirmDeleteComment() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("删除评论")
                .setMessage("确定删除这条评论吗？")
                .setPositiveButton("删除", (d, w) -> deleteComment(actionTargetId))
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteComment(int commentId) {
        Map<String, Object> body = new HashMap<>();
        body.put("id", commentId);
        String url = UrlBuilder.getInstance()
                .longPath(SecureStrings.path(55))
                .param("action", "delete")
                .param(SecureStrings.path(95), AppToken.getToken())
                .build();
        HttpManage.getInstance().delete(url)
                .header(SecureStrings.path(96), UserToken.getToken())
                .json(body)
                .onSuccess((code, resp) -> {
                    try {
                        JSONObject respJson = new JSONObject(resp);
                        if (code == 200 && respJson.getInt("code") == 200) {
                            runOnUiThread(() -> {
                                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                                if (replyDetailSheet != null && replyDetailSheet.isShowing()) {
                                    replyDetailSheet.dismiss();
                                }
                                loadComments();
                            });
                        } else {
                            String msg = respJson.optString("msg", "删除失败");
                            runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
                        }
                    } catch (JSONException e) {
                        runOnUiThread(() -> Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show());
                    }
                })
                .onFailure((code, err) -> runOnUiThread(() ->
                        Toast.makeText(this, "网络异常：" + err, Toast.LENGTH_SHORT).show()))
                .execute();
    }

    private void openReportPage() {
        Intent intent = new Intent(this, ReportActivity.class);
        intent.putExtra("target_type", 3);
        intent.putExtra("target_id", actionTargetId);
        intent.putExtra("target_content", actionTargetContent == null ? "" : actionTargetContent);
        startActivity(intent);
    }

    private void openPostReportPage() {
        if (!UserToken.isLoggedIn()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        if (postAuthorId > 0 && postAuthorId == UserToken.getSub()) {
            Toast.makeText(this, "不能举报自己的帖子", Toast.LENGTH_SHORT).show();
            return;
        }
        String title = viewModel.getTitle().getValue();
        Intent intent = new Intent(this, ReportActivity.class);
        intent.putExtra("target_type", 1);
        intent.putExtra("target_id", POST_ID);
        intent.putExtra("target_content", title == null ? "" : title);
        startActivity(intent);
    }

    private void initView(ActivityPostDetailBinding binding) {
        viewModel.getAuthorNickname().observe(this, binding.authorNickname::setText);
        viewModel.getAuthorMemberLevel().observe(this, value -> {
            if (value > 0) {
                binding.authorNickname.setTextColor(ContextCompat.getColor(this, R.color.colorPrimary));
            } else {
                binding.authorNickname.setTextColor(ContextCompat.getColor(this, R.color.colorOnSecondaryContainer));
            }
        });
        viewModel.getAuthorAvatar().observe(this, value -> {
            Glide.with(this)
                    .load(value)
                    .into(binding.authorAvatar);
        });
        viewModel.getAuthorUserLevelTitle().observe(this, binding.authorUserLevelTitle::setText);
        viewModel.getCreatedAt().observe(this, value -> {
            binding.createdAt.setText(RelativeTimeUtil.toRelativeTime(value));
        });
        viewModel.getContent().observe(this, value -> {
            MarkdownProvider.create(this).setMarkdown(binding.postContent, value);
        });
        viewModel.getLike().observe(this, value -> binding.like.setText(String.valueOf(value)));
        viewModel.getCoins().observe(this, value -> binding.coins.setText(String.valueOf(value)));
        viewModel.getFavorites()
                .observe(this, value -> binding.favorites.setText(String.valueOf(value)));

        View.OnClickListener toProfile = v -> {
            if (postAuthorId > 0) {
                String nickname = viewModel.getAuthorNickname().getValue();
                Intent intent = new Intent(this, UserProfileActivity.class);
                intent.putExtra(UserProfileActivity.EXTRA_USER_ID, postAuthorId);
                intent.putExtra(UserProfileActivity.EXTRA_USERNAME, nickname != null ? nickname : "");
                startActivity(intent);
            }
        };
        binding.authorAvatar.setOnClickListener(toProfile);
        binding.authorNickname.setOnClickListener(toProfile);
    }

    private void loadPostDetails() {
        client.get(UrlBuilder.getInstance().longPath(SecureStrings.path(50)).build())
                .header(SecureStrings.path(96), UserToken.getToken())
                .query("action", "detail")
                .query(SecureStrings.path(95), AppToken.getToken())
                .query("id", String.valueOf(POST_ID))
                .onSuccess((code, body) -> {
                    try {
                        JSONObject jsonObjectBody = new JSONObject(body.trim());
                        if (jsonObjectBody.getInt("code") != 200) {
                            // TODO 错误提示
                            String msg = jsonObjectBody.getString("msg");
                            return;
                        }

                        JSONObject data = jsonObjectBody.getJSONObject("data");
                        String authorName = data.getString("author_name");
                        String authorNickname = data.getString("author_nickname");
                        String sectionName = data.getString("section_name");
                        String title = data.getString("title");
                        String content = data.getString("content");
                        String authorAvatar = data.getString("author_avatar");
                        String authorUserLevelTitle = data.getString("author_user_level_title");
                        int views = data.getInt("views");
                        int likes = data.getInt("likes");
                        int favorites = data.getInt("favorites");
                        int coins = data.getInt("coins");
                        int authorMemberLevel = data.getInt("author_member_level");
                        boolean isTop = data.getInt("is_top") == 1;
                        boolean isRecommend = data.getInt("is_recommend") == 1;
                        int auditStatus = data.getInt("audit_status");
                        String createdAt = data.getString("created_at");
                        boolean isLiked = data.optBoolean("is_liked", false);
                        boolean isFavorited = data.optBoolean("is_favorited", false);
int userId = data.optInt("user_id", 0);
                            int userCoinCount = data.optInt("user_coin_count", 0);
                            int secId = data.optInt("section_id", 0);

                            runOnUiThread(() -> {
                                viewModel.loadData(authorName, authorNickname, sectionName, title, content, createdAt, views, likes, favorites, coins, auditStatus, isRecommend, isTop, authorAvatar, authorUserLevelTitle, authorMemberLevel);
                                postAuthorId = userId;
                                sectionId = secId;
                            applyLikeState(isLiked);
                            applyFavoriteState(isFavorited);
                            postCoinCount = userCoinCount;
                            applyCoinState();
                        });
                    } catch (JSONException e) {
                        // TODO json解析失败
                        throw new RuntimeException(e);
                    }
                })
                .onFailure((code, error) -> {

                })
                .execute();

        loadComments();
    }

    private void loadComments() {
        client.get(UrlBuilder.getInstance().longPath(SecureStrings.path(55)).build())
                .header(SecureStrings.path(96), UserToken.getToken())
                .query("action", "list")
                .query("target_type", String.valueOf(1))
                .query(SecureStrings.path(95), AppToken.getToken())
                .query("target_id", String.valueOf(POST_ID))
                .onSuccess((code, body) -> {
                    try {
                        JSONObject bodyJson = new JSONObject(body.trim());
                        JSONObject data = bodyJson.getJSONObject("data");

                        // 分页信息
                        data.getInt("total");
                        data.getInt("page");
                        data.getInt("page_size");
                        data.getInt("target_type");
                        data.getInt("target_id");

                        commentsList.clear();
                        JSONArray list = data.getJSONArray("list");
                        for (int i = 0; i < list.length(); i++) {
                            JSONObject info = list.getJSONObject(i);
                            int id = info.getInt("id");
                            int userId = info.optInt("user_id", 0);
                            String authorName = info.getString("author_name");
                            String authorNickname = info.getString("author_nickname");
                            String authorAvatar = info.getString("author_avatar");
                            int authorMemberLevel = info.getInt("author_member_level");
                            String authorMemberLevelName = info.getString("author_member_level_name");
                            int authorUserLevel = info.getInt("author_user_level");
                            String authorUserLevelTitle = info.getString("author_user_level_title");
                            String content = info.getString("content");
                            int likeCount = info.getInt("like_count");
                            boolean likeStatus = info.optBoolean("is_liked", false);
                            String createdAt = info.getString("created_at");
                            List<Replies> repliesList = new ArrayList<>();
                            JSONArray replies = info.getJSONArray("replies");
                            for (int j = 0; j < replies.length(); j++) {
                                JSONObject replie = replies.getJSONObject(j);
                                int repliesId = replie.getInt("id");
                                int repliesUserId = replie.optInt("user_id", 0);
                                String repliesAuthorName = replie.getString("author_name");
                                String repliesAuthorNickname = replie.getString("author_nickname");
                                String repliesAuthorAvatar = replie.getString("author_avatar");
                                int repliesAuthorMemberLevel = replie.getInt("author_member_level");
                                String repliesAuthorMemberLevelName = replie.getString("author_member_level_name");
                                int repliesAuthorUserLevel = replie.getInt("author_user_level");
                                String repliesAuthorUserLevelTitle = replie.getString("author_user_level_title");
                                String repliesContent = replie.getString("content");
                                int repliesLikeCount = replie.getInt("like_count");
                                boolean repliesLikeStatus = replie.optBoolean("is_liked", false);
                                String repliesCreatedAt = replie.getString("created_at");
                                String repliesReplyToUserNickname = replie.optString("reply_to_user_nickname", "");
                                int repliesParentId = replie.optInt("parent_id", 0);

                                Replies replyItem = new Replies(repliesId,repliesAuthorName, repliesAuthorNickname,repliesAuthorAvatar, repliesAuthorMemberLevel, repliesAuthorUserLevel, repliesLikeCount, repliesAuthorUserLevelTitle, repliesContent, repliesCreatedAt, repliesReplyToUserNickname);
                                replyItem.setUserId(repliesUserId);
                                replyItem.setLiked(repliesLikeStatus);
                                replyItem.setParentId(repliesParentId);
                                repliesList.add(replyItem);
                            }
                            int replyCount = info.optInt("reply_count", repliesList.size());
                            Comments commentItem = new Comments(id, authorName, authorNickname, authorAvatar, authorMemberLevel, authorUserLevel, likeCount, authorUserLevelTitle, content, createdAt, repliesList, replyCount);
                            commentItem.setUserId(userId);
                            commentItem.setLiked(likeStatus);
                            commentsList.add(commentItem);
                        }
                        runOnUiThread(() -> commentsAdapter.setData(commentsList));
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                })
                .onFailure((code, body) -> {

                }).execute();

    }

    private void applyWindowInsets(ActivityPostDetailBinding binding) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.toolbar.setPadding(
                    binding.toolbar.getPaddingLeft(),
                    bars.top,
                    binding.toolbar.getPaddingRight(),
                    binding.toolbar.getPaddingBottom());
            binding.content.setPadding(
                    binding.content.getPaddingLeft(),
                    binding.content.getPaddingTop(),
                    binding.content.getPaddingRight(),
                    bars.bottom);
            return insets;
        });
    }
}
