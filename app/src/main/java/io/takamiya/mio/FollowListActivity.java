package io.takamiya.mio;
import io.takamiya.mio.utils.SecureStrings;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

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
import io.takamiya.mio.adapter.FollowUserAdapter;
import io.takamiya.mio.databinding.ActivityFollowListBinding;
import io.takamiya.mio.utils.AppToken;
import io.takamiya.mio.utils.HttpManage;
import io.takamiya.mio.utils.UserToken;

public class FollowListActivity extends AppCompatActivity {

    private static final String TAG = "FollowListActivity";
    private static final int PAGE_SIZE = 20;

    public static final String EXTRA_MODE = "EXTRA_MODE";
    public static final String MODE_FOLLOWING = "following";
    public static final String MODE_FOLLOWERS = "followers";

    private ActivityFollowListBinding binding;
    private HttpClientJava client = HttpManage.getInstance();
    private FollowUserAdapter adapter;

    private String mode;
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
        binding = ActivityFollowListBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets();

        mode = getIntent().getStringExtra(EXTRA_MODE);
        if (!MODE_FOLLOWING.equals(mode)) {
            mode = MODE_FOLLOWERS;
        }

        boolean isFollowing = MODE_FOLLOWING.equals(mode);
        binding.toolbar.setTitle(isFollowing ? "我的关注" : "我的粉丝");
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        binding.tvEmptyTitle.setText(isFollowing ? "暂未关注任何人" : "暂时还没有粉丝");
        binding.tvEmptyHint.setText(isFollowing ? "去发现感兴趣的用户吧" : "发帖互动可以吸引粉丝");

        adapter = new FollowUserAdapter();
        adapter.setOnItemClickListener(item -> {
            if (item.userId <= 0) return;
            Intent intent = new Intent(this, UserProfileActivity.class);
            intent.putExtra(UserProfileActivity.EXTRA_USER_ID, item.userId);
            intent.putExtra(UserProfileActivity.EXTRA_USERNAME, item.nickname);
            startActivity(intent);
        });

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

        binding.btnRetry.setOnClickListener(v -> refresh());

        refresh();
    }

    private void refresh() {
        loading = true;
        hasMore = true;
        page = 1;
        ++loadRequestId;
        showLoading();
        fetchPage(1, true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!hasLoaded || loading) return;
        LinearLayoutManager lm = (LinearLayoutManager) binding.recyclerView.getLayoutManager();
        if (lm != null && lm.getChildCount() > 0) {
            restorePosition = lm.findFirstVisibleItemPosition();
            View first = lm.getChildAt(0);
            restoreOffset = first != null ? first.getTop() : 0;
        }
        silentRefresh = true;
        loading = true;
        hasMore = true;
        page = 1;
        ++loadRequestId;
        fetchPage(1, true);
    }

    private void loadNextPage() {
        if (loading || !hasMore) return;
        loading = true;
        ++loadRequestId;
        adapter.setLoadingMore(true);
        fetchPage(page + 1, false);
    }

    private void fetchPage(int targetPage, boolean isRefresh) {
        long requestId = loadRequestId;
        String url = UrlBuilder.getInstance()
                .longPath(SecureStrings.path(57))
                .param("action", mode)
                .param(SecureStrings.path(95), AppToken.getToken())
                .param("page", String.valueOf(targetPage))
                .param("pageSize", String.valueOf(PAGE_SIZE))
                .build();

        client.get(url)
                .header(SecureStrings.path(96), UserToken.getToken())
                .onSuccess((code, body) -> {
                    if (requestId != loadRequestId) return;
                    try {
                        JSONObject resp = new JSONObject(body);
                        int respCode = resp.getInt("code");
                        if (code == 200 && respCode == 200) {
                            JSONObject data = resp.optJSONObject("data");
                            JSONArray arr = data != null ? data.optJSONArray("list") : null;
                            List<FollowUserAdapter.FollowUser> list = parseItems(arr);
                            JSONObject pagination = data != null ? data.optJSONObject("pagination") : null;
                            int totalPages = pagination != null ? pagination.optInt("totalPages", 1) : 1;
                            boolean more = targetPage < totalPages && !list.isEmpty();
                            runOnUiThread(() -> {
                                page = targetPage;
                                hasMore = more;
                                loading = false;
                                hasLoaded = true;
                                if (isRefresh) {
                                    adapter.clearLoadingMore();
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
                                    adapter.setLoadingMore(false);
                                    adapter.appendData(list);
                                    if (list.isEmpty()) {
                                        hasMore = false;
                                    }
                                }
                            });
                        } else {
                            runOnUiThread(() -> {
                                loading = false;
                                if (isRefresh) {
                                    showError();
                                } else {
                                    adapter.setLoadingMore(false);
                                }
                            });
                        }
                    } catch (Exception e) {
                        AppLogger.e(TAG, "parse follow list failed: " + e.getMessage(), e);
                        runOnUiThread(() -> {
                            loading = false;
                            if (isRefresh) {
                                showError();
                            } else {
                                adapter.setLoadingMore(false);
                            }
                        });
                    }
                })
                .onFailure((code, err) -> {
                    if (requestId != loadRequestId) return;
                    AppLogger.e(TAG, "load follow list failed: " + err);
                    runOnUiThread(() -> {
                        loading = false;
                        if (isRefresh) {
                            showError();
                        } else {
                            adapter.setLoadingMore(false);
                        }
                    });
                })
                .execute();
    }

    private List<FollowUserAdapter.FollowUser> parseItems(JSONArray arr) {
        List<FollowUserAdapter.FollowUser> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject obj = arr.getJSONObject(i);
                list.add(new FollowUserAdapter.FollowUser(
                        obj.optInt("id", 0),
                        obj.optString("username", ""),
                        obj.optString("nickname", ""),
                        obj.optString("avatar", ""),
                        obj.optInt("member_level", 0),
                        obj.optString("follow_time", "")
                ));
            } catch (Exception e) {
                // skip
            }
        }
        return list;
    }

    private void restoreScroll() {
        if (restorePosition < 0) return;
        LinearLayoutManager lm = (LinearLayoutManager) binding.recyclerView.getLayoutManager();
        if (lm != null) {
            lm.scrollToPositionWithOffset(restorePosition, restoreOffset);
        }
        restorePosition = -1;
    }

    private void showLoading() {
        binding.layoutLoading.setVisibility(View.VISIBLE);
        binding.layoutEmpty.setVisibility(View.GONE);
        binding.layoutError.setVisibility(View.GONE);
        binding.recyclerView.setVisibility(View.GONE);
    }

    private void showList() {
        binding.layoutLoading.setVisibility(View.GONE);
        binding.layoutEmpty.setVisibility(View.GONE);
        binding.layoutError.setVisibility(View.GONE);
        binding.recyclerView.setVisibility(View.VISIBLE);
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

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.toolbar.setPadding(
                    binding.toolbar.getPaddingLeft(),
                    bars.top,
                    binding.toolbar.getPaddingRight(),
                    binding.toolbar.getPaddingBottom());
            return insets;
        });
    }
}