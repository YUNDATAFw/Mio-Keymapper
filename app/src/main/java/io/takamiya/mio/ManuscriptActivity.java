package io.takamiya.mio;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.HttpUtils.java.HttpClientJava;
import cn.panda.utils.library.UrlBuilder;
import io.takamiya.mio.adapter.ManuscriptAdapter;
import io.takamiya.mio.bean.ForumPost;
import io.takamiya.mio.bean.ForumResource;
import io.takamiya.mio.bean.ManuscriptItem;
import io.takamiya.mio.databinding.ActivityManuscriptBinding;
import io.takamiya.mio.databinding.DialogManuscriptActionBinding;
import io.takamiya.mio.utils.AppToken;
import io.takamiya.mio.utils.HttpManage;
import io.takamiya.mio.utils.SecureStrings;
import io.takamiya.mio.utils.UserToken;
import o.s.o.l;

public class ManuscriptActivity extends AppCompatActivity {

    private static final String TAG = "ManuscriptActivity";
    private static final int PAGE_SIZE = 20;

    private ActivityManuscriptBinding binding;
    private HttpClientJava client = HttpManage.getInstance();
    private ManuscriptAdapter postAdapter = new ManuscriptAdapter();
    private ManuscriptAdapter resourceAdapter = new ManuscriptAdapter();

    private int currentTab;
    private boolean loading;
    private long loadRequestId;
    private boolean hasLoaded;
    private boolean silentRefresh;
    private int restorePosition = -1;
    private int restoreOffset;

    private int postPage = 1;
    private int resourcePage = 1;
    private boolean postHasMore = true;
    private boolean resourceHasMore = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityManuscriptBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets();

