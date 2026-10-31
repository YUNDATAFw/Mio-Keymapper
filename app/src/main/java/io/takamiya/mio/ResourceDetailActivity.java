package io.takamiya.mio;
import io.takamiya.mio.utils.SecureStrings;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
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

import cn.panda.utils.library.UrlBuilder;
import io.takamiya.mio.adapter.CommentsAdapter;
import io.takamiya.mio.adapter.RepliesAdapter;
import io.takamiya.mio.bean.Comments;
import io.takamiya.mio.bean.Replies;
import io.takamiya.mio.databinding.ActivityResourceDetailBinding;
import io.takamiya.mio.utils.AppToken;
import io.takamiya.mio.utils.HttpManage;
import io.takamiya.mio.utils.MarkdownProvider;
import io.takamiya.mio.utils.ModerationUtil;
import io.takamiya.mio.utils.RelativeTimeUtil;
import io.takamiya.mio.utils.SectionModeratorUtil;
import io.takamiya.mio.utils.DownloadManager;
import io.takamiya.mio.utils.UserToken;
import o.s.o.l;

public class ResourceDetailActivity extends AppCompatActivity {

    private static final int TARGET_TYPE = 2;

    private ActivityResourceDetailBinding binding;
    private int RESOURCE_ID;

    private final List<Comments> commentsList = new ArrayList<>();
    private CommentsAdapter commentsAdapter;
    private CommentEditBottomSheet commentSheet;
    private CommentActionSheet actionSheet;
    private ReplyDetailBottomSheet replyDetailSheet;
    private CoinSheet coinSheet;

    private int actionTargetId;
    private String actionTargetContent;

    private boolean resourceLiked;
    private boolean resourceFavorited;
    private int userCoinCount;
    private int resourceAuthorId;
    private int sectionId;
    private Boolean sectionModeratorCache;
    private boolean resourceIsPaid;
    private boolean resourceIsPurchased;
    private boolean resourceIsAuthor;
    private boolean resourceIsTop;
    private boolean resourceIsRecommend;
    private double resourcePrice;
    private int resourceResType;
    private String resourceResUrl = "";
    private String resourceResPwd = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        RESOURCE_ID = getIntent().getIntExtra("RESOURCE_ID", 0);

        binding = ActivityResourceDetailBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets(binding);

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        commentsAdapter = new CommentsAdapter();
        binding.commentsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.commentsRecyclerView.setNestedScrollingEnabled(false);
        binding.commentsRecyclerView.setAdapter(commentsAdapter);

        initCommentSheet();
        initCommentActions();
        initInteractions();

        binding.btnPurchase.setOnClickListener(v -> onPurchaseClick());

