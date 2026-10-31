package io.takamiya.mio.navigation;
import io.takamiya.mio.utils.SecureStrings;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
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
import io.takamiya.mio.ForumItemActions;
import io.takamiya.mio.PostDetailActivity;
import io.takamiya.mio.ReportActivity;
import io.takamiya.mio.adapter.ForumPostsAdapter;
import io.takamiya.mio.adapter.SpaceItemDecoration;
import io.takamiya.mio.bean.ForumPost;
import io.takamiya.mio.databinding.FragmentForumPostsBinding;
import io.takamiya.mio.utils.AppToken;
import io.takamiya.mio.utils.HttpManage;
import io.takamiya.mio.utils.ModerationUtil;
import io.takamiya.mio.utils.SectionModeratorUtil;
import io.takamiya.mio.utils.UserToken;

public class ForumPostsFragment extends Fragment {

    private static final String TAG = "ForumPostsFragment";
    private static final int PAGE_SIZE = 20;

    private FragmentForumPostsBinding binding;
    private ForumPostsAdapter adapter;
    private int currentSectionId;

    private long loadRequestId;
    private boolean loading;
    private boolean loaded;
    private int page = 1;
    private boolean hasMore = true;

    private boolean isSectionModerator;

    private String currentSortBy = "time";

    public static ForumPostsFragment newInstance(int sectionId) {
        ForumPostsFragment fragment = new ForumPostsFragment();
        Bundle args = new Bundle();
        args.putInt("section_id", sectionId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentForumPostsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            currentSectionId = args.getInt("section_id", 0);
        }

        adapter = new ForumPostsAdapter();
        adapter.setOnItemClickListener(post -> {
            int id = post.getId();
            Intent intent = new Intent(requireContext(), PostDetailActivity.class);
            intent.putExtra("POST_ID",id);
            startActivity(intent);
        });
        adapter.setOnItemLongClickListener(this::showPostActions);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.addItemDecoration(new SpaceItemDecoration(dp(8)));
        binding.recyclerView.setPadding(dp(12), dp(8), dp(12), dp(8));
        binding.recyclerView.setAdapter(adapter);

        binding.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || !hasMore || loading || !isAdded() || binding == null) return;
                LinearLayoutManager lm = (LinearLayoutManager) binding.recyclerView.getLayoutManager();
                if (lm == null) return;
                int lastVisible = lm.findLastVisibleItemPosition();
                if (lastVisible >= lm.getItemCount() - 4) {
                    loadNextPage();
                }
            }
        });

        binding.btnRetry.setOnClickListener(v -> loadFirstPage());
        binding.swipeRefresh.setOnRefreshListener(this::loadFirstPage);

        binding.fabCreate.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), io.takamiya.mio.CreatePostActivity.class);
            intent.putExtra("section_id", currentSectionId);
            startActivity(intent);
        });

        checkSectionModerator();
        setupSortChips();
        showLoading();
        loadPosts();
    }

    private void setupSortChips() {
        binding.chipGroupSort.removeAllViews();
        for (String[] option : new String[][]{
                {"最新", "time"}, {"最热", "heat"}, {"浏览", "views"},
                {"点赞", "likes"}, {"收藏", "favorites"}}) {
            com.google.android.material.chip.Chip chip =
                    new com.google.android.material.chip.Chip(requireContext());
            chip.setText(option[0]);
            chip.setTag(option[1]);
            chip.setCheckable(true);
            chip.setChecked(option[1].equals(currentSortBy));
            binding.chipGroupSort.addView(chip);
        }
        binding.chipGroupSort.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            com.google.android.material.chip.Chip chip = group.findViewById(checkedIds.get(0));
            if (chip == null || chip.getTag() == null) return;
            String sortBy = chip.getTag().toString();
            if (!sortBy.equals(currentSortBy)) {
                currentSortBy = sortBy;
                loadFirstPage();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // 首次 onResume 时首个请求尚在途（loaded=false），不会重复请求
        if (loaded) {
            loadPosts();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 置空后回调查用 binding 前都会被 isAdded/binding 判空拦截；
        // 请求无法取消，通过 loadRequestId 丢弃过期响应
        binding = null;
    }

    private void loadPosts() {
        loadFirstPage();
    }

    private void loadFirstPage() {
        if (loading || !isAdded() || binding == null) return;
        page = 1;
        hasMore = true;
        loading = true;
        long requestId = ++loadRequestId;

        // 已有数据时静默刷新，不打断列表，避免回到页面闪 loading
        if (adapter.getItemCount() == 0) {
            showLoading();
        }

        fetchPage(requestId, 1, true);
    }

    private void loadNextPage() {
        if (loading || !hasMore || !isAdded() || binding == null) return;
        loading = true;
        long requestId = ++loadRequestId;
        adapter.setLoadingMore(true);
        fetchPage(requestId, page + 1, false);
    }

    private void fetchPage(long requestId, int targetPage, boolean isRefresh) {
        String url = UrlBuilder.getInstance()
                .longPath(SecureStrings.path(50))
                .param("action", "list")
                .param("section_id", String.valueOf(currentSectionId))
                .param("sort_by", currentSortBy)
                .param("sort_order", "DESC")
                .param("page", String.valueOf(targetPage))
                .param("page_size", String.valueOf(PAGE_SIZE))
                .param(SecureStrings.path(95), AppToken.getToken())
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
                            List<ForumPost> list = arr != null ? ForumPost.fromJsonArray(arr) : new ArrayList<>();
                            int totalPages = data != null ? data.optInt("total_pages", 1) : 1;
                            boolean more = targetPage < totalPages;
                            postToUi(requestId, () -> {
                                page = targetPage;
                                hasMore = more;
                                if (isRefresh) {
                                    adapter.setData(list);
                                    adapter.setLoadingMore(false);
                                    loaded = true;
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
                            postToUi(requestId, () -> {
                                if (isRefresh) {
                                    showError();
                                } else {
                                    adapter.setLoadingMore(false);
                                }
                            });
                        }
                    } catch (Exception e) {
                        AppLogger.e(TAG, "parse posts failed: " + e.getMessage(), e);
                        postToUi(requestId, () -> {
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
                    AppLogger.e(TAG, "load posts failed: " + err);
                    postToUi(requestId, () -> {
                        if (isRefresh) {
                            showError();
                        } else {
                            adapter.setLoadingMore(false);
                        }
                    });
                })
                .execute();
    }

    private void postToUi(long requestId, Runnable action) {
        FragmentForumPostsBinding b = binding;
        View root = b != null ? b.getRoot() : null;
        if (root == null || root.getHandler() == null) return;
        root.post(() -> {
            if (requestId != loadRequestId || binding == null) return;
            loading = false;
            binding.swipeRefresh.setRefreshing(false);
            action.run();
        });
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

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
        // 已有数据时刷新失败：保留列表，不切错误页
        if (adapter.getItemCount() > 0) {
            showList();
            return;
        }
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

    private void checkSectionModerator() {
        SectionModeratorUtil.check(currentSectionId, result -> {
            FragmentForumPostsBinding b = binding;
            if (b != null && b.getRoot() != null) {
                b.getRoot().post(() -> isSectionModerator = result);
            } else {
                isSectionModerator = result;
            }
        });
    }

    private void showPostActions(ForumPost post) {
        boolean isAuthor = UserToken.isLoggedIn() && post.getUserId() == UserToken.getSub();
        ForumItemActions.show(requireContext(), "帖子", post.getTitle(), isAuthor,
                isSectionModerator, isAuthor, post.isTop() == 1, post.isRecommend() == 1,
                new ForumItemActions.Callbacks() {
                    @Override
                    public void onEdit() {
                        Toast.makeText(requireContext(), "修改帖子功能未开放，敬请期待", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onDelete() {
                        confirmDeletePost(post);
                    }

                    @Override
                    public void onReport() {
                        openPostReport(post);
                    }

                    @Override
                    public void onSetTop(boolean top) {
                        ModerationUtil.setPostTop(post.getId(), top, (ok, msg) -> postToUiOnDone(() -> {
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                            if (ok) loadFirstPage();
                        }));
                    }

                    @Override
                    public void onSetRecommend(boolean recommend) {
                        ModerationUtil.setPostRecommend(post.getId(), recommend, (ok, msg) -> postToUiOnDone(() -> {
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                            if (ok) loadFirstPage();
                        }));
                    }
                });
    }

    private void confirmDeletePost(ForumPost post) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("删除帖子")
                .setMessage("确定删除这篇帖子吗？删除后不可恢复。")
                .setPositiveButton("删除", (d, w) -> deletePost(post))
                .setNegativeButton("取消", null)
                .show();
    }

    private void deletePost(ForumPost post) {
        Map<String, Object> body = new HashMap<>();
        body.put("id", post.getId());
        String url = UrlBuilder.getInstance()
                .longPath(SecureStrings.path(50))
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
                            postToUiOnDone(() -> {
                                Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show();
                                loadFirstPage();
                            });
                        } else {
                            String msg = respJson.optString("msg", "删除失败");
                            postToUiOnDone(() -> Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show());
                        }
                    } catch (Exception e) {
                        postToUiOnDone(() -> Toast.makeText(requireContext(), "删除失败", Toast.LENGTH_SHORT).show());
                    }
                })
                .onFailure((code, err) -> postToUiOnDone(() ->
                        Toast.makeText(requireContext(), "网络异常：" + err, Toast.LENGTH_SHORT).show()))
                .execute();
    }

    private void openPostReport(ForumPost post) {
        Intent intent = new Intent(requireContext(), ReportActivity.class);
        intent.putExtra("target_type", 1);
        intent.putExtra("target_id", post.getId());
        intent.putExtra("target_content", post.getTitle());
        startActivity(intent);
    }

    private void postToUiOnDone(Runnable action) {
        FragmentForumPostsBinding b = binding;
        View root = b != null ? b.getRoot() : null;
        if (root == null || root.getHandler() == null) {
            if (isAdded() && binding != null) {
                action.run();
            }
            return;
        }
        root.post(action);
    }
}
