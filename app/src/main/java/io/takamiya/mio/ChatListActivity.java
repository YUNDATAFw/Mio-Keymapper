package io.takamiya.mio;

import static o.s.o.l.*;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.HttpUtils.java.HttpClientJava;
import cn.panda.utils.library.UrlBuilder;
import io.takamiya.mio.adapter.ChatConversationAdapter;
import io.takamiya.mio.bean.ChatConversation;
import io.takamiya.mio.databinding.ActivityChatListBinding;
import io.takamiya.mio.utils.AppToken;
import io.takamiya.mio.utils.HttpManage;
import io.takamiya.mio.utils.SecureStrings;
import io.takamiya.mio.utils.UserToken;

public class ChatListActivity extends AppCompatActivity {

    private static final String TAG = "ChatListActivity";
    private static final int PAGE_SIZE = 20;
    private static String API_PATH;

    private ActivityChatListBinding binding;
    private HttpClientJava client = HttpManage.getInstance();
    private ChatConversationAdapter adapter;

    private long loadRequestId;
    private boolean loading;
    private int page = 1;
    private boolean hasMore = true;
    private boolean hasLoaded;
    private boolean silentRefresh;
    private int restorePosition = -1;
    private int restoreOffset;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatListBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets();

        API_PATH = get().a()[100];

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        if (!UserToken.isLoggedIn()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        adapter = new ChatConversationAdapter();
        adapter.setOnItemClickListener(item -> {
            if (item.getChatUserId() <= 0) return;
            Intent intent = new Intent(this, ChatActivity.class);
            intent.putExtra(ChatActivity.EXTRA_USER_ID, item.getChatUserId());
            intent.putExtra(ChatActivity.EXTRA_TITLE, item.getNickname());
            startActivity(intent);
        });
        adapter.setOnMyMessagesClickListener(() ->
                startActivity(new Intent(this, MyMessagesActivity.class)));

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);

        binding.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || !hasMore || loading) return;
                LinearLayoutManager lm = (LinearLayoutManager) binding.recyclerView.getLayoutManager();
                if (lm == null) return;
                if (lm.findLastVisibleItemPosition() >= adapter.getItemCount() - 3) {
                    loadNextPage();
                }
            }
        });

        binding.swipeRefresh.setOnRefreshListener(this::reload);
        binding.swipeRefresh.setColorSchemeResources(R.color.colorPrimary);
        binding.btnRetry.setOnClickListener(v -> reload());

        loadPage(1);
    }

    private void reload() {
        loadPage(1);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!hasLoaded || loading || adapter.getItemCount() == 1) return;
        LinearLayoutManager lm = (LinearLayoutManager) binding.recyclerView.getLayoutManager();
        if (lm != null && lm.getChildCount() > 0) {
            restorePosition = lm.findFirstVisibleItemPosition();
            View first = lm.getChildAt(0);
            restoreOffset = first != null ? first.getTop() : 0;
        }
        silentRefresh = true;
        loadPage(1);
    }

    private void loadNextPage() {
        if (loading || !hasMore) return;
        loadPage(page + 1);
    }

    private void loadPage(int targetPage) {
        if (loading) return;
        loading = true;
        long rid = ++loadRequestId;
        page = targetPage;

        if (targetPage == 1 && adapter.getItemCount() == 0) {
            showLoading();
        }

        String url = UrlBuilder.getInstance()
                .longPath(API_PATH)
                .param("action", "list")
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
                            JSONArray arr = data != null ? data.optJSONArray("list") : null;
                            List<ChatConversation> list = new ArrayList<>();
                            if (arr != null) {
                                for (int i = 0; i < arr.length(); i++) {
                                    try {
                                        ChatConversation c = ChatConversation.fromJson(arr.getJSONObject(i));
                                        if (c != null) list.add(c);
                                    } catch (Exception ignored) {
                                    }
                                }
                            }
                            boolean more = list.size() >= PAGE_SIZE;
                            runOnUiThread(() -> {
                                if (rid != loadRequestId || binding == null) return;
                                page = targetPage;
                                hasMore = more;
                                loading = false;
                                hasLoaded = true;
                                binding.swipeRefresh.setRefreshing(false);
                                if (targetPage == 1) {
                                    adapter.setData(list);
                                    if (list.isEmpty()) {
                                        showEmpty();
                                    } else {
                                        showList();
                                        if (silentRefresh) {
                                            silentRefresh = false;
                                            restoreScroll();
                                        }
                                    }
                                } else {
                                    adapter.appendData(list);
                                    if (list.isEmpty()) {
                                        hasMore = false;
                                    }
                                }
                            });
                        } else {
                            runOnUiThread(() -> {
                                if (rid != loadRequestId || binding == null) return;
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
                            if (rid != loadRequestId || binding == null) return;
                            loading = false;
                            binding.swipeRefresh.setRefreshing(false);
                            if (adapter.getItemCount() == 0) {
                                showError();
                            }
                        });
                    }
                })
                .onFailure((code, err) -> {
                    if (rid != loadRequestId) return;
                    AppLogger.e(TAG, "load list failed: " + err);
                    runOnUiThread(() -> {
                        if (rid != loadRequestId || binding == null) return;
                        loading = false;
                        binding.swipeRefresh.setRefreshing(false);
                        if (adapter.getItemCount() == 0) {
                            showError();
                        }
                    });
                })
                .execute();
    }

    private void showLoading() {
        binding.layoutLoading.setVisibility(android.view.View.VISIBLE);
        binding.layoutEmpty.setVisibility(android.view.View.GONE);
        binding.layoutError.setVisibility(android.view.View.GONE);
    }

    private void showEmpty() {
        binding.layoutLoading.setVisibility(android.view.View.GONE);
        binding.layoutEmpty.setVisibility(android.view.View.VISIBLE);
        binding.layoutError.setVisibility(android.view.View.GONE);
    }

    private void showList() {
        binding.layoutLoading.setVisibility(android.view.View.GONE);
        binding.layoutEmpty.setVisibility(android.view.View.GONE);
        binding.layoutError.setVisibility(android.view.View.GONE);
    }

    private void showError() {
        binding.layoutLoading.setVisibility(android.view.View.GONE);
        binding.layoutEmpty.setVisibility(android.view.View.GONE);
        binding.layoutError.setVisibility(android.view.View.VISIBLE);
    }

    private void restoreScroll() {
        if (restorePosition < 0) return;
        LinearLayoutManager lm = (LinearLayoutManager) binding.recyclerView.getLayoutManager();
        if (lm != null) {
            lm.scrollToPositionWithOffset(restorePosition, restoreOffset);
        }
        restorePosition = -1;
    }

    private void applyWindowInsets() {
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