        loadResourceDetail();
    }

    private void initCommentSheet() {
        commentSheet = new CommentEditBottomSheet(this);
        commentSheet.setTargetType(TARGET_TYPE);
        commentSheet.setTargetId(RESOURCE_ID);
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
                toggleCommentLike(comment.getId(), comment.isLiked());
            }

            @Override
            public void onCommentReply(Comments comment) {
                commentSheet.setReplyTo(comment.getId(), comment.getAuthorNickname());
                commentSheet.show();
            }

            @Override
            public void onCommentMore(Comments comment) {
                if (!UserToken.isLoggedIn()) {
                    Toast.makeText(ResourceDetailActivity.this, "请先登录", Toast.LENGTH_SHORT).show();
                    return;
                }
                actionTargetId = comment.getId();
                actionTargetContent = comment.getContent();
                boolean canDelete = comment.getUserId() > 0 && comment.getUserId() == UserToken.getSub();
                actionSheet.show("评论操作", canDelete, "删除评论", "举报评论");
            }

            @Override
            public void onReplyLike(Replies reply) {
                toggleCommentLike(reply.getId(), reply.isLiked());
            }

            @Override
            public void onReplyMore(Replies reply) {
                if (!UserToken.isLoggedIn()) {
                    Toast.makeText(ResourceDetailActivity.this, "请先登录", Toast.LENGTH_SHORT).show();
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

        binding.writeCommentFab.setOnClickListener(v -> {
            commentSheet.setReplyTo(0, null);
            commentSheet.show();
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
                openCommentReportPage();
            }
        });

        commentsAdapter.setOnCommentActionListener(new CommentsAdapter.OnCommentActionListener() {
            @Override
            public void onCommentLike(Comments comment) {
                toggleCommentLike(comment.getId(), comment.isLiked());
            }

            @Override
            public void onCommentMore(Comments comment) {
                if (!UserToken.isLoggedIn()) {
                    Toast.makeText(ResourceDetailActivity.this, "请先登录", Toast.LENGTH_SHORT).show();
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
                toggleCommentLike(reply.getId(), reply.isLiked());
            }

            @Override
            public void onReplyMore(Replies reply) {
                if (!UserToken.isLoggedIn()) {
                    Toast.makeText(ResourceDetailActivity.this, "请先登录", Toast.LENGTH_SHORT).show();
                    return;
                }
                actionTargetId = reply.getId();
                actionTargetContent = reply.getContent();
                boolean canDelete = reply.getUserId() > 0 && reply.getUserId() == UserToken.getSub();
                actionSheet.show("评论操作", canDelete, "删除回复", "举报回复");
            }
        });

        commentsAdapter.setOnReplyTargetClickListener(reply -> jumpToComment(reply.getParentId()));
    }

    private void initInteractions() {
        coinSheet = new CoinSheet(this);
        coinSheet.setOnCoinListener(count -> submitCoin(count));

        binding.btnLike.setOnClickListener(v -> toggleResourceLike());
        binding.btnFavorite.setOnClickListener(v -> toggleResourceFavorite());
        binding.btnCoins.setOnClickListener(v -> openCoinSheet());
        binding.btnReport.setOnClickListener(v -> onReportClick());
    }

    private void onPurchaseClick() {
        if (resourceIsPaid && !resourceIsPurchased && !resourceIsAuthor) {
            if (!UserToken.isLoggedIn()) {
                Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
                return;
            }
            confirmPurchase();
            return;
        }
        openResourceLink();
    }

    private void openResourceLink() {
        if (resourceResUrl.isEmpty()) {
            Toast.makeText(this, "资源链接为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (resourceResType == 2) {
            // 网盘资源：浏览器打开 + 复制提取码
            try {
                Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(resourceResUrl));
                startActivity(browser);
            } catch (Exception e) {
                Toast.makeText(this, "无法打开链接", Toast.LENGTH_SHORT).show();
            }
            if (!resourceResPwd.isEmpty()) {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("extract_code", resourceResPwd));
                    Toast.makeText(this, "提取码已复制：" + resourceResPwd, Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            // 直链：加入下载队列并跳转下载管理
            String fileName = resourceTitleFromUrl();
            DownloadManager.getInstance().addTask(resourceResUrl, fileName);
            Toast.makeText(this, "已开始下载", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, DownloadManagerActivity.class));
        }
    }

    private String resourceTitleFromUrl() {
        String url = resourceResUrl;
        int idx = url.lastIndexOf('/');
        if (idx >= 0 && idx < url.length() - 1) {
            String name = url.substring(idx + 1);
            int q = name.indexOf('?');
            if (q >= 0) name = name.substring(0, q);
            if (!name.isEmpty()) return name;
        }
        return binding.resourceTitle.getText() + ".dat";
    }

    private void confirmPurchase() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("购买资源")
                .setMessage(String.format("确认花费 %.2f 金币购买该资源？", resourcePrice))
                .setPositiveButton("购买", (d, w) -> purchaseResource())
                .setNegativeButton("取消", null)
                .show();
    }

    private void purchaseResource() {
        Map<String, Object> body = new HashMap<>();
        body.put("resource_id", RESOURCE_ID);
        String url = UrlBuilder.getInstance()
                .longPath(SecureStrings.path(51))
                .param("action", "purchase")
                .param(SecureStrings.path(95), AppToken.getToken())
                .build();
        HttpManage.getInstance().post(url)
                .header(SecureStrings.path(96), UserToken.getToken())
                .json(body)
                .onSuccess((code, resp) -> runOnUiThread(() -> {
                    try {
                        JSONObject json = new JSONObject(resp.trim());
                        if (code == 200 && json.getInt("code") == 200) {
                            Toast.makeText(this, json.optString("msg", "购买成功"), Toast.LENGTH_SHORT).show();
                            JSONObject data = json.optJSONObject("data");
                            double remainingGold = data == null ? 0 : data.optDouble("remaining_gold", 0);
                            if (remainingGold > 0) {
                                Toast.makeText(this, String.format("剩余金币:%.2f", remainingGold), Toast.LENGTH_SHORT).show();
                            }
                            loadResourceDetail();
                        } else {
                            Toast.makeText(this, json.optString("msg", "购买失败"), Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(this, "购买失败", Toast.LENGTH_SHORT).show();
                    }
                }))
                .onFailure((code, err) -> runOnUiThread(() ->
                        Toast.makeText(this, "网络异常：" + err, Toast.LENGTH_SHORT).show()))
                .execute();
    }

    private void onReportClick() {
        if (!UserToken.isLoggedIn()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        if (sectionModeratorCache != null) {
            showResourceActionSheet(sectionModeratorCache);
            return;
        }
        SectionModeratorUtil.check(sectionId, result -> runOnUiThread(() -> {
            sectionModeratorCache = result;
            showResourceActionSheet(result);
        }));
    }

    private void showResourceActionSheet(boolean isModerator) {
        String title = binding.resourceTitle.getText().toString();
        ForumItemActions.show(this, "资源", title, resourceIsAuthor, isModerator, false,
                resourceIsTop, resourceIsRecommend, new ForumItemActions.Callbacks() {
                    @Override
                    public void onEdit() {
                    }

                    @Override
                    public void onDelete() {
                        confirmDeleteResource();
                    }

                    @Override
                    public void onReport() {
                        openResourceReportPage();
                    }

                    @Override
                    public void onSetTop(boolean top) {
                        ModerationUtil.setResourceTop(RESOURCE_ID, top, (ok, msg) -> runOnUiThread(() -> {
                            Toast.makeText(ResourceDetailActivity.this, msg, Toast.LENGTH_SHORT).show();
                            if (ok) resourceIsTop = top;
                        }));
                    }

                    @Override
                    public void onSetRecommend(boolean recommend) {
                        ModerationUtil.setResourceRecommend(RESOURCE_ID, recommend, (ok, msg) -> runOnUiThread(() -> {
                            Toast.makeText(ResourceDetailActivity.this, msg, Toast.LENGTH_SHORT).show();
                            if (ok) resourceIsRecommend = recommend;
                        }));
                    }
                });
    }

    private void openResourceReportPage() {
        String title = binding.resourceTitle.getText().toString();
        Intent intent = new Intent(this, ReportActivity.class);
        intent.putExtra("target_type", TARGET_TYPE);
        intent.putExtra("target_id", RESOURCE_ID);
        intent.putExtra("target_content", title == null ? "" : title);
        startActivity(intent);
    }

    private void confirmDeleteResource() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("删除资源")
                .setMessage("确定删除这个资源吗？")
                .setPositiveButton("删除", (d, w) -> deleteResource())
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteResource() {
        Map<String, Object> body = new HashMap<>();
        body.put("id", RESOURCE_ID);
        String url = UrlBuilder.getInstance()
                .longPath(SecureStrings.path(51))
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

    private void toggleCommentLike(int commentId, boolean like) {
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

    private void toggleResourceLike() {
        if (!UserToken.isLoggedIn()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean likeNow = !resourceLiked;
        Map<String, Object> body = new HashMap<>();
        body.put("target_type", TARGET_TYPE);
        body.put("target_id", RESOURCE_ID);

        applyLikeState(likeNow);
        adjustLikeCount(likeNow ? 1 : -1);
        sendStatusChange(SecureStrings.path(97), likeNow ? "like" : "unlike", body, likeNow,
                "点赞失败", "取消点赞失败", () -> {
                    applyLikeState(!likeNow);
                    adjustLikeCount(likeNow ? -1 : 1);
                });
    }

    private void toggleResourceFavorite() {
        if (!UserToken.isLoggedIn()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean favoriteNow = !resourceFavorited;
        Map<String, Object> body = new HashMap<>();
        body.put("target_type", TARGET_TYPE);
        body.put("target_id", RESOURCE_ID);

        applyFavoriteState(favoriteNow);
        adjustFavoriteCount(favoriteNow ? 1 : -1);
        sendStatusChange(SecureStrings.path(98), favoriteNow ? "favorite" : "unfavorite", body, favoriteNow,
                "收藏失败", "取消收藏失败", () -> {
                    applyFavoriteState(!favoriteNow);
                    adjustFavoriteCount(favoriteNow ? -1 : 1);
                });
    }

    private void adjustLikeCount(int delta) {
        int current = 0;
        try {
            current = Integer.parseInt(binding.like.getText().toString());
        } catch (NumberFormatException ignored) {
        }
        binding.like.setText(String.valueOf(Math.max(0, current + delta)));
    }

    private void adjustFavoriteCount(int delta) {
        int current = 0;
        try {
            current = Integer.parseInt(binding.favorites.getText().toString());
        } catch (NumberFormatException ignored) {
        }
        binding.favorites.setText(String.valueOf(Math.max(0, current + delta)));
    }

    private void applyLikeState(boolean liked) {
        if (binding == null) return;
        resourceLiked = liked;
        binding.btnLike.setIconResource(liked
                ? R.drawable.material_symbols_favorite_rounded
                : R.drawable.material_symbols_favorite_outline_rounded);
        binding.btnLike.setIconTint(ColorStateList.valueOf(
                ContextCompat.getColor(this, liked ? R.color.colorPrimary : R.color.colorSecondary)));
        binding.like.setTextColor(ContextCompat.getColor(this,
                liked ? R.color.colorPrimary : R.color.colorSecondary));
    }

    private void applyFavoriteState(boolean favorited) {
        if (binding == null) return;
        resourceFavorited = favorited;
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
        boolean coined = userCoinCount > 0;
        binding.btnCoins.setIconResource(coined
                ? R.drawable.material_symbols_paid_rounded
                : R.drawable.material_symbols_paid_outline_rounded);
        binding.btnCoins.setIconTint(ColorStateList.valueOf(
                ContextCompat.getColor(this, coined ? R.color.colorPrimary : R.color.colorSecondary)));
        binding.coins.setTextColor(ContextCompat.getColor(this,
                coined ? R.color.colorPrimary : R.color.colorSecondary));
    }

    private void sendStatusChange(String endpoint, String action, Map<String, Object> body, boolean post,
                                  String postFailMsg, String deleteFailMsg, Runnable revert) {
        String url = UrlBuilder.getInstance()
                .longPath(l.get().a()[101] + endpoint)
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
        if (resourceAuthorId > 0 && resourceAuthorId == UserToken.getSub()) {
            Toast.makeText(this, "不能给自己的资源投币", Toast.LENGTH_SHORT).show();
            return;
        }
        int remaining = 2 - userCoinCount;
        if (remaining <= 0) {
            Toast.makeText(this, "已投满 2 枚，不能继续投币", Toast.LENGTH_SHORT).show();
            return;
        }
        coinSheet.show(remaining);
    }

    private void submitCoin(int count) {
        Map<String, Object> body = new HashMap<>();
        body.put("target_type", TARGET_TYPE);
        body.put("target_id", RESOURCE_ID);
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
                            int current = 0;
                            try {
                                current = Integer.parseInt(binding.coins.getText().toString());
                            } catch (NumberFormatException ignored) {
                            }
                            binding.coins.setText(String.valueOf(current + count));
                            userCoinCount += count;
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

    private void openCommentReportPage() {
        Intent intent = new Intent(this, ReportActivity.class);
        intent.putExtra("target_type", 3);
        intent.putExtra("target_id", actionTargetId);
        intent.putExtra("target_content", actionTargetContent == null ? "" : actionTargetContent);
        startActivity(intent);
    }

    private void jumpToComment(int commentId) {
        if (commentId <= 0) return;
        for (int i = 0; i < commentsList.size(); i++) {
            if (commentsList.get(i).getId() == commentId) {
                binding.commentsRecyclerView.scrollToPosition(i);
                return;
            }
        }
    }

    private void loadResourceDetail() {
        String url = UrlBuilder.getInstance()
                .longPath(SecureStrings.path(51))
                .param("action", "detail")
                .param("id", String.valueOf(RESOURCE_ID))
                .param(SecureStrings.path(95), AppToken.getToken())
                .build();

        HttpManage.getInstance().get(url)
                .header(SecureStrings.path(96), UserToken.getToken())
                .onSuccess((code, body) -> {
                    try {
                        JSONObject resp = new JSONObject(body.trim());
                        if (code != 200 || resp.getInt("code") != 200) {
                            String msg = resp.optString("msg", "加载失败");
                            runOnUiThread(() -> {
                                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                                finish();
                            });
                            return;
                        }
                        JSONObject data = resp.getJSONObject("data");
                        String authorNickname = data.getString("author_nickname");
                        String authorAvatar = data.getString("author_avatar");
                        String authorUserLevelTitle = data.getString("author_user_level_title");
                        int authorMemberLevel = data.getInt("author_member_level");
                        String createdAt = data.getString("created_at");
                        String title = data.getString("title");
                        String sectionName = data.optString("section_name", "");
                        String resTypeName = data.optString("res_type_name", "");
                        String intro = data.optString("intro", "");
                        boolean isTop = data.optInt("is_top", 0) == 1;
                        boolean isRecommend = data.optInt("is_recommend", 0) == 1;
                        boolean isPaid = data.optInt("is_paid", 0) == 1;
                        double coinPrice = data.optDouble("coin_price", 0.0);
                        double discountPrice = data.optDouble("discount_price", 0.0);
                        String expireText = data.optString("expire_days_text", "");
                        boolean isPurchased = data.optBoolean("is_purchased", false);
                        double heat = data.optDouble("heat", 0.0);
                        String resUrl = data.isNull("res_url") ? "" : data.optString("res_url");
                        String resPwd = data.isNull("res_pwd") ? "" : data.optString("res_pwd");
                        int resType = data.optInt("res_type", 0);
                        int likes = data.optInt("likes", 0);
                        int favorites = data.optInt("favorites", 0);
                        int coins = data.optInt("coins", 0);
                        int views = data.optInt("views", 0);
                        int userId = data.optInt("user_id", 0);
                        int resSectionId = data.optInt("section_id", 0);
                        boolean isAuthor = data.optBoolean("is_author", false);
                        boolean isLiked = data.optBoolean("is_liked", false);
                        boolean isFavorited = data.optBoolean("is_favorited", false);
                        int userCoinCount = data.optInt("user_coin_count", 0);
                        List<String> tags = new ArrayList<>();
                        JSONArray tagArr = data.optJSONArray("tags");
                        if (tagArr != null) {
                            for (int i = 0; i < tagArr.length(); i++) {
                                JSONObject tag = tagArr.optJSONObject(i);
                                if (tag != null && !tag.isNull("name")) {
                                    tags.add(tag.optString("name"));
                                }
                            }
                        }

                        runOnUiThread(() -> {
                            resourceAuthorId = userId;
                            sectionId = resSectionId;
                            resourceIsPaid = isPaid;
                            resourceIsPurchased = isPurchased;
                            resourceIsAuthor = isAuthor;
                            resourceIsTop = isTop;
                            resourceIsRecommend = isRecommend;
                            resourceResType = resType;
                            resourceResUrl = resUrl;
                            resourceResPwd = resPwd;

                            binding.authorNickname.setText(authorNickname);
                            binding.authorNickname.setTextColor(ContextCompat.getColor(this,
                                    authorMemberLevel > 0 ? R.color.colorPrimary : R.color.colorOnSecondaryContainer));
                            Glide.with(this).load(authorAvatar).into(binding.authorAvatar);
                            binding.authorUserLevelTitle.setText(authorUserLevelTitle);
                            binding.createdAt.setText(RelativeTimeUtil.toRelativeTime(createdAt));
                            binding.resourceTitle.setText(title);

                            View.OnClickListener toProfile = v -> {
                                if (resourceAuthorId > 0) {
                                    Intent intent = new Intent(ResourceDetailActivity.this, UserProfileActivity.class);
                                    intent.putExtra(UserProfileActivity.EXTRA_USER_ID, resourceAuthorId);
                                    intent.putExtra(UserProfileActivity.EXTRA_USERNAME, authorNickname);
                                    startActivity(intent);
                                }
                            };
                            binding.authorAvatar.setOnClickListener(toProfile);
                            binding.authorNickname.setOnClickListener(toProfile);

                            binding.cardVip.setVisibility(isTop ? android.view.View.VISIBLE : android.view.View.GONE);
                            binding.cardRecommend.setVisibility(isRecommend ? android.view.View.VISIBLE : android.view.View.GONE);
                            binding.tvSection.setText(sectionName);
                            binding.tvResType.setText(resTypeName);
                            if (tags.isEmpty()) {
                                binding.cardTag.setVisibility(android.view.View.GONE);
                            } else {
                                binding.tvTag.setText(tags.get(0));
                                binding.cardTag.setVisibility(android.view.View.VISIBLE);
                            }

                            double price = discountPrice > 0 ? discountPrice : coinPrice;
                            binding.tvPrice.setText(isPaid ? String.format("%.2f 币", price) : "免费");
                            binding.tvPrice.setTextColor(ContextCompat.getColor(this,
                                    isPaid ? R.color.colorOnSurface : R.color.colorPrimary));
                            binding.tvExpire.setText(expireText.isEmpty() ? (isPaid ? "--" : "永久") : expireText);
                            binding.tvHeat.setText(String.valueOf(heat));

                            binding.resourceIntro.setVisibility(intro.isEmpty()
                                    ? android.view.View.VISIBLE : android.view.View.VISIBLE);
                            if (intro.isEmpty()) {
                                binding.resourceIntro.setText("暂无简介");
                            } else {
                                MarkdownProvider.create(this)
                                        .setMarkdown(binding.resourceIntro, intro);
                            }

                            if (resUrl.isEmpty()) {
                                binding.cardLink.setVisibility(android.view.View.GONE);
                            } else {
                                binding.cardLink.setVisibility(android.view.View.VISIBLE);
                                if (resType == 2) {
                                    binding.tvResUrl.setText("网盘资源，点击下方按钮将在浏览器打开");
                                    if (resPwd.isEmpty()) {
                                        binding.tvResPwd.setVisibility(android.view.View.GONE);
                                    } else {
                                        binding.tvResPwd.setVisibility(android.view.View.VISIBLE);
                                        binding.tvResPwd.setText("提取码：" + resPwd);
                                    }
                                } else {
                                    binding.tvResUrl.setText("直链资源，点击下方按钮在应用内下载");
                                    binding.tvResPwd.setVisibility(android.view.View.GONE);
                                }
                            }

                            binding.like.setText(String.valueOf(likes));
                            binding.favorites.setText(String.valueOf(favorites));
                            binding.coins.setText(String.valueOf(coins));
                            applyLikeState(isLiked);
                            applyFavoriteState(isFavorited);
                            applyCoinState();

                            if (isPaid) {
                                resourcePrice = price;
                            }
                            if (isAuthor) {
                                binding.btnPurchase.setText("下载资源");
                            } else if (isPaid && !isPurchased) {
                                binding.btnPurchase.setText(String.format("购买资源（%.2f 币）", price));
                            } else if (isPaid) {
                                binding.btnPurchase.setText("查看下载链接");
                            } else {
                                binding.btnPurchase.setText("下载资源");
                            }
                        });
                    } catch (JSONException e) {
                        runOnUiThread(() -> {
                            Toast.makeText(this, "解析详情失败", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }
                })
                .onFailure((code, err) -> runOnUiThread(() -> {
                    Toast.makeText(this, "网络异常：" + err, Toast.LENGTH_SHORT).show();
                    finish();
                }))
                .execute();

        loadComments();
    }

    private void loadComments() {
        String url = UrlBuilder.getInstance()
                .longPath(SecureStrings.path(55))
                .param("action", "list")
                .param("target_type", String.valueOf(TARGET_TYPE))
                .param("target_id", String.valueOf(RESOURCE_ID))
                .param(SecureStrings.path(95), AppToken.getToken())
                .build();

        HttpManage.getInstance().get(url)
                .header(SecureStrings.path(96), UserToken.getToken())
                .onSuccess((code, body) -> {
                    try {
                        JSONObject bodyJson = new JSONObject(body.trim());
                        if (bodyJson.getInt("code") != 200) {
                            return;
                        }
                        JSONObject data = bodyJson.getJSONObject("data");
                        commentsList.clear();
                        JSONArray list = data.optJSONArray("list");
                        if (list == null) {
                            runOnUiThread(() -> commentsAdapter.setData(commentsList));
                            return;
                        }
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
                            JSONArray replies = info.optJSONArray("replies");
                            if (replies != null) {
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

                                    Replies replyItem = new Replies(repliesId, repliesAuthorName, repliesAuthorNickname,
                                            repliesAuthorAvatar, repliesAuthorMemberLevel, repliesAuthorUserLevel,
                                            repliesLikeCount, repliesAuthorUserLevelTitle, repliesContent,
                                            repliesCreatedAt, repliesReplyToUserNickname);
                                    replyItem.setUserId(repliesUserId);
                                    replyItem.setLiked(repliesLikeStatus);
                                    replyItem.setParentId(repliesParentId);
                                    repliesList.add(replyItem);
                                }
                            }
                            int replyCount = info.optInt("reply_count", repliesList.size());
                            Comments commentItem = new Comments(id, authorName, authorNickname, authorAvatar,
                                    authorMemberLevel, authorUserLevel, likeCount, authorUserLevelTitle, content,
                                    createdAt, repliesList, replyCount);
                            commentItem.setUserId(userId);
                            commentItem.setLiked(likeStatus);
                            commentsList.add(commentItem);
                        }
                        runOnUiThread(() -> commentsAdapter.setData(commentsList));
                    } catch (JSONException e) {
                        // 忽略解析失败
                    }
                })
                .execute();
    }

    private void applyWindowInsets(ActivityResourceDetailBinding binding) {
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