        if (!UserToken.isLoggedIn()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        postAdapter.setOnItemClickListener(this::showActions);
        resourceAdapter.setOnItemClickListener(this::showActions);

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.addItemDecoration(new io.takamiya.mio.adapter.SpaceItemDecoration((int) (8 * getResources().getDisplayMetrics().density)));

        binding.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || loading) return;
                ManuscriptAdapter adapter = currentAdapter();
                if (!(currentTab == 0 ? postHasMore : resourceHasMore)) return;
                LinearLayoutManager lm = (LinearLayoutManager) binding.recyclerView.getLayoutManager();
                if (lm != null && lm.findLastVisibleItemPosition() >= adapter.getItemCount() - 3) {
                    loadNextPage();
                }
            }
        });

        binding.tabLayout.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                refreshCurrentTab();
            }

            @Override
            public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {
            }
        });

        binding.btnRetry.setOnClickListener(v -> refreshCurrentTab());

        loading = true;
        ++loadRequestId;
        showLoading();
        fetchPosts(1, true);
    }

    private ManuscriptAdapter currentAdapter() {
        return currentTab == 0 ? postAdapter : resourceAdapter;
    }

    private void refreshCurrentTab() {
        loading = true;
        ++loadRequestId;
        silentRefresh = false;
        restorePosition = -1;
        if (currentTab == 0) {
            postPage = 1;
            postHasMore = true;
            showLoading();
            fetchPosts(1, true);
        } else {
            resourcePage = 1;
            resourceHasMore = true;
            showLoading();
            fetchResources(1, true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!hasLoaded || loading || currentAdapter().getItemCount() == 0) return;
        LinearLayoutManager lm = (LinearLayoutManager) binding.recyclerView.getLayoutManager();
        if (lm != null && lm.getChildCount() > 0) {
            restorePosition = lm.findFirstVisibleItemPosition();
            View first = lm.getChildAt(0);
            restoreOffset = first != null ? first.getTop() : 0;
        }
        if (currentTab == 0) {
            postPage = 1;
            postHasMore = true;
            silentRefresh = true;
            loading = true;
            ++loadRequestId;
            fetchPosts(1, true);
        } else {
            resourcePage = 1;
            resourceHasMore = true;
            silentRefresh = true;
            loading = true;
            ++loadRequestId;
            fetchResources(1, true);
        }
    }

    private void loadNextPage() {
        if (loading) return;
        loading = true;
        ++loadRequestId;
        currentAdapter().setLoadingMore(true);
        if (currentTab == 0) {
            fetchPosts(postPage + 1, false);
        } else {
            fetchResources(resourcePage + 1, false);
        }
    }

    private void fetchPosts(int targetPage, boolean isRefresh) {
        long requestId = loadRequestId;
        String url = UrlBuilder.getInstance()
                .longPath(SecureStrings.path(50))
                .param("action", "my")
                .param(SecureStrings.path(95), AppToken.getToken())
                .param("page", String.valueOf(targetPage))
                .param("page_size", String.valueOf(PAGE_SIZE))
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
                            final List<ManuscriptItem> list = new ArrayList<>();
                            if (arr != null) {
                                for (int i = 0; i < arr.length(); i++) {
                                    ForumPost post = ForumPost.fromJson(arr.getJSONObject(i));
                                    list.add(new ManuscriptItem(
                                            ManuscriptItem.TYPE_POST,
                                            post.getId(),
                                            post.getTitle(),
                                            post.getSectionName(),
                                            post.getAuditStatus(),
                                            post.getViews(),
                                            post.getLikes(),
                                            post.getCreatedAt()));
                                }
                            }
                            int total = data != null ? data.optInt("total", -1) : -1;
                            final boolean more = total > 0
                                    ? targetPage * PAGE_SIZE < total
                                    : !list.isEmpty() && list.size() == PAGE_SIZE;
                            runOnUiThread(() -> {
                                postPage = targetPage;
                                postHasMore = more;
                                applyPosts(list, isRefresh);
                            });
                        } else {
                            failLoading(isRefresh);
                        }
                    } catch (Exception e) {
                        AppLogger.e(TAG, "parse posts failed: " + e.getMessage(), e);
                        failLoading(isRefresh);
                    }
                })
                .onFailure((code, err) -> {
                    if (requestId != loadRequestId) return;
                    AppLogger.e(TAG, "load posts failed: " + err);
                    failLoading(isRefresh);
                })
                .execute();
    }

    private void fetchResources(int targetPage, boolean isRefresh) {
        long requestId = loadRequestId;
        String url = UrlBuilder.getInstance()
                .longPath(SecureStrings.path(51))
                .param("action", "my")
                .param(SecureStrings.path(95), AppToken.getToken())
                .param("page", String.valueOf(targetPage))
                .param("page_size", String.valueOf(PAGE_SIZE))
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
                            final List<ManuscriptItem> list = new ArrayList<>();
                            if (arr != null) {
                                for (int i = 0; i < arr.length(); i++) {
                                    ForumResource res = ForumResource.fromJson(arr.getJSONObject(i));
                                    list.add(new ManuscriptItem(
                                            ManuscriptItem.TYPE_RESOURCE,
                                            res.getId(),
                                            res.getTitle(),
                                            res.getSectionName(),
                                            res.getAuditStatus(),
                                            res.getViews(),
                                            res.getLikes(),
                                            res.getCreatedAt()));
                                }
                            }
                            int total = data != null ? data.optInt("total", -1) : -1;
                            final boolean more = total > 0
                                    ? targetPage * PAGE_SIZE < total
                                    : !list.isEmpty() && list.size() == PAGE_SIZE;
                            runOnUiThread(() -> {
                                resourcePage = targetPage;
                                resourceHasMore = more;
                                applyResources(list, isRefresh);
                            });
                        } else {
                            failLoading(isRefresh);
                        }
                    } catch (Exception e) {
                        AppLogger.e(TAG, "parse resources failed: " + e.getMessage(), e);
                        failLoading(isRefresh);
                    }
                })
                .onFailure((code, err) -> {
                    if (requestId != loadRequestId) return;
                    AppLogger.e(TAG, "load resources failed: " + err);
                    failLoading(isRefresh);
                })
                .execute();
    }

    private void applyPosts(List<ManuscriptItem> list, boolean isRefresh) {
        postAdapter.setLoadingMore(false);
        if (isRefresh) {
            postAdapter.setData(list);
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
            postAdapter.appendData(list);
            if (list.isEmpty()) postHasMore = false;
        }
        finishLoadingUI();
    }

    private void applyResources(List<ManuscriptItem> list, boolean isRefresh) {
        resourceAdapter.setLoadingMore(false);
        if (isRefresh) {
            resourceAdapter.setData(list);
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
            resourceAdapter.appendData(list);
            if (list.isEmpty()) resourceHasMore = false;
        }
        finishLoadingUI();
    }

    private void finishLoadingUI() {
        loading = false;
        hasLoaded = true;
    }

    private void failLoading(boolean isRefresh) {
        runOnUiThread(() -> {
            loading = false;
            currentAdapter().setLoadingMore(false);
            if (isRefresh) {
                showError();
            }
        });
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
        binding.recyclerView.setAdapter(currentAdapter());
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

    // ══════════════════════════════════════════════
    //  稿件操作（BottomSheet）
    // ══════════════════════════════════════════════

    private void showActions(ManuscriptItem item) {
        DialogManuscriptActionBinding b =
                DialogManuscriptActionBinding.inflate(LayoutInflater.from(this));
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(b.getRoot());

        b.msoTitle.setText(truncate(item.getTitle()));

        b.viewRow.setOnClickListener(v -> {
            dialog.dismiss();
            openDetail(item);
        });
        b.editRow.setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(this, "修改功能未开放，敬请期待", Toast.LENGTH_SHORT).show();
        });
        boolean canAppeal = item.getAuditStatus() == 3;
        b.appealRow.setVisibility(canAppeal ? View.VISIBLE : View.GONE);
        b.appealRow.setOnClickListener(v -> {
            dialog.dismiss();
            showAppealDialog(item);
        });
        b.deleteRow.setOnClickListener(v -> {
            dialog.dismiss();
            confirmDelete(item);
        });

        dialog.show();
    }

    private void openDetail(ManuscriptItem item) {
        if (item.getType() == ManuscriptItem.TYPE_POST) {
            Intent intent = new Intent(this, PostDetailActivity.class);
            intent.putExtra("POST_ID", item.getId());
            startActivity(intent);
        } else {
            Intent intent = new Intent(this, ResourceDetailActivity.class);
            intent.putExtra("RESOURCE_ID", item.getId());
            startActivity(intent);
        }
    }

    private void showAppealDialog(ManuscriptItem item) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_text, null);
        EditText input = view.findViewById(R.id.editInput);
        input.setHint("请输入申诉理由（1000 字以内）");

        new MaterialAlertDialogBuilder(this)
                .setTitle("申请复核")
                .setView(view)
                .setPositiveButton("提交", (d, w) -> {
                    String reason = input.getText().toString().trim();
                    if (reason.isEmpty()) {
                        Toast.makeText(this, "请填写申诉理由", Toast.LENGTH_SHORT).show();
                    } else {
                        submitAppeal(item, reason);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void submitAppeal(ManuscriptItem item, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("target_type", item.getType() == ManuscriptItem.TYPE_POST ? 1 : 2);
        body.put("target_id", item.getId());
        body.put("appeal_reason", reason);

        String url = UrlBuilder.getInstance()
                .longPath(l.get().a()[102])
                .param("action", "appeal")
                .param(SecureStrings.path(95), AppToken.getToken())
                .build();

        client.post(url)
                .header(SecureStrings.path(96), UserToken.getToken())
                .json(body)
                .onSuccess((code, resp) -> {
                    try {
                        JSONObject info = new JSONObject(resp.trim());
                        if (code == 200 && info.getInt("code") == 200) {
                            runOnUiThread(() -> {
                                Toast.makeText(this, "申诉已提交，等待复核", Toast.LENGTH_SHORT).show();
                                refreshCurrentTab();
                            });
                        } else {
                            runOnUiThread(() -> Toast.makeText(this,
                                    info.optString("msg", "提交失败"), Toast.LENGTH_SHORT).show());
                        }
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(this, "提交失败", Toast.LENGTH_SHORT).show());
                    }
                })
                .onFailure((code, err) -> runOnUiThread(() ->
                        Toast.makeText(this, "网络异常：" + err, Toast.LENGTH_SHORT).show()))
                .execute();
    }

    private void confirmDelete(ManuscriptItem item) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("删除稿件")
                .setMessage("确定删除「" + truncate(item.getTitle()) + "」？\n删除后不可恢复。")
                .setPositiveButton("删除", (d, w) -> deleteItem(item))
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteItem(ManuscriptItem item) {
        Map<String, Object> body = new HashMap<>();
        body.put("id", item.getId());
        String url = UrlBuilder.getInstance()
                .longPath(item.getType() == ManuscriptItem.TYPE_POST
                        ? SecureStrings.path(50) : SecureStrings.path(51))
                .param("action", "delete")
                .param(SecureStrings.path(95), AppToken.getToken())
                .build();

        client.delete(url)
                .header(SecureStrings.path(96), UserToken.getToken())
                .json(body)
                .onSuccess((code, resp) -> {
                    try {
                        JSONObject info = new JSONObject(resp.trim());
                        if (code == 200 && info.getInt("code") == 200) {
                            runOnUiThread(() -> {
                                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                                refreshCurrentTab();
                            });
                        } else {
                            runOnUiThread(() -> Toast.makeText(this,
                                    info.optString("msg", "删除失败"), Toast.LENGTH_SHORT).show());
                        }
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show());
                    }
                })
                .onFailure((code, err) -> runOnUiThread(() ->
                        Toast.makeText(this, "网络异常：" + err, Toast.LENGTH_SHORT).show()))
                .execute();
    }

    private static String truncate(String text) {
        String s = (text == null ? "" : text).replaceAll("\\s+", " ").trim();
        if (s.length() <= 18) return s.isEmpty() ? "稿件" : s;
        return s.substring(0, 18) + "…";
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
