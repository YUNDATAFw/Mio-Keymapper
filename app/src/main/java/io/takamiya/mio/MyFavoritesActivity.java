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
import cn.panda.utils.library.UrlBuilder;
import io.takamiya.mio.adapter.MyFavoritesAdapter;
import io.takamiya.mio.databinding.ActivityMyFavoritesBinding;
import io.takamiya.mio.utils.AppToken;
import io.takamiya.mio.utils.HttpManage;
import io.takamiya.mio.utils.UserToken;

public class MyFavoritesActivity extends AppCompatActivity {

    private static final String TAG = "MyFavoritesActivity";
    private static final int PAGE_SIZE = 20;

    private ActivityMyFavoritesBinding binding;
    private MyFavoritesAdapter adapter;

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
        binding = ActivityMyFavoritesBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets();

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new MyFavoritesAdapter();
        adapter.setOnItemClickListener(item -> {
            if (item.targetType == 2) {
                Intent intent = new Intent(this, ResourceDetailActivity.class);
                intent.putExtra("RESOURCE_ID", item.targetId);
                startActivity(intent);
            } else if (item.targetType == 1) {
                Intent intent = new Intent(this, PostDetailActivity.class);
                intent.putExtra("POST_ID", item.targetId);
                startActivity(intent);
            }
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
        long requestId = ++loadRequestId;
        if (!silentRefresh) {
            showLoading();
        }
        fetchPage(requestId, 1, true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!hasLoaded || loading || adapter.getItemCount() == 0) return;
        LinearLayoutManager lm = (LinearLayoutManager) binding.recyclerView.getLayoutManager();
        if (lm != null && lm.getChildCount() > 0) {
            restorePosition = lm.findFirstVisibleItemPosition();
            View first = lm.getChildAt(0);
            restoreOffset = first != null ? first.getTop() : 0;
        }
        silentRefresh = true;
        refresh();
    }

    private void loadNextPage() {
        if (loading || !hasMore) return;
        loading = true;
        long requestId = ++loadRequestId;
        adapter.setLoadingMore(true);
        fetchPage(requestId, page + 1, false);
    }

    private void fetchPage(long requestId, int targetPage, boolean isRefresh) {
        String url = UrlBuilder.getInstance()
                .longPath(SecureStrings.path(53))
                .param("action", "my")
                .param(SecureStrings.path(95), AppToken.getToken())
                .param("page", String.valueOf(targetPage))
                .param("page_size", String.valueOf(PAGE_SIZE))
                .build();

        HttpManage.getInstance().get(url)
                .header(SecureStrings.path(96), UserToken.getToken())
                .onSuccess((code, body) -> {
                    if (requestId != loadRequestId) return;
                    try {
                        JSONObject resp = new JSONObject(body);
                        int respCode = resp.getInt("code");
                        if (code == 200 && respCode == 200) {
                            JSONObject data = resp.optJSONObject("data");
                            JSONArray arr = data != null ? data.optJSONArray("list") : null;
                            List<MyFavoritesAdapter.FavoriteItem> list = parseItems(arr);
                            int totalPages = data != null ? data.optInt("total_pages", 1) : 1;
                            boolean more = targetPage < totalPages;
                            runOnUiThread(() -> {
                                page = targetPage;
                                hasMore = more;
                                loading = false;
                                hasLoaded = true;
                                if (isRefresh) {
                                    adapter.setData(list);
                                    adapter.setLoadingMore(false);
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
                                if (isRefresh) {
                                    showError();
                                } else {
                                    adapter.setLoadingMore(false);
                                }
                            });
                        }
                    } catch (Exception e) {
                        AppLogger.e(TAG, "parse favorites failed: " + e.getMessage(), e);
                        runOnUiThread(() -> {
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
                    AppLogger.e(TAG, "load favorites failed: " + err);
                    runOnUiThread(() -> {
                        if (isRefresh) {
                            showError();
                        } else {
                            adapter.setLoadingMore(false);
                        }
                    });
                })
                .execute();
    }

    private List<MyFavoritesAdapter.FavoriteItem> parseItems(JSONArray arr) {
        List<MyFavoritesAdapter.FavoriteItem> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject obj = arr.getJSONObject(i);
                int targetAuthorUserId = obj.optInt("target_author_id", 0);
                list.add(new MyFavoritesAdapter.FavoriteItem(
                        obj.optInt("id", 0),
                        obj.optInt("target_type", 0),
                        obj.optInt("target_id", 0),
                        obj.optString("target_title", ""),
                        obj.optString("target_author_nickname", obj.optString("target_author_name", "")),
                        obj.optString("target_author_avatar", ""),
                        targetAuthorUserId,
                        obj.optString("target_section_name", ""),
                        obj.optString("target_content_summary", ""),
                        obj.optInt("target_views", 0),
                        obj.optInt("target_likes", 0),
                        obj.optInt("target_favorites", 0),
                        obj.optString("created_at", "")
                ));
            } catch (Exception e) {
                // skip
            }
        }
        return list;
    }

    private void showLoading() {
        binding.layoutLoading.setVisibility(View.VISIBLE);
        binding.layoutEmpty.setVisibility(View.GONE);
        binding.layoutError.setVisibility(View.GONE);
        binding.recyclerView.setVisibility(View.GONE);
    }

    private void restoreScroll() {
        if (restorePosition < 0) return;
        LinearLayoutManager lm = (LinearLayoutManager) binding.recyclerView.getLayoutManager();
        if (lm != null) {
            lm.scrollToPositionWithOffset(restorePosition, restoreOffset);
        }
        restorePosition = -1;
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
