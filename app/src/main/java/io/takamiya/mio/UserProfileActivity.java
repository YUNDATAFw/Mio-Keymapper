package io.takamiya.mio;
import io.takamiya.mio.utils.SecureStrings;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.HttpUtils.java.HttpClientJava;
import cn.panda.utils.library.UrlBuilder;
import io.takamiya.mio.adapter.ForumPostsAdapter;
import io.takamiya.mio.adapter.ForumResourcesAdapter;
import io.takamiya.mio.bean.ForumPost;
import io.takamiya.mio.bean.ForumResource;
import io.takamiya.mio.databinding.ActivityUserProfileBinding;
import io.takamiya.mio.utils.AppToken;
import io.takamiya.mio.utils.HttpManage;
import io.takamiya.mio.utils.UserToken;

public class UserProfileActivity extends AppCompatActivity {

    private static final String TAG = "UserProfileActivity";
    private static final int TAB_POSTS = 0;
    private static final int TAB_RESOURCES = 1;

    public static final String EXTRA_USER_ID = "USER_ID";
    public static final String EXTRA_USERNAME = "USERNAME";

    private ActivityUserProfileBinding binding;
    private HttpClientJava client = HttpManage.getInstance();

    private int userId;
    private String username;
    private long loadRequestId;

    private ForumPostsAdapter postsAdapter;
    private ForumResourcesAdapter resourcesAdapter;
    private final List<Integer> tabOrder = new ArrayList<>();
    private int currentTab = TAB_POSTS;
    private boolean postsLoaded = false;
    private boolean resourcesLoaded = false;
    private boolean isFollowing = false;
    private boolean isSelf = false;
    private int postsCount;
    private int resourcesCount;
    private static final int PAGE_SIZE = 20;
    private int postsPage = 1;
    private int resourcesPage = 1;
    private boolean postsHasMore = true;
    private boolean resourcesHasMore = true;
    private boolean postsLoading = false;
    private boolean resourcesLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUserProfileBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets();

        userId = getIntent().getIntExtra(EXTRA_USER_ID, 0);
        username = getIntent().getStringExtra(EXTRA_USERNAME);

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        if (username != null) {
            binding.toolbar.setTitle(username);
        }

        setupTabs();
        setupPostsList();
        setupResourcesList();

        binding.scrollView.setOnScrollChangeListener(new androidx.core.widget.NestedScrollView.OnScrollChangeListener() {
            @Override
            public void onScrollChange(androidx.core.widget.NestedScrollView v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                if (scrollY <= oldScrollY) return;
                if (v.getChildCount() == 0) return;
                View content = v.getChildAt(0);
                int reachBottom = content.getHeight() - v.getHeight() - 200;
                if (scrollY < reachBottom) return;
                if (currentTab == TAB_POSTS) {
                    loadMoreUserPosts();
                } else {
                    loadMoreUserResources();
                }
            }
        });

