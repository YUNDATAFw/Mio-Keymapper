package io.takamiya.mio;

import static o.s.o.l.get;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.HttpUtils.java.HttpClientJava;
import cn.panda.utils.library.HttpUtils.java.SseRequestJava;
import cn.panda.utils.library.UrlBuilder;
import io.takamiya.mio.adapter.ChatMessageAdapter;
import io.takamiya.mio.bean.ChatMessage;
import io.takamiya.mio.databinding.ActivityChatBinding;
import io.takamiya.mio.utils.AppToken;
import io.takamiya.mio.utils.HttpManage;
import io.takamiya.mio.utils.SecureStrings;
import io.takamiya.mio.utils.UserToken;

public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_USER_ID = "USER_ID";
    public static final String EXTRA_TITLE = "title";

    private static final String TAG = "ChatActivity";
    private static final int PAGE_SIZE = 50;
    private static final long SSE_RECONNECT_DELAY_MS = 3000;
    private static String API_PATH;

    private ActivityChatBinding binding;
    private HttpClientJava client = HttpManage.getInstance();
    private HttpClientJava sseClient;
    private SseRequestJava sseRequest;
    private ChatMessageAdapter adapter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private int peerId;
    private String peerNickname = "";
    private boolean canSend = true;

    private long loadRequestId;
    private boolean loading;
    private int page = 1;
    private boolean hasMore = true;
    private int lastId;

    private boolean destroyed;
    private boolean sseReconnecting;

    private final Runnable sseReconnectRunnable = new Runnable() {
        @Override
        public void run() {
            sseReconnecting = false;
            connectSse();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets(binding);

        API_PATH = get().a()[100];

        peerId = getIntent().getIntExtra(EXTRA_USER_ID, 0);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        peerNickname = title != null ? title : "";
        if (title != null && !title.isEmpty()) {
            binding.toolbar.setTitle(title);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        if (!UserToken.isLoggedIn()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (peerId <= 0) {
            Toast.makeText(this, "参数错误", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        adapter = new ChatMessageAdapter();
        adapter.setOnPeerAvatarClickListener(this::openPeerProfile);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);

        binding.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0 || !hasMore || loading) return;
                LinearLayoutManager lm = (LinearLayoutManager) binding.recyclerView.getLayoutManager();
                if (lm == null) return;
                if (lm.findFirstVisibleItemPosition() <= 2) {
                    loadHistory();
                }
            }
        });

        binding.etInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                boolean hasText = s != null && s.toString().trim().length() > 0;
                binding.btnSend.setEnabled(hasText);
            }
        });

        binding.btnSend.setOnClickListener(v -> sendMessage());

        loadHistory();
        connectSse();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        mainHandler.removeCallbacks(sseReconnectRunnable);
        if (sseRequest != null) {
            try {
                sseRequest.cancel();
            } catch (Exception ignored) {
            }
            sseRequest = null;
        }
        super.onDestroy();
    }

    private String nowTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    private void openPeerProfile() {
        Intent intent = new Intent(this, UserProfileActivity.class);
        intent.putExtra(UserProfileActivity.EXTRA_USER_ID, peerId);
        intent.putExtra(UserProfileActivity.EXTRA_USERNAME, peerNickname);
        startActivity(intent);
    }

    // ══════════════════════════════════════════════
    //  聊天记录
    // ══════════════════════════════════════════════

    private void loadHistory() {
        if (loading || binding == null) return;
        loading = true;
        long rid = ++loadRequestId;
        final int targetPage = page;

        String url = UrlBuilder.getInstance()
                .longPath(API_PATH)
                .param("action", "chat")
                .param("user_id", String.valueOf(peerId))
                .param("page", String.valueOf(targetPage))
                .param("pageSize", String.valueOf(PAGE_SIZE))
                .param(SecureStrings.path(95), AppToken.getToken())
                .build();

        client.get(url)
                .header(SecureStrings.path(96), UserToken.getToken())
                .onSuccess((code, body) -> {
                    if (rid != loadRequestId) return;
                    try {
                        JSONObject resp = new JSONObject(body);
                        int respCode = resp.getInt("code");
                        if (code == 200 && respCode == 200) {
                            JSONObject data = resp.optJSONObject("data");
                            JSONArray arr = data != null ? data.optJSONArray("messages") : null;
                            List<ChatMessage> messages = new ArrayList<>();
                            if (arr != null) {
                                for (int i = 0; i < arr.length(); i++) {
                                    try {
                                        ChatMessage m = ChatMessage.fromJson(arr.getJSONObject(i));
                                        if (m != null) messages.add(m);
                                    } catch (Exception ignored) {
                                    }
                                }
                            }
                            int serverLastId = data != null ? data.optInt("last_id", 0) : 0;
                            boolean serverCanSend = data == null || data.optBoolean("can_send", true);
                            final String peerAvatar = data != null && data.optJSONObject("peer") != null
                                    ? data.optJSONObject("peer").optString("avatar", "") : "";
                            final String peerNick = data != null && data.optJSONObject("peer") != null
                                    ? data.optJSONObject("peer").optString("nickname", "") : "";
                            final String myAvatar = data != null && data.optJSONObject("me") != null
                                    ? data.optJSONObject("me").optString("avatar", "") : "";
                            final boolean peerIsMember = data != null && data.optJSONObject("peer") != null
                                    && data.optJSONObject("peer").optInt("member_level", 0) > 0;
                            runOnUiThread(() -> {
                                if (rid != loadRequestId || binding == null) return;
                                loading = false;
                                canSend = serverCanSend;
                                if (targetPage == 1) {
                                    if (!peerNick.isEmpty()) {
                                        peerNickname = peerNick;
                                    }
                                    adapter.setAvatarUrls(peerAvatar, myAvatar);
                                    if (peerIsMember) {
                                        binding.toolbar.setTitleTextColor(getColor(R.color.colorPrimary));
                                    } else {
                                        binding.toolbar.setTitleTextColor(getColor(R.color.colorOnSecondaryContainer));
                                    }
                                    adapter.setData(messages);
                                    if (!messages.isEmpty()) {
                                        lastId = messages.get(messages.size() - 1).getId();
                                    }
                                    if (lastId < serverLastId) lastId = serverLastId;
                                    scrollToBottom();
                                } else {
                                    List<ChatMessage> older = new ArrayList<>();
                                    for (ChatMessage m : messages) {
                                        if (adapter.getData().isEmpty() || m.getId() < adapter.getData().get(0).getId()) {
                                            older.add(m);
                                        }
                                    }
                                    adapter.prependData(older);
                                }
                                if (messages.size() < PAGE_SIZE) {
                                    hasMore = false;
                                } else {
                                    page = targetPage + 1;
                                }
                            });
                        } else {
                            runOnUiThread(() -> {
                                if (rid != loadRequestId || binding == null) return;
                                loading = false;
                                if (targetPage > 1) {
                                    hasMore = false;
                                }
                            });
                        }
                    } catch (Exception e) {
                        AppLogger.e(TAG, "parse chat failed: " + e.getMessage());
                        runOnUiThread(() -> {
                            if (rid != loadRequestId || binding == null) return;
                            loading = false;
                            if (targetPage > 1) {
                                hasMore = false;
                            }
                        });
                    }
                })
                .onFailure((code, err) -> {
                    if (rid != loadRequestId) return;
                    AppLogger.e(TAG, "load chat failed: " + err);
                    runOnUiThread(() -> {
                        if (rid != loadRequestId || binding == null) return;
                        loading = false;
                        if (targetPage > 1) {
                            hasMore = false;
                        }
                    });
                })
                .execute();
    }

    // ══════════════════════════════════════════════
    //  发送
    // ══════════════════════════════════════════════

    private void sendMessage() {
        final String content = binding.etInput.getText().toString().trim();
        if (content.isEmpty()) return;
        binding.etInput.setText("");

        if (!canSend) {
            showSendFailedDialog("未能发送", "未互相关注，只能发送一条消息");
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("receiver_id", peerId);
        body.put("content", content);

        client.post(UrlBuilder.getInstance()
                        .longPath(API_PATH)
                        .param(SecureStrings.path(95), AppToken.getToken())
                        .build())
                .header(SecureStrings.path(96), UserToken.getToken())
                .json(body)
                .onSuccess((code, resp) -> {
                    try {
                        JSONObject json = new JSONObject(resp.trim());
                        int respCode = json.getInt("code");
                        if (code == 200 && respCode == 200) {
                            int messageId = json.optJSONObject("data") != null
                                    ? json.optJSONObject("data").optInt("message_id", 0) : 0;
                            runOnUiThread(() -> {
                                if (binding == null) return;
                                adapter.appendData(java.util.Collections.singletonList(
                                        new ChatMessage(messageId, "sent", content, nowTime(), false)));
                                if (lastId < messageId) lastId = messageId;
                                scrollToBottom();
                            });
                        } else {
                            runOnUiThread(() -> {
                                if (binding == null) return;
                                adapter.appendData(java.util.Collections.singletonList(
                                        new ChatMessage(content, nowTime(), true, true)));
                                scrollToBottom();
                                showSendFailedDialog("发送失败",
                                        json.optString("msg", "消息发送失败，请检查网络后重试"));
                            });
                        }
                    } catch (Exception e) {
                        AppLogger.e(TAG, "parse send result failed: " + e.getMessage());
                        runOnUiThread(() -> {
                            if (binding == null) return;
                            adapter.appendData(java.util.Collections.singletonList(
                                    new ChatMessage(content, nowTime(), true, true)));
                            scrollToBottom();
                            showSendFailedDialog("发送失败", "消息发送失败，请检查网络后重试");
                        });
                    }
                })
                .onFailure((code, err) -> runOnUiThread(() -> {
                    if (binding == null) return;
                    adapter.appendData(java.util.Collections.singletonList(
                            new ChatMessage(content, nowTime(), true, true)));
                    scrollToBottom();
                    showSendFailedDialog("发送失败", "网络异常，请稍后重试");
                }))
                .execute();
    }

    // ══════════════════════════════════════════════
    //  SSE 实时消息
    // ══════════════════════════════════════════════

    private void connectSse() {
        if (destroyed || binding == null) return;
        if (sseClient == null) {
            sseClient = new HttpClientJava.Builder()
                    .connectTimeout(15L)
                    .readTimeout(0L) // 长连接，不限制读取超时
                    .writeTimeout(15L)
                    .build();
        }

        final int fromId = lastId;
        String url = UrlBuilder.getInstance()
                .longPath(API_PATH)
                .param("action", "sse")
                .param("last_id", String.valueOf(fromId))
                .param("chat_user_id", String.valueOf(peerId))
                .param(SecureStrings.path(95), AppToken.getToken())
                .build();

        sseRequest = sseClient.sse(url)
                .header(SecureStrings.path(96), UserToken.getToken())
                .onEvent(this::onSseEvent)
                .onClosed(() -> {
                    AppLogger.i(TAG, "SSE closed, reconnect scheduled");
                    scheduleSseReconnect();
                })
                .onFailure((code, err) -> {
                    AppLogger.e(TAG, "SSE failure: " + err);
                    scheduleSseReconnect();
                });
        sseRequest.execute();
    }

    private void onSseEvent(String data) {
        if (data == null || data.isEmpty()) return;
        try {
            JSONObject obj = new JSONObject(data);
            String content = obj.optString("content", null);
            if (content == null) {
                return; // connected / timeout 事件，忽略（timeout 由 onClosed 触发的关闭处理）
            }
            final int msgId = obj.optInt("id", 0);
            final String msgContent = content;
            final String createdAt = obj.optString("created_at", "");
            if (msgId > 0 && msgId <= lastId) return; // 去重
            runOnUiThread(() -> {
                if (binding == null || destroyed) return;
                if (msgId > lastId) lastId = msgId;
                adapter.appendData(Collections.singletonList(
                        new ChatMessage(msgId, "received", msgContent, createdAt, false)));
                scrollToBottom();
            });
        } catch (Exception e) {
            AppLogger.e(TAG, "sse event parse failed: " + e.getMessage());
        }
    }

    private void scheduleSseReconnect() {
        if (destroyed || sseReconnecting) return;
        sseReconnecting = true;
        mainHandler.postDelayed(sseReconnectRunnable, SSE_RECONNECT_DELAY_MS);
    }

    // ══════════════════════════════════════════════
    //  UI 辅助
    // ══════════════════════════════════════════════

    private void showSendFailedDialog(String title, String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("知道了", null)
                .show();
    }

    private void scrollToBottom() {
        binding.recyclerView.post(() -> {
            if (binding != null && adapter != null && adapter.getItemCount() > 0) {
                binding.recyclerView.scrollToPosition(adapter.getItemCount() - 1);
            }
        });
    }

    private void applyWindowInsets(ActivityChatBinding binding) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());

            binding.toolbar.setPadding(
                    binding.toolbar.getPaddingLeft(),
                    bars.top,
                    binding.toolbar.getPaddingRight(),
                    binding.toolbar.getPaddingBottom());

            binding.content.setPadding(
                    binding.content.getPaddingLeft(),
                    binding.content.getPaddingTop(),
                    binding.content.getPaddingRight(),
                    Math.max(bars.bottom, ime.bottom));

            if (ime.bottom > 0 && adapter != null && adapter.getItemCount() > 0) {
                binding.recyclerView.post(() ->
                        binding.recyclerView.scrollToPosition(adapter.getItemCount() - 1));
            }
            return insets;
        });
    }
}
