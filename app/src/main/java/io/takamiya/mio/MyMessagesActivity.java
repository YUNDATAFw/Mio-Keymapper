package io.takamiya.mio;
import io.takamiya.mio.utils.SecureStrings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.UrlBuilder;
import io.takamiya.mio.adapter.MessagesAdapter;
import io.takamiya.mio.bean.Message;
import io.takamiya.mio.databinding.ActivityMyMessagesBinding;
import io.takamiya.mio.databinding.DialogMessageDetailBinding;
import io.takamiya.mio.utils.AppToken;
import io.takamiya.mio.utils.HttpManage;
import io.takamiya.mio.utils.UserToken;

public class MyMessagesActivity extends AppCompatActivity {

    private static final String TAG = "MyMessagesActivity";
    private static final String API_PATH = SecureStrings.path(60);
    private static final int PAGE_SIZE = 50;

    private ActivityMyMessagesBinding binding;
    private MessagesAdapter adapter;

    private int page = 1;
    private int totalPages = 1;
    private boolean loading;
    private boolean loaded;
    private long requestId;
    private boolean silentRefresh;
    private int restorePosition = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMyMessagesBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets(binding);

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.toolbar.inflateMenu(R.menu.menu_my_messages);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_mark_all_read) {
                markAllRead();
                return true;
            } else if (item.getItemId() == R.id.action_clear_all) {
                confirmClearAll();
                return true;
            }
            return false;
        });

        if (!UserToken.isLoggedIn()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        adapter = new MessagesAdapter();
        adapter.setOnMessageActionListener(new MessagesAdapter.OnMessageActionListener() {
            @Override
            public void onDetailClick(int position) {
                Message message = getMessageAt(position);
                if (message != null) {
                    showDetailDialog(message, position);
                }
            }

            @Override
            public void onDeleteClick(int position) {
                Message message = getMessageAt(position);
                if (message != null) {
                    confirmDelete(message, position);
                }
            }
        });
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);
        ((LinearLayoutManager) binding.recyclerView.getLayoutManager()).setReverseLayout(true);
        ((LinearLayoutManager) binding.recyclerView.getLayoutManager()).setStackFromEnd(true);

        binding.swipeRefresh.setOnRefreshListener(this::reload);
        binding.swipeRefresh.setColorSchemeResources(R.color.colorPrimary);
        binding.btnRetry.setOnClickListener(v -> reload());
        binding.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                int total = adapter.getItemCount();
                if (lm != null && total > 0
                        && lm.findLastVisibleItemPosition() >= total - 3) {
                    loadOlder();
                }
            }
        });

        loadPage(1);
    }

    private Message getMessageAt(int position) {
        List<Message> data = adapter.getData();
        if (position < 0 || position >= data.size()) return null;
        return data.get(position);
    }

    private String buildUrl(String action, String... param) {
        UrlBuilder.Builder builder = UrlBuilder.getInstance()
                .longPath(API_PATH)
                .param(SecureStrings.path(95), AppToken.getToken());
        if (action != null) {
            builder.param("action", action);
        }
        for (int i = 0; i + 1 < param.length; i += 2) {
            builder.param(param[i], param[i + 1]);
        }
        return builder.build();
    }

    // ══════════════════════════════════════════════
    //  列表加载
    // ══════════════════════════════════════════════

    private void reload() {
        loadPage(1);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!loaded || loading || binding == null || adapter.getItemCount() == 0) return;
        LinearLayoutManager lm = (LinearLayoutManager) binding.recyclerView.getLayoutManager();
        if (lm != null && lm.getChildCount() > 0) {
            restorePosition = lm.findLastVisibleItemPosition();
        }
        silentRefresh = true;
        loadPage(1);
    }

    private void loadPage(int targetPage) {
        if (loading || binding == null) return;
        loading = true;
        long rid = ++requestId;
        page = targetPage;

        if (targetPage == 1 && adapter.getItemCount() == 0) {
            showLoading();
        }

        HttpManage.getInstance().get(buildUrl("list",
                "page", String.valueOf(targetPage),
                "pageSize", String.valueOf(PAGE_SIZE)))
                .header(SecureStrings.path(96), UserToken.getToken())
                .onSuccess((code, body) -> {
                    if (rid != requestId) return;
                    try {
                        JSONObject resp = new JSONObject(body);
                        int respCode = resp.getInt("code");
                        if (code == 200 && respCode == 200) {
                            JSONObject data = resp.optJSONObject("data");
                            JSONArray arr = data != null ? data.optJSONArray("list") : null;
                            List<Message> list = arr != null ? Message.fromJsonArray(arr) : new ArrayList<>();
                            JSONObject pagination = data != null ? data.optJSONObject("pagination") : null;
                            int tp = pagination != null ? pagination.optInt("totalPages", 1) : 1;
                            runOnUiThread(() -> {
                                if (rid != requestId || binding == null) return;
                                if (targetPage == 1) {
                                    adapter.setData(list);
                                    loaded = true;
                                    if (list.isEmpty()) {
                                        showEmpty();
                                    } else {
                                        showList();
                                        if (silentRefresh) {
                                            silentRefresh = false;
                                            restoreScroll();
                                        } else {
                                            binding.recyclerView.scrollToPosition(0);
                                        }
                                    }
                                } else {
                                    adapter.appendData(list);
                                }
                                totalPages = Math.max(tp, 1);
                                loading = false;
                                binding.swipeRefresh.setRefreshing(false);
                            });
                        } else {
                            runOnUiThread(() -> {
                                if (rid != requestId || binding == null) return;
                                loading = false;
                                binding.swipeRefresh.setRefreshing(false);
                                if (adapter.getItemCount() == 0) {
                                    showError();
                                }
                            });
                        }
                    } catch (Exception e) {
                        AppLogger.e(TAG, "parse list failed: " + e.getMessage());
                        runOnUiThread(() -> {
                            if (rid != requestId || binding == null) return;
                            loading = false;
                            binding.swipeRefresh.setRefreshing(false);
                            if (adapter.getItemCount() == 0) {
                                showError();
                            }
                        });
                    }
                })
                .onFailure((code, err) -> {
                    if (rid != requestId) return;
                    AppLogger.e(TAG, "load list failed: " + err);
                    runOnUiThread(() -> {
                        if (rid != requestId || binding == null) return;
                        loading = false;
                        binding.swipeRefresh.setRefreshing(false);
                        if (adapter.getItemCount() == 0) {
                            showError();
                        }
                    });
                })
                .execute();
    }

    private void loadOlder() {
        if (loading || binding == null) return;
        if (page >= totalPages) return;
        loadPage(page + 1);
    }

    private void restoreScroll() {
        if (restorePosition < 0) return;
        LinearLayoutManager lm = (LinearLayoutManager) binding.recyclerView.getLayoutManager();
        if (lm != null) {
            lm.scrollToPosition(restorePosition);
        }
        restorePosition = -1;
    }

    // ══════════════════════════════════════════════
    //  详情（打开弹窗即标记已读）
    // ══════════════════════════════════════════════

    private void showDetailDialog(Message message, int position) {
        DialogMessageDetailBinding db = DialogMessageDetailBinding.inflate(LayoutInflater.from(this));
        db.tvDetailTitle.setText(emptyToDash(message.getTitle()));
        db.tvDetailSender.setText(message.getSenderName() != null && !message.getSenderName().isEmpty()
                ? "来自：" + message.getSenderName() : "");
        db.tvDetailTime.setText(message.getCreatedAt());
        db.tvDetailContent.setText(emptyToDash(message.getContent()));

        new MaterialAlertDialogBuilder(this)
                .setView(db.getRoot())
                .setPositiveButton("关闭", null)
                .show();

        // 打开详情弹窗后调用 detail 接口（服务端自动置已读）
        if (message.getIsRead() == 0) {
            markRead(message, position);
        }
    }

    private void markRead(Message message, int position) {
        HttpManage.getInstance().get(buildUrl("detail", "id", String.valueOf(message.getId())))
                .header(SecureStrings.path(96), UserToken.getToken())
                .onSuccess((code, body) -> {
                    try {
                        JSONObject resp = new JSONObject(body);
                        int respCode = resp.getInt("code");
                        if (code == 200 && respCode == 200) {
                            runOnUiThread(() -> {
                                if (binding == null) return;
                                Message current = getMessageAt(position);
                                if (current != null && current.getId() == message.getId()) {
                                    current.setIsRead(1);
                                    adapter.notifyItemChanged(position);
                                }
                            });
                        }
                    } catch (Exception e) {
                        AppLogger.e(TAG, "mark read failed: " + e.getMessage());
                    }
                })
                .onFailure((code, err) -> AppLogger.e(TAG, "mark read request failed: " + err))
                .execute();
    }

    // ══════════════════════════════════════════════
    //  批量操作
    // ══════════════════════════════════════════════

    private void markAllRead() {
        Map<String, Object> body = new HashMap<>();
        body.put("action", "mark_read");

        HttpManage.getInstance().delete(buildUrl(null))
                .header(SecureStrings.path(96), UserToken.getToken())
                .json(body)
                .onSuccess((code, responseBody) -> {
                    try {
                        JSONObject resp = new JSONObject(responseBody);
                        int respCode = resp.getInt("code");
                        if (code == 200 && respCode == 200) {
                            runOnUiThread(() -> {
                                if (binding == null) return;
                                adapter.markAllRead();
                                Toast.makeText(this, "已全部标记已读", Toast.LENGTH_SHORT).show();
                            });
                        } else {
                            String msg = resp.optString("msg", "操作失败");
                            runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
                        }
                    } catch (Exception e) {
                        AppLogger.e(TAG, "parse mark all read failed: " + e.getMessage());
                        runOnUiThread(() -> Toast.makeText(this, "操作失败", Toast.LENGTH_SHORT).show());
                    }
                })
                .onFailure((code, err) -> {
                    AppLogger.e(TAG, "mark all read failed: " + err);
                    runOnUiThread(() -> Toast.makeText(this, "操作失败", Toast.LENGTH_SHORT).show());
                })
                .execute();
    }

    private void confirmClearAll() {
        List<Message> data = adapter.getData();
        if (data.isEmpty()) {
            Toast.makeText(this, "暂无消息", Toast.LENGTH_SHORT).show();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("清空消息")
                .setMessage("确定清空已加载的 " + data.size() + " 条消息吗？")
                .setPositiveButton("清空", (d, w) -> clearAll())
                .setNegativeButton("取消", null)
                .show();
    }

    private void clearAll() {
        List<Message> data = adapter.getData();
        if (data.isEmpty()) return;

        List<Integer> ids = new ArrayList<>();
        for (Message m : data) {
            ids.add(m.getId());
        }
        clearBatch(ids, 0);
    }

    private void clearBatch(List<Integer> ids, int from) {
        if (from >= ids.size()) {
            runOnUiThread(() -> {
                if (binding == null) return;
                adapter.setData(new ArrayList<>());
                showEmpty();
                Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show();
            });
            return;
        }
        int to = Math.min(from + 50, ids.size());
        List<Integer> chunk = ids.subList(from, to);

        Map<String, Object> body = new HashMap<>();
        body.put("message_ids", chunk);

        HttpManage.getInstance().delete(buildUrl(null))
                .header(SecureStrings.path(96), UserToken.getToken())
                .json(body)
                .onSuccess((code, responseBody) -> {
                    try {
                        JSONObject resp = new JSONObject(responseBody);
                        int respCode = resp.getInt("code");
                        if (code == 200 && respCode == 200) {
                            clearBatch(ids, to);
                        } else {
                            String msg = resp.optString("msg", "清空失败");
                            runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
                        }
                    } catch (Exception e) {
                        AppLogger.e(TAG, "parse clear failed: " + e.getMessage());
                        runOnUiThread(() -> Toast.makeText(this, "清空失败", Toast.LENGTH_SHORT).show());
                    }
                })
                .onFailure((code, err) -> {
                    AppLogger.e(TAG, "clear failed: " + err);
                    runOnUiThread(() -> Toast.makeText(this, "清空失败", Toast.LENGTH_SHORT).show());
                })
                .execute();
    }

    // ══════════════════════════════════════════════
    //  删除
    // ══════════════════════════════════════════════

    private void confirmDelete(Message message, int position) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("删除消息")
                .setMessage("确定删除这条消息吗？")
                .setPositiveButton("删除", (d, w) -> deleteMessage(message, position))
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteMessage(Message message, int position) {
        Map<String, Object> body = new HashMap<>();
        body.put("message_id", message.getId());

        HttpManage.getInstance().delete(buildUrl(null))
                .header(SecureStrings.path(96), UserToken.getToken())
                .json(body)
                .onSuccess((code, responseBody) -> {
                    try {
                        JSONObject resp = new JSONObject(responseBody);
                        int respCode = resp.getInt("code");
                        if (code == 200 && respCode == 200) {
                            runOnUiThread(() -> {
                                if (binding == null) return;
                                Message current = getMessageAt(position);
                                if (current != null && current.getId() == message.getId()) {
                                    adapter.removeAt(position);
                                }
                                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                            });
                        } else {
                            String msg = resp.optString("msg", "删除失败");
                            runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
                        }
                    } catch (Exception e) {
                        AppLogger.e(TAG, "parse delete failed: " + e.getMessage());
                        runOnUiThread(() -> Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show());
                    }
                })
                .onFailure((code, err) -> {
                    AppLogger.e(TAG, "delete failed: " + err);
                    runOnUiThread(() -> Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show());
                })
                .execute();
    }

    // ══════════════════════════════════════════════
    //  状态切换
    // ══════════════════════════════════════════════

    private void showLoading() {
        binding.layoutLoading.setVisibility(View.VISIBLE);
        binding.layoutEmpty.setVisibility(View.GONE);
        binding.layoutError.setVisibility(View.GONE);
        binding.recyclerView.setVisibility(View.GONE);
    }

    private void showEmpty() {
        binding.layoutLoading.setVisibility(View.GONE);
        binding.layoutEmpty.setVisibility(View.VISIBLE);
        binding.layoutError.setVisibility(View.GONE);
        binding.recyclerView.setVisibility(View.GONE);
    }

    private void showError() {
        binding.layoutLoading.setVisibility(View.GONE);
        binding.layoutEmpty.setVisibility(View.GONE);
        binding.layoutError.setVisibility(View.VISIBLE);
        binding.recyclerView.setVisibility(View.GONE);
    }

    private void showList() {
        binding.layoutLoading.setVisibility(View.GONE);
        binding.layoutEmpty.setVisibility(View.GONE);
        binding.layoutError.setVisibility(View.GONE);
        binding.recyclerView.setVisibility(View.VISIBLE);
    }

    private String emptyToDash(String s) {
        return s == null || s.equals("null") || s.isEmpty() ? "-" : s;
    }

    private void applyWindowInsets(ActivityMyMessagesBinding binding) {
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