        binding.btnFollow.setOnClickListener(v -> toggleFollow());
        binding.btnChat.setOnClickListener(v -> openChat());
        binding.btnRetry.setOnClickListener(v -> loadProfile());
        loadProfile();
    }

    private void setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                if (tab.getPosition() < tabOrder.size()) {
                    currentTab = tabOrder.get(tab.getPosition());
                }
                applyTabSelection();
            }

            @Override
            public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {
            }
        });
    }

    private void applyTabSelection() {
        if (currentTab == TAB_POSTS) {
            binding.recyclerContributions.setAdapter(postsAdapter);
            if (!postsLoaded) loadUserPosts();
        } else {
            binding.recyclerContributions.setAdapter(resourcesAdapter);
            if (!resourcesLoaded) loadUserResources();
        }
    }

    private void setupPostsList() {
        postsAdapter = new ForumPostsAdapter();
        postsAdapter.setProfileClickEnabled(false);
        postsAdapter.setOnItemClickListener(post -> {
            Intent intent = new Intent(this, PostDetailActivity.class);
            intent.putExtra("POST_ID", post.getId());
            startActivity(intent);
        });
        binding.recyclerContributions.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerContributions.setAdapter(postsAdapter);
        binding.recyclerContributions.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(android.graphics.Rect outRect, android.view.View view, RecyclerView parent, RecyclerView.State state) {
                outRect.bottom = 8;
            }
        });
    }

    private void setupResourcesList() {
        resourcesAdapter = new ForumResourcesAdapter();
        resourcesAdapter.setProfileClickEnabled(false);
        resourcesAdapter.setOnItemClickListener(resource -> {
            Intent intent = new Intent(this, ResourceDetailActivity.class);
            intent.putExtra("RESOURCE_ID", resource.getId());
            startActivity(intent);
        });
    }

    private void loadProfile() {
        long requestId = ++loadRequestId;
        showLoading();
        postsLoaded = false;
        resourcesLoaded = false;

        String url = UrlBuilder.getInstance()
                .longPath(SecureStrings.path(6))
                .param("userid", String.valueOf(userId))
                .param(SecureStrings.path(95), AppToken.getToken())
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
                            if (data != null) {
                                runOnUiThread(() -> {
                                    applyProfile(data);
                                    showContent();
                                    checkSelfAndFollowStatus();
                                });
                            } else {
                                runOnUiThread(this::showError);
                            }
                        } else {
                            runOnUiThread(this::showError);
                        }
                    } catch (Exception e) {
                        AppLogger.e(TAG, "parse profile failed: " + e.getMessage(), e);
                        runOnUiThread(this::showError);
                    }
                })
                .onFailure((code, err) -> {
                    if (requestId != loadRequestId) return;
                    AppLogger.e(TAG, "load profile failed: " + err);
                    runOnUiThread(this::showError);
                })
                .execute();
    }

    private void checkSelfAndFollowStatus() {
        isSelf = UserToken.getSub() == userId;
        if (isSelf) {
            binding.btnFollow.setVisibility(View.GONE);
        } else {
            binding.btnFollow.setVisibility(View.VISIBLE);
            loadFollowStatus();
        }
    }

    private void loadFollowStatus() {
        String url = UrlBuilder.getInstance()
                .longPath(SecureStrings.path(57))
                .param("action", "status")
                .param("user_id", String.valueOf(userId))
                .param(SecureStrings.path(95), AppToken.getToken())
                .build();

        client.get(url)
                .header(SecureStrings.path(96), UserToken.getToken())
                .onSuccess((code, body) -> {
                    try {
                        JSONObject resp = new JSONObject(body);
                        if (code == 200 && resp.getInt("code") == 200) {
                            JSONObject data = resp.optJSONObject("data");
                            if (data != null) {
                                isFollowing = data.optBoolean("is_following", false);
                                runOnUiThread(this::applyFollowState);
                            }
                        }
                    } catch (Exception e) {
                        AppLogger.e(TAG, "load follow status failed: " + e.getMessage(), e);
                    }
                })
                .onFailure((code, err) -> AppLogger.e(TAG, "load follow status failed: " + err))
                .execute();
    }

    private void toggleFollow() {
        if (isFollowing) {
            unfollowUser();
        } else {
            followUser();
        }
    }

    private void followUser() {
        Map<String, Object> body = new HashMap<>();
        body.put("user_id", userId);

        client.post(UrlBuilder.getInstance()
                        .longPath(SecureStrings.path(57))
                        .param(SecureStrings.path(95), AppToken.getToken())
                        .build())
                .header(SecureStrings.path(96), UserToken.getToken())
                .json(body)
                .onSuccess((code, respBody) -> {
                    try {
                        JSONObject resp = new JSONObject(respBody);
                        if (code == 200 && resp.getInt("code") == 200) {
                            isFollowing = true;
                            runOnUiThread(() -> {
                                applyFollowState();
                                adjustFollowerCount(1);
                            });
                        }
                    } catch (Exception e) {
                        AppLogger.e(TAG, "follow failed: " + e.getMessage(), e);
                    }
                })
                .onFailure((code, err) -> AppLogger.e(TAG, "follow failed: " + err))
                .execute();
    }

    private void unfollowUser() {
        Map<String, Object> body = new HashMap<>();
        body.put("user_id", userId);

        client.delete(UrlBuilder.getInstance()
                        .longPath(SecureStrings.path(57))
                        .param(SecureStrings.path(95), AppToken.getToken())
                        .build())
                .header(SecureStrings.path(96), UserToken.getToken())
                .json(body)
                .onSuccess((code, respBody) -> {
                    try {
                        JSONObject resp = new JSONObject(respBody);
                        if (code == 200 && resp.getInt("code") == 200) {
                            isFollowing = false;
                            runOnUiThread(() -> {
                                applyFollowState();
                                adjustFollowerCount(-1);
                            });
                        }
                    } catch (Exception e) {
                        AppLogger.e(TAG, "unfollow failed: " + e.getMessage(), e);
                    }
                })
                .onFailure((code, err) -> AppLogger.e(TAG, "unfollow failed: " + err))
                .execute();
    }

    private void adjustFollowerCount(int delta) {
        try {
            int current = Integer.parseInt(binding.tvStatFollowers.getText().toString());
            binding.tvStatFollowers.setText(String.valueOf(Math.max(0, current + delta)));
        } catch (NumberFormatException ignored) {
        }
    }

