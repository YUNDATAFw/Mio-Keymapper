package io.takamiya.mio;
import io.takamiya.mio.utils.SecureStrings;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import cn.panda.utils.library.UrlBuilder;
import io.takamiya.mio.databinding.DialogCommentEditBinding;
import io.takamiya.mio.utils.AppToken;
import io.takamiya.mio.utils.HttpManage;
import io.takamiya.mio.utils.UserToken;

/**
 * 评论编辑 BottomSheet 封装类
 * 用法：
 *   CommentEditBottomSheet sheet = new CommentEditBottomSheet(context);
 *   sheet.setTargetType(1);           // 目标类型，帖子为 1
 *   sheet.setTargetId(postId);        // 目标 id
 *   sheet.setReplyTo("某用户昵称");    // 回复某评论，可空
 *   sheet.setOnSendListener((content, s) -> { ...自定义网络请求... });
 *   sheet.setOnResultListener(...);   // 发送结果回调，用于刷新列表
 *   sheet.show();
 */
public class CommentEditBottomSheet {

    public interface OnSendListener {
        void onSend(String content, CommentEditBottomSheet sheet);
    }

    public interface OnResultListener {
        void onSuccess(String msg);
        void onFailure(String err);
    }

    private final Context context;
    private final BottomSheetDialog dialog;
    private final DialogCommentEditBinding binding;

    private int targetType = 1;
    private int targetId;
    private int replyToId;
    private String replyToName;

    private OnSendListener sendListener;
    private OnResultListener resultListener;

    public CommentEditBottomSheet(Context context) {
        this.context = context;
        binding = DialogCommentEditBinding.inflate(LayoutInflater.from(context));
        dialog = new BottomSheetDialog(context);
        dialog.setContentView(binding.getRoot());

        binding.btnClose.setOnClickListener(v -> dismiss());
        binding.btnCancel.setOnClickListener(v -> dismiss());
        binding.btnSend.setOnClickListener(v -> send());
    }

    public Context getContext() {
        return context;
    }

    public void setTargetType(int targetType) {
        this.targetType = targetType;
    }

    public void setTargetId(int targetId) {
        this.targetId = targetId;
    }

    public int getTargetType() {
        return targetType;
    }

    public int getTargetId() {
        return targetId;
    }

    public void setReplyTo(String nickname) {
        setReplyTo(0, nickname);
    }

    public void setReplyTo(int commentId, String nickname) {
        this.replyToId = commentId;
        this.replyToName = nickname;
        if (isReplyMode()) {
            binding.tvSheetTitle.setText("回复 @" + nickname);
        } else {
            binding.tvSheetTitle.setText("发表评论");
        }
    }

    /**
     * true = 回复某条评论；false = 评论帖子
     */
    public boolean isReplyMode() {
        return this.replyToId > 0;
    }

    public int getReplyToId() {
        return replyToId;
    }

    public String getReplyToName() {
        return replyToName;
    }

    public void setOnSendListener(OnSendListener listener) {
        this.sendListener = listener;
    }

    public void setOnResultListener(OnResultListener listener) {
        this.resultListener = listener;
    }

    public void show() {
        binding.etCommentInput.setText("");
        binding.etCommentInput.clearFocus();
        binding.tilComment.setError(null);
        setSending(false);
        dialog.show();
    }

    public void dismiss() {
        dialog.dismiss();
    }

    public boolean isShowing() {
        return dialog.isShowing();
    }

    private void send() {
        if (!UserToken.isLoggedIn()) {
            Toast.makeText(context, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        String content = binding.etCommentInput.getText().toString().trim();
        if (content.isEmpty()) {
            binding.tilComment.setError("内容不能为空");
            return;
        }
        binding.tilComment.setError(null);
        setSending(true);
        if (sendListener != null) {
            sendListener.onSend(content, this);
        } else {
            submitToApi(content);
        }
    }

    /**
     * 内置网络请求模板：走评论接口的 create 动作。
     * 参数与 endpoint 请按实际接口调整。
     */
    public void submitToApi(String content) {
        Map<String, Object> data = new HashMap<>();
        data.put("target_type", targetType);
        data.put("target_id", targetId);
        data.put("parent_id", isReplyMode() ? replyToId : 0);
        data.put("content", content);

        String url = UrlBuilder.getInstance()
                .longPath(SecureStrings.path(55))
                .param("action", "create")
                .param(SecureStrings.path(95), AppToken.getToken())
                .build();

        HttpManage.getInstance().post(url)
                .header(SecureStrings.path(96), UserToken.getToken())
                .json(data)
                .onSuccess((code, body) -> runOnUiThread(() -> {
                    try {
                        JSONObject resp = new JSONObject(body);
                        if (code == 200 && resp.getInt("code") == 200) {
                            onSendSuccess(resp.optString("msg", "评论成功"));
                        } else {
                            onSendFailure(resp.optString("msg", "评论失败"));
                        }
                    } catch (Exception e) {
                        onSendFailure("解析响应失败");
                    }
                }))
                .onFailure((code, err) -> runOnUiThread(() -> onSendFailure("网络异常：" + err)))
                .execute();
    }

    public void onSendSuccess(String msg) {
        setSending(false);
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
        dismiss();
        if (resultListener != null) {
            resultListener.onSuccess(msg);
        }
    }

    public void onSendFailure(String err) {
        setSending(false);
        Toast.makeText(context, err, Toast.LENGTH_SHORT).show();
        if (resultListener != null) {
            resultListener.onFailure(err);
        }
    }

    private void setSending(boolean sending) {
        binding.btnSend.setEnabled(!sending);
        binding.btnSend.setText(sending ? "发送中..." : "发送");
        binding.progressSend.setVisibility(sending ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    private void runOnUiThread(Runnable runnable) {
        ContextCompat.getMainExecutor(context).execute(runnable);
    }
}