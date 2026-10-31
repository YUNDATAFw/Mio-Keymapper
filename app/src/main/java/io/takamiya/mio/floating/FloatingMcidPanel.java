package io.takamiya.mio.floating;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;
import java.util.List;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.adapter.McidAdapter;
import io.takamiya.mio.bean.McidItem;
import io.takamiya.mio.databinding.FloatingWindowMcidPanelBinding;
import io.takamiya.mio.mcid.McidRepository;

public class FloatingMcidPanel {

    private static final String TAG = "FloatingMcidPanel";

    private static final String[] TAB_NAMES = {"物品", "方块", "药水", "附魔", "粒子", "群系", "生物", "迷雾"};

    private final Context context;
    private final WindowManager windowManager;
    private final WindowManager.LayoutParams params;
    private final FloatingWindowMcidPanelBinding binding;
    private final McidAdapter mcidAdapter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private List<McidItem> allItems = new ArrayList<>();
    private String selectedTab = "物品";

    private boolean closing;

    public FloatingMcidPanel(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.binding = FloatingWindowMcidPanelBinding.inflate(LayoutInflater.from(context));

        int type = FloatingWindowType.resolve(context);
        int flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                flags,
                PixelFormat.TRANSLUCENT);
        FloatingWindowType.applyToken(params);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 0;

        binding.commandPanelRoot.setOnClickListener(v -> close());
        binding.commandPanelCard.setOnClickListener(v -> {
        });
        binding.commandBtnClose.setOnClickListener(v -> close());

        mcidAdapter = new McidAdapter();

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(context));
        binding.recyclerView.setAdapter(mcidAdapter);

        for (String name : TAB_NAMES) {
            binding.tabLayout.addTab(binding.tabLayout.newTab().setText(name));
        }
        binding.tabLayout.selectTab(binding.tabLayout.getTabAt(0));

        binding.tabLayout.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                selectedTab = tab.getText().toString();
                refreshList();
            }

            @Override
            public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {
            }
        });

        binding.tvCmd.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refreshList();
            }
        });

        applySystemBarInsets(binding.commandPanelCard);

        loadData();
    }

    private void loadData() {
        McidRepository.loadAll(new McidRepository.Callback() {
            @Override
            public void onStart() {
                AppLogger.d(TAG, "mcid loading started");
                binding.loadingLayout.setVisibility(View.VISIBLE);
                binding.recyclerView.setVisibility(View.GONE);
            }

            @Override
            public void onResult(List<McidItem> items) {
                allItems = items;
                mainHandler.post(() -> refreshList());
            }

            @Override
            public void onFinish() {
                AppLogger.d(TAG, "mcid loading finished");
                binding.loadingLayout.setVisibility(View.GONE);
                binding.recyclerView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onError(String error) {
                AppLogger.e(TAG, "load mcid failed: " + error);
            }
        });
    }

    private void refreshList() {
        String query = binding.tvCmd.getText().toString().trim();
        mcidAdapter.setData(buildItems(selectedTab, query.isEmpty() ? null : query));
    }

    private List<String[]> buildItems(String tabCategory, String query) {
        List<String[]> result = new ArrayList<>();
        for (McidItem item : allItems) {
            if (tabCategory != null && !item.getCategory().equals(tabCategory)) {
                continue;
            }
            if (query == null) {
                result.add(item.toArray());
            } else if (matchItem(item, query)) {
                result.add(item.toArray());
            }
        }
        return result;
    }

    private boolean matchItem(McidItem item, String query) {
        String name = item.getNameCn().isEmpty() ? item.getName() : item.getNameCn();
        String id = item.getId();
        String category = item.getCategory();

        String lowerQuery = query.toLowerCase();
        String lowerName = name.toLowerCase();
        String lowerId = id.toLowerCase();
        String lowerCategory = category.toLowerCase();

        if (lowerQuery.startsWith("name:")) {
            String keyword = query.substring(5).trim().toLowerCase();
            if (keyword.isEmpty()) return true;
            for (String part : keyword.split("\\|")) {
                if (lowerName.contains(part.trim())) return true;
            }
            return false;
        }

        if (lowerQuery.startsWith("id:")) {
            String keyword = query.substring(3).trim().toLowerCase();
            if (keyword.isEmpty()) return true;
            for (String part : keyword.split("\\|")) {
                if (lowerId.contains(part.trim())) return true;
            }
            return false;
        }

        if (lowerQuery.startsWith("class:")) {
            String keyword = query.substring(6).trim().toLowerCase();
            if (keyword.isEmpty()) return true;
            for (String part : keyword.split("\\|")) {
                if (lowerCategory.equals(part.trim())) return true;
            }
            return false;
        }

        return lowerName.contains(lowerQuery)
                || lowerId.contains(lowerQuery)
                || lowerCategory.contains(lowerQuery);
    }

    public void attach() {
        if (isAttached()) return;
        closing = false;
        try {
            windowManager.addView(binding.getRoot(), params);
            AppLogger.d(TAG, "mcid panel attached");
            binding.getRoot().post(this::animateOpen);
        } catch (Exception e) {
            AppLogger.e(TAG, "attach failed: " + e.getMessage(), e);
        }
    }

    public void detach() {
        if (!isAttached()) return;
        try {
            windowManager.removeView(binding.getRoot());
            AppLogger.d(TAG, "mcid panel detached");
        } catch (Exception e) {
            AppLogger.e(TAG, "detach failed: " + e.getMessage(), e);
        }
        resetAnimState();
    }

    public boolean isAttached() {
        return binding.getRoot().getParent() != null;
    }

    public void close() {
        if (closing) return;
        closing = true;
        animateClose();
    }

    private void animateOpen() {
        FloatingAnimator.fadeIn(binding.getRoot(), FloatingAnimator.NORMAL_MS, null);
        FloatingAnimator.slideInFromStart(
                binding.commandPanelCard, slideDistance(), FloatingAnimator.NORMAL_MS, null);
    }

    private void animateClose() {
        FloatingAnimator.slideOutToStart(
                binding.commandPanelCard, slideDistance(), FloatingAnimator.NORMAL_MS, this::detach);
        FloatingAnimator.fadeOut(binding.getRoot(), FloatingAnimator.NORMAL_MS, null);
    }

    private float slideDistance() {
        int width = binding.commandPanelCard.getWidth();
        if (width <= 0) {
            width = context.getResources().getDisplayMetrics().widthPixels;
        }
        return -width;
    }

    private void resetAnimState() {
        closing = false;
        binding.getRoot().setAlpha(1f);
        binding.commandPanelCard.setAlpha(1f);
        binding.commandPanelCard.setTranslationX(0f);
    }

    private void applySystemBarInsets(android.view.View card) {
        ViewCompat.setOnApplyWindowInsetsListener(card, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