private void applyFollowState() {
        if (isFollowing) {
            binding.btnFollow.setText("已关注");
            binding.btnFollow.setBackgroundColor(
                    ContextCompat.getColor(this, android.R.color.transparent));
            binding.btnFollow.setTextColor(
                    ContextCompat.getColor(this, R.color.colorPrimary));
            binding.btnFollow.setStrokeWidth((int) (1.5 * getResources().getDisplayMetrics().density));
            binding.btnFollow.setStrokeColor(
                    ColorStateList.valueOf(ContextCompat.getColor(this, R.color.colorOutline)));
            binding.btnChat.setVisibility(View.VISIBLE);
        } else {
            binding.btnFollow.setText("关注");
            binding.btnFollow.setBackgroundColor(
                    ContextCompat.getColor(this, R.color.colorPrimary));
            binding.btnFollow.setTextColor(
                    ContextCompat.getColor(this, R.color.colorOnPrimary));
            binding.btnFollow.setStrokeWidth(0);
            binding.btnChat.setVisibility(View.GONE);
        }
    }

    private void openChat() {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_USER_ID, userId);
        intent.putExtra(ChatActivity.EXTRA_TITLE, binding.tvNickname.getText().toString());
        startActivity(intent);
    }

    private void loadUserPosts() {
        postsPage = 1;
        postsHasMore = true;
        fetchUserPosts(1, true);
    }

    private void loadMoreUserPosts() {
        if (postsLoading || !postsHasMore) return;
        fetchUserPosts(postsPage + 1, false);
    }

    private void fetchUserPosts(int targetPage, boolean isRefresh) {
        postsLoading = true;
        String url = UrlBuilder.getInstance()
                .longPath(SecureStrings.path(50))
                .param("user_id", String.valueOf(userId))
                .param(SecureStrings.path(95), AppToken.getToken())
                .param("page", String.valueOf(targetPage))
                .param("page_size", String.valueOf(PAGE_SIZE))
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
                            List<ForumPost> list = new ArrayList<>();
                            if (arr != null) {
                                for (int i = 0; i < arr.length(); i++) {
                                    list.add(ForumPost.fromJson(arr.getJSONObject(i)));
                                }
                            }
                            int totalPages = data != null ? data.optInt("total_pages", 1) : 1;
                            boolean more = targetPage < totalPages && !list.isEmpty();
                            runOnUiThread(() -> {
                                postsLoading = false;
                                postsLoaded = true;
                                postsPage = targetPage;
                                postsHasMore = more;
                                if (isRefresh) {
                                    postsAdapter.setData(list);
                                } else {
                                    postsAdapter.appendData(list);
                                }
                            });
                        } else {
                            runOnUiThread(() -> postsLoading = false);
                        }
                    } catch (Exception e) {
                        AppLogger.e(TAG, "load user posts failed: " + e.getMessage(), e);
                        runOnUiThread(() -> postsLoading = false);
                    }
                })
                .onFailure((code, err) -> {
                    AppLogger.e(TAG, "load user posts failed: " + err);
                    runOnUiThread(() -> postsLoading = false);
                })
                .execute();
    }

    private void loadUserResources() {
        resourcesPage = 1;
        resourcesHasMore = true;
        fetchUserResources(1, true);
    }

    private void loadMoreUserResources() {
        if (resourcesLoading || !resourcesHasMore) return;
        fetchUserResources(resourcesPage + 1, false);
    }

    private void fetchUserResources(int targetPage, boolean isRefresh) {
        resourcesLoading = true;
        String url = UrlBuilder.getInstance()
                .longPath(SecureStrings.path(51))
                .param("user_id", String.valueOf(userId))
                .param(SecureStrings.path(95), AppToken.getToken())
                .param("page", String.valueOf(targetPage))
                .param("page_size", String.valueOf(PAGE_SIZE))
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
                            List<ForumResource> list = new ArrayList<>();
                            if (arr != null) {
                                for (int i = 0; i < arr.length(); i++) {
                                    list.add(ForumResource.fromJson(arr.getJSONObject(i)));
                                }
                            }
                            int totalPages = data != null ? data.optInt("total_pages", 1) : 1;
                            boolean more = targetPage < totalPages && !list.isEmpty();
                            runOnUiThread(() -> {
                                resourcesLoading = false;
                                resourcesLoaded = true;
                                resourcesPage = targetPage;
                                resourcesHasMore = more;
                                if (isRefresh) {
                                    resourcesAdapter.setData(list);
                                } else {
                                    resourcesAdapter.appendData(list);
                                }
                            });
                        } else {
                            runOnUiThread(() -> resourcesLoading = false);
                        }
                    } catch (Exception e) {
                        AppLogger.e(TAG, "load user resources failed: " + e.getMessage(), e);
                        runOnUiThread(() -> resourcesLoading = false);
                    }
                })
                .onFailure((code, err) -> {
                    AppLogger.e(TAG, "load user resources failed: " + err);
                    runOnUiThread(() -> resourcesLoading = false);
                })
                .execute();
    }

    private void applyProfile(JSONObject data) {
        binding.tvNickname.setText(data.optString("nickname", "-"));
        binding.tvUsername.setText("@" + data.optString("username", ""));

        String avatar = data.optString("avatar", "");
        if (avatar != null && !avatar.isEmpty()) {
            Glide.with(this)
                    .load(avatar)
                    .placeholder(R.drawable.material_symbols_account_circle_outline)
                    .circleCrop()
                    .into(binding.ivAvatar);
        } else {
            binding.ivAvatar.setImageResource(R.drawable.material_symbols_account_circle_outline);
        }

        boolean permanent = data.optBoolean("is_permanent_member", false);
        binding.cardVip.setVisibility(permanent ? View.VISIBLE : View.GONE);
        if (permanent) {
            binding.tvVip.setText(data.optString("member_title", "会员"));
            binding.tvNickname.setTextColor(ContextCompat.getColor(this, R.color.colorPrimary));
        } else {
            binding.tvNickname.setTextColor(ContextCompat.getColor(this, R.color.colorOnSecondaryContainer));
        }

        binding.tvLevel.setText(data.optString("user_level_title", "Lv.0"));

        JSONObject bbs = data.optJSONObject("bbs");
        if (bbs != null) {
            binding.tvStatFollowing.setText(String.valueOf(bbs.optInt("follow_count", 0)));
            binding.tvStatFollowers.setText(String.valueOf(bbs.optInt("fans_count", 0)));
            binding.tvStatPosts.setText(String.valueOf(bbs.optInt("posts_count", 0)));
            binding.tvStatResources.setText(String.valueOf(bbs.optInt("resources_count", 0)));
            postsCount = bbs.optInt("posts_count", 0);
            resourcesCount = bbs.optInt("resources_count", 0);
        } else {
            postsCount = 0;
            resourcesCount = 0;
        }
        applyTabVisib(binding);

        String authorTag = data.optString("tag_key", "");
        String authorTagTitle = data.optString("tag_key_title", "");
        if (authorTag != null && !authorTag.isEmpty() && !authorTag.equals("0")
                && authorTagTitle != null && !authorTagTitle.isEmpty()) {
            binding.layoutAuthorTag.setVisibility(View.VISIBLE);
            binding.tvAuthorTag.setText(authorTagTitle);
        } else {
            binding.layoutAuthorTag.setVisibility(View.GONE);
        }

        String bio = data.optString("bio", "");
        if (bio != null && !bio.isEmpty()) {
            binding.tvBio.setVisibility(View.VISIBLE);
            binding.tvBio.setText(bio);
        } else {
            binding.tvBio.setVisibility(View.GONE);
        }

        String website = data.optString("website", "");
        if (website != null && !website.isEmpty()) {
            binding.tvWebsite.setVisibility(View.VISIBLE);
            binding.tvWebsite.setText(website);
        } else {
            binding.tvWebsite.setVisibility(View.GONE);
        }
    }

    private void showLoading() {
        binding.layoutLoading.setVisibility(View.VISIBLE);
        binding.layoutError.setVisibility(View.GONE);
        binding.scrollView.setVisibility(View.GONE);
    }

    private void showContent() {
        binding.layoutLoading.setVisibility(View.GONE);
        binding.layoutError.setVisibility(View.GONE);
        binding.scrollView.setVisibility(View.VISIBLE);
    }

    private void showError() {
        binding.layoutLoading.setVisibility(View.GONE);
        binding.layoutError.setVisibility(View.VISIBLE);
        binding.scrollView.setVisibility(View.GONE);
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

    private void applyTabVisib(ActivityUserProfileBinding binding) {
        if (postsCount <= 0 && resourcesCount <= 0) {
            binding.tabLayout.setVisibility(View.GONE);
            binding.recyclerContributions.setVisibility(View.GONE);
            return;
        }
        binding.tabLayout.setVisibility(View.VISIBLE);
        binding.recyclerContributions.setVisibility(View.VISIBLE);

        binding.tabLayout.removeAllTabs();
        tabOrder.clear();
        if (postsCount > 0) {
            tabOrder.add(TAB_POSTS);
            binding.tabLayout.addTab(binding.tabLayout.newTab().setText("帖子"));
        }
        if (resourcesCount > 0) {
            tabOrder.add(TAB_RESOURCES);
            binding.tabLayout.addTab(binding.tabLayout.newTab().setText("资源"));
        }

        if (!tabOrder.contains(currentTab)) {
            currentTab = tabOrder.get(0);
        }
        binding.tabLayout.selectTab(binding.tabLayout.getTabAt(tabOrder.indexOf(currentTab)));
        applyTabSelection();
    }
}
