package io.takamiya.mio;
import io.takamiya.mio.utils.SecureStrings;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

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
import io.takamiya.mio.adapter.UserSearchAdapter;
import io.takamiya.mio.databinding.ActivityUserSearchBinding;
import io.takamiya.mio.utils.AppToken;
import io.takamiya.mio.utils.HttpManage;
import io.takamiya.mio.utils.UserToken;

public class UserSearchActivity extends AppCompatActivity {

    private static final String TAG = "UserSearchActivity";
    private static final int PAGE_SIZE = 20;

    private ActivityUserSearchBinding binding;
    private HttpClientJava client = HttpManage.getInstance();
    private UserSearchAdapter adapter;

    private long loadRequestId;
    private boolean loading;
    private int page = 1;
    private boolean hasMore = true;
    private String lastQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUserSearchBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets();

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new UserSearchAdapter();
        adapter.setOnItemClickListener(item -> {
            Intent intent = new Intent(this, UserProfileActivity.class);
            intent.putExtra(UserProfileActivity.EXTRA_USER_ID, item.userId);
            intent.putExtra(UserProfileActivity.EXTRA_USERNAME, item.username);
            startActivity(intent);
        });

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);

        binding.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(androidx.recyclerview.widget.RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || !hasMore || loading) return;
                LinearLayoutManager lm = (LinearLayoutManager) binding.recyclerView.getLayoutManager();
                if (lm == null) return;
                if (lm.findLastVisibleItemPosition() >= adapter.getItemCount() - 3) {
                    loadNextPage();
                }
            }
        });

        binding.etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch();
                return true;
            }
            return false;
        });

        binding.etSearch.requestFocus();
        showKeyboard();
    }

    private void doSearch() {
        String query = binding.etSearch.getText().toString().trim();
        if (query.isEmpty()) return;

        hideKeyboard();
        lastQuery = query;
        loading = true;
        hasMore = true;
        page = 1;
        long requestId = ++loadRequestId;

        showLoading();
        searchUsers(requestId, 1, true);
    }

    private void loadNextPage() {
        if (loading || !hasMore || lastQuery.isEmpty()) return;
        loading = true;
        long requestId = ++loadRequestId;
        adapter.setLoadingMore(true);
        searchUsers(requestId, page + 1, false);
    }

    private void searchUsers(long requestId, int targetPage, boolean isRefresh) {
        String url = UrlBuilder.getInstance()
                .longPath(SecureStrings.path(58))
                .param("keyword", lastQuery)
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
                            JSONObject pagination = data != null ? data.optJSONObject("pagination") : null;
                            List<UserSearchAdapter.UserItem> list = parseItems(arr);
                            int totalPages = pagination != null ? pagination.optInt("totalPages", 1) : 1;
                            boolean more = targetPage < totalPages;
                            runOnUiThread(() -> {
                                page = targetPage;
                                hasMore = more;
                                if (isRefresh) {
                                    adapter.setData(list);
                                    adapter.setLoadingMore(false);
                                    if (list.isEmpty()) {
                                        showNoResult();
                                    } else {
                                        showList();
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
                                    showNoResult();
                                } else {
                                    adapter.setLoadingMore(false);
                                }
                            });
                        }
                    } catch (Exception e) {
                        AppLogger.e(TAG, "parse search result failed: " + e.getMessage(), e);
                        runOnUiThread(() -> {
                            if (isRefresh) {
                                showNoResult();
                            } else {
                                adapter.setLoadingMore(false);
                            }
                        });
                    }
                })
                .onFailure((code, err) -> {
                    if (requestId != loadRequestId) return;
                    AppLogger.e(TAG, "search users failed: " + err);
                    runOnUiThread(() -> {
                        if (isRefresh) {
                            showNoResult();
                        } else {
                            adapter.setLoadingMore(false);
                        }
                    });
                })
                .execute();
    }

    private List<UserSearchAdapter.UserItem> parseItems(JSONArray arr) {
        List<UserSearchAdapter.UserItem> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject obj = arr.getJSONObject(i);
                JSONObject stats = obj.optJSONObject("stats");
                int fansCount = stats != null ? stats.optInt("fans_count", 0) : 0;
                int postsCount = stats != null ? stats.optInt("posts_count", 0) : 0;
                int resourcesCount = stats != null ? stats.optInt("resources_count", 0) : 0;
                list.add(new UserSearchAdapter.UserItem(
                        obj.optInt("id", 0),
                        obj.optString("username", ""),
                        obj.optString("nickname", ""),
                        obj.optString("avatar", ""),
                        obj.optInt("member_level", 0),
                        obj.optInt("user_level", 0),
                        obj.optString("user_level_title", ""),
                        fansCount,
                        postsCount,
                        resourcesCount
                ));
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    private void showLoading() {
        binding.layoutLoading.setVisibility(View.VISIBLE);
        binding.layoutEmpty.setVisibility(View.GONE);
        binding.layoutNoResult.setVisibility(View.GONE);
        binding.recyclerView.setVisibility(View.GONE);
    }

    private void showList() {
        binding.layoutLoading.setVisibility(View.GONE);
        binding.layoutEmpty.setVisibility(View.GONE);
        binding.layoutNoResult.setVisibility(View.GONE);
        binding.recyclerView.setVisibility(View.VISIBLE);
    }

    private void showNoResult() {
        binding.layoutLoading.setVisibility(View.GONE);
        binding.layoutEmpty.setVisibility(View.GONE);
        binding.layoutNoResult.setVisibility(View.VISIBLE);
        binding.recyclerView.setVisibility(View.GONE);
    }

    private void showKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(binding.etSearch.getWindowToken(), 0);
        }
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
