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

import com.google.android.material.chip.Chip;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.HttpUtils.java.HttpClientJava;
import cn.panda.utils.library.UrlBuilder;
import io.takamiya.mio.adapter.PostSearchAdapter;
import io.takamiya.mio.bean.ForumSection;
import io.takamiya.mio.databinding.ActivityPostSearchBinding;
import io.takamiya.mio.utils.AppToken;
import io.takamiya.mio.utils.HttpManage;
import io.takamiya.mio.utils.UserToken;

public class PostSearchActivity extends AppCompatActivity {

    private static final String TAG = "PostSearchActivity";
    private static final int PAGE_SIZE = 20;

    private ActivityPostSearchBinding binding;
    private HttpClientJava client = HttpManage.getInstance();
    private PostSearchAdapter adapter;

    private long loadRequestId;
    private boolean loading;
    private int page = 1;
    private boolean hasMore = true;
    private String lastQuery = "";
    private int selectedSectionId = 0;

    private final List<ForumSection> sections = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPostSearchBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets();

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new PostSearchAdapter();
        adapter.setOnItemClickListener(item -> {
            Intent intent = new Intent(this, PostDetailActivity.class);
            intent.putExtra("POST_ID", item.id);
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

        binding.etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch();
                return true;
            }
            return false;
        });

        loadSections();
    }

    private void loadSections() {
        String url = UrlBuilder.getInstance()
                .longPath(SecureStrings.path(48))
                .param("action", "list")
                .param(SecureStrings.path(95), AppToken.getToken())
                .build();

        client.get(url)
                .header(SecureStrings.path(96), UserToken.getToken())
                .onSuccess((code, body) -> {
                    try {
                        JSONObject resp = new JSONObject(body);
                        int respCode = resp.getInt("code");
                        if (code == 200 && respCode == 200) {
                            JSONObject data = resp.optJSONObject("data");
                            JSONArray arr = data != null ? data.optJSONArray("list") : null;
                            List<ForumSection> list = new ArrayList<>();
                            if (arr != null) {
                                for (int i = 0; i < arr.length(); i++) {
                                    list.add(ForumSection.fromJson(arr.getJSONObject(i)));
                                }
                            }
                            runOnUiThread(() -> {
                                sections.clear();
                                sections.addAll(list);
                                setupSectionChips();
                                loadPosts();
                            });
                        }
                    } catch (Exception e) {
                        AppLogger.e(TAG, "load sections failed: " + e.getMessage(), e);
                    }
                })
                .onFailure((code, err) -> AppLogger.e(TAG, "load sections failed: " + err))
                .execute();
    }

    private void setupSectionChips() {
        binding.chipGroupSections.removeAllViews();

        Chip allChip = new Chip(this);
        allChip.setText("全部");
        allChip.setCheckable(true);
        allChip.setChecked(true);
        allChip.setOnClickListener(v -> {
            selectedSectionId = 0;
            refreshPosts();
        });
        binding.chipGroupSections.addView(allChip);

        for (ForumSection section : sections) {
            Chip chip = new Chip(this);
            chip.setText(section.getName());
            chip.setCheckable(true);
            chip.setOnClickListener(v -> {
                selectedSectionId = section.getId();
                refreshPosts();
            });
            binding.chipGroupSections.addView(chip);
        }
    }

    private void doSearch() {
        String query = binding.etSearch.getText().toString().trim();
        lastQuery = query;
        hideKeyboard();
        refreshPosts();
    }

    private void refreshPosts() {
        loading = true;
        hasMore = true;
        page = 1;
        long requestId = ++loadRequestId;
        showLoading();
        fetchPosts(requestId, 1, true);
    }

    private void loadNextPage() {
        if (loading || !hasMore) return;
        loading = true;
        long requestId = ++loadRequestId;
        adapter.setLoadingMore(true);
        fetchPosts(requestId, page + 1, false);
    }

    private void loadPosts() {
        loading = true;
        hasMore = true;
        page = 1;
        long requestId = ++loadRequestId;
        showLoading();
        fetchPosts(requestId, 1, true);
    }

    private void fetchPosts(long requestId, int targetPage, boolean isRefresh) {
        cn.panda.utils.library.UrlBuilder.Builder builder = UrlBuilder.getInstance()
                .longPath(SecureStrings.path(50))
                .param(SecureStrings.path(95), AppToken.getToken())
                .param("page", String.valueOf(targetPage))
                .param("page_size", String.valueOf(PAGE_SIZE));

        if (!lastQuery.isEmpty()) {
            builder = builder.param("keyword", lastQuery);
        }
        if (selectedSectionId > 0) {
            builder = builder.param("section_id", String.valueOf(selectedSectionId));
        }

        String url = builder.build();

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
                            List<PostSearchAdapter.PostItem> list = parseItems(arr);
                            int totalPages = data != null ? data.optInt("total_pages", 1) : 1;
                            boolean more = targetPage < totalPages;
                            runOnUiThread(() -> {
                                page = targetPage;
                                hasMore = more;
                                if (isRefresh) {
                                    adapter.setData(list);
                                    adapter.setLoadingMore(false);
                                    if (list.isEmpty()) {
                                        showEmpty();
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
                                    showEmpty();
                                } else {
                                    adapter.setLoadingMore(false);
                                }
                            });
                        }
                    } catch (Exception e) {
                        AppLogger.e(TAG, "parse posts failed: " + e.getMessage(), e);
                        runOnUiThread(() -> {
                            if (isRefresh) {
                                showEmpty();
                            } else {
                                adapter.setLoadingMore(false);
                            }
                        });
                    }
                })
                .onFailure((code, err) -> {
                    if (requestId != loadRequestId) return;
                    AppLogger.e(TAG, "load posts failed: " + err);
                    runOnUiThread(() -> {
                        if (isRefresh) {
                            showEmpty();
                        } else {
                            adapter.setLoadingMore(false);
                        }
                    });
                })
                .execute();
    }

    private List<PostSearchAdapter.PostItem> parseItems(JSONArray arr) {
        List<PostSearchAdapter.PostItem> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject obj = arr.getJSONObject(i);
                JSONObject authorObj = obj.optJSONObject("author");
                int authorUserId = authorObj != null ? authorObj.optInt("id", 0) : obj.optInt("author_id", 0);
                int authorMemberLevel = authorObj != null ? authorObj.optInt("member_level", 0) : obj.optInt("author_member_level", 0);
                list.add(new PostSearchAdapter.PostItem(
                        obj.optInt("id", 0),
                        obj.optString("title", ""),
                        obj.optString("content_summary", ""),
                        obj.optString("author_nickname", obj.optString("author_name", "")),
                        obj.optString("author_avatar", ""),
                        authorUserId,
                        authorMemberLevel,
                        obj.optString("section_name", ""),
                        obj.optString("created_at", ""),
                        obj.optInt("views", 0),
                        obj.optInt("likes", 0),
                        obj.optInt("favorites", 0),
                        obj.optInt("coins", 0)
                ));
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    private void showLoading() {
        binding.layoutLoading.setVisibility(View.VISIBLE);
        binding.layoutEmpty.setVisibility(View.GONE);
        binding.recyclerView.setVisibility(View.GONE);
    }

    private void showList() {
        binding.layoutLoading.setVisibility(View.GONE);
        binding.layoutEmpty.setVisibility(View.GONE);
        binding.recyclerView.setVisibility(View.VISIBLE);
    }

    private void showEmpty() {
        binding.layoutLoading.setVisibility(View.GONE);
        binding.layoutEmpty.setVisibility(View.VISIBLE);
        binding.recyclerView.setVisibility(View.GONE);
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
