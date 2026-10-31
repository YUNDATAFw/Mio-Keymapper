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
import io.takamiya.mio.CreateResourceActivity;
import io.takamiya.mio.ForumItemActions;
import io.takamiya.mio.ReportActivity;
import io.takamiya.mio.ResourceDetailActivity;
import io.takamiya.mio.adapter.ForumResourcesAdapter;
import io.takamiya.mio.adapter.SpaceItemDecoration;
import io.takamiya.mio.bean.ForumResource;
import io.takamiya.mio.databinding.FragmentForumResourcesBinding;
import io.takamiya.mio.utils.AppToken;
import io.takamiya.mio.utils.HttpManage;
import io.takamiya.mio.utils.ModerationUtil;
import io.takamiya.mio.utils.SectionModeratorUtil;
import io.takamiya.mio.utils.UserToken;

public class ForumResourcesFragment extends Fragment {

    private static final String TAG = "ForumResourcesFragment";
    private static final int PAGE_SIZE = 20;

    private FragmentForumResourcesBinding binding;
    private ForumResourcesAdapter adapter;
    private int currentSectionId;

    private long loadRequestId;
    private boolean loading;
    private boolean loaded;
    private int page = 1;
    private boolean hasMore = true;

    private boolean isSectionModerator;

    private String currentSortBy = "time";

    public static ForumResourcesFragment newInstance(int sectionId) {
        ForumResourcesFragment fragment = new ForumResourcesFragment();
        Bundle args = new Bundle();
        args.putInt("section_id", sectionId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentForumResourcesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            currentSectionId = args.getInt("section_id", 0);
        }

        adapter = new ForumResourcesAdapter();
        adapter.setOnItemClickListener(resource -> {
            Intent intent = new Intent(requireContext(), ResourceDetailActivity.class);
            intent.putExtra("RESOURCE_ID", resource.getId());
            startActivity(intent);
        });
        adapter.setOnItemLongClickListener(this::showResourceActions);
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
            Intent intent = new Intent(requireContext(), CreateResourceActivity.class);
            intent.putExtra("section_id", currentSectionId);
            startActivity(intent);
        });

        checkSectionModerator();
        setupSortChips();
        showLoading();
        loadFirstPage();
    }

    private void setupSortChips() {
        binding.chipGroupSort.removeAllViews();
        for (String[] option : new String[][]{
                {"最新", "time"}, {"最热", "heat"}, {"浏览", "views"},
                {"点赞", "likes"}, {"收藏", "favorites"}, {"收益", "coins"}}) {
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
        if (loaded) {
            loadFirstPage();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void loadFirstPage() {
        if (loading || !isAdded() || binding == null) return;
        page = 1;
        hasMore = true;
        loading = true;
        long requestId = ++loadRequestId;

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
                .longPath(SecureStrings.path(51))
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
                            List<ForumResource> list = arr != null
                                    ? ForumResource.fromJsonArray(arr) : new ArrayList<>();
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
                        AppLogger.e(TAG, "parse resources failed: " + e.getMessage(), e);
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
                    AppLogger.e(TAG, "load resources failed: " + err);
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
        FragmentForumResourcesBinding b = binding;
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
            FragmentForumResourcesBinding b = binding;
            if (b != null && b.getRoot() != null) {
                b.getRoot().post(() -> isSectionModerator = result);
            } else {
                isSectionModerator = result;
            }
        });
    }

    private void showResourceActions(ForumResource resource) {
        boolean isAuthor = UserToken.isLoggedIn() && resource.getUserId() == UserToken.getSub();
        ForumItemActions.show(requireContext(), "资源", resource.getTitle(), isAuthor,
                isSectionModerator, false, resource.isTop() == 1, resource.isRecommend() == 1,
                new ForumItemActions.Callbacks() {
                    @Override
                    public void onEdit() {
                    }

                    @Override
                    public void onDelete() {
                        confirmDeleteResource(resource);
                    }

                    @Override
                    public void onReport() {
                        openResourceReport(resource);
                    }

                    @Override
                    public void onSetTop(boolean top) {
                        ModerationUtil.setResourceTop(resource.getId(), top, (ok, msg) -> postToUiOnDone(() -> {
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                            if (ok) loadFirstPage();
                        }));
                    }

                    @Override
                    public void onSetRecommend(boolean recommend) {
                        ModerationUtil.setResourceRecommend(resource.getId(), recommend, (ok, msg) -> postToUiOnDone(() -> {
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                            if (ok) loadFirstPage();
                        }));
                    }
                });
    }

    private void confirmDeleteResource(ForumResource resource) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("删除资源")
                .setMessage("确定删除这个资源吗？删除后不可恢复。")
                .setPositiveButton("删除", (d, w) -> deleteResource(resource))
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteResource(ForumResource resource) {
        Map<String, Object> body = new HashMap<>();
        body.put("id", resource.getId());
        String url = UrlBuilder.getInstance()
                .longPath(SecureStrings.path(51))
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

    private void openResourceReport(ForumResource resource) {
        Intent intent = new Intent(requireContext(), ReportActivity.class);
        intent.putExtra("target_type", 2);
        intent.putExtra("target_id", resource.getId());
        intent.putExtra("target_content", resource.getTitle());
        startActivity(intent);
    }

    private void postToUiOnDone(Runnable action) {
        FragmentForumResourcesBinding b = binding;
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