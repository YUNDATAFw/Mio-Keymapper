package io.takamiya.mio.floating;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.adapter.BuildingCommandAdapter;
import io.takamiya.mio.building.BuildingInfo;
import io.takamiya.mio.building.BuildingStore;
import io.takamiya.mio.databinding.FloatingWindowBuildingCommandPanelBinding;

/**
 * 建筑命令子面板：TabLayout（手动/方块/其他）展示三种命令，
 * 每条命令单行显示（超出省略）+ 复制按钮；方块命令分页流式加载。
 * 打开自导入建筑侧边栏，关闭后回到侧边栏。
 */
public class FloatingBuildingCommandPanel {

    private static final String TAG = "BuildingCommandPanel";
    /** Tab 索引与命令组对应。 */
    private static final int TAB_MANUAL = 0;
    private static final int TAB_BLOCK = 1;
    private static final int TAB_OTHER = 2;
    private static final int PAGE_SIZE = 100;

    private final Context context;
    private final WindowManager windowManager;
    private final WindowManager.LayoutParams params;
    private final FloatingWindowBuildingCommandPanelBinding binding;
    private final BuildingStore store;
    private final BuildingCommandAdapter adapter;
    private final LinearLayoutManager layoutManager;

    private Consumer<String> showToastFn;
    private boolean closing;

    private BuildingInfo current;
    private int currentTab = TAB_MANUAL;
    /** 方块组下一页起始 offset。 */
    private int blockOffset;
    private boolean appendRequest;
    private boolean loading;
    /** 换建筑/切 Tab 时自增，丢弃过期的异步回调。 */
    private int generation;

    public FloatingBuildingCommandPanel(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.binding = FloatingWindowBuildingCommandPanelBinding.inflate(LayoutInflater.from(context));
        this.store = BuildingStore.get(context);

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

        adapter = new BuildingCommandAdapter();
        layoutManager = new LinearLayoutManager(context);
        binding.rvCommands.setLayoutManager(layoutManager);
        binding.rvCommands.setAdapter(adapter);
        adapter.setOnCopyListener(this::copyCommand);

        binding.getRoot().setOnClickListener(v -> close());
        binding.commandPanelCard.setOnClickListener(v -> { });
        binding.btnCloseCommand.setOnClickListener(v -> close());

        binding.tabCommands.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                selectTab(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        binding.rvCommands.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return;
                int last = layoutManager.findLastVisibleItemPosition();
                if (last >= adapter.getItemCount() - 5) {
                    loadMoreBlock();
                }
            }
        });

        applySystemBarInsets(binding.commandPanelCard);
    }

    public void setShowToast(Consumer<String> fn) {
        this.showToastFn = fn;
    }

    private void toast(String msg) {
        if (showToastFn != null) showToastFn.accept(msg);
    }

    /** 打开/刷新：设置标题并加载该建筑的命令。 */
    public void load(BuildingInfo info) {
        current = info;
        generation++;
        binding.tvCommandTitle.setText(info.name);
        binding.tvCommandSubtitle.setText(
                info.sx + "×" + info.sy + "×" + info.sz + " · " + info.commandCount + " 条命令");
        binding.tabCommands.selectTab(binding.tabCommands.getTabAt(TAB_MANUAL));
        // selectTab 若已选中不会回调，这里显式加载
        selectTab(binding.tabCommands.getSelectedTabPosition());
    }

    private void selectTab(int position) {
        if (current == null) return;
        currentTab = position;
        blockOffset = 0;
        appendRequest = false;
        generation++;
        loadPage();
    }

    private void loadMoreBlock() {
        if (current == null || currentTab != TAB_BLOCK || loading) return;
        if (blockOffset >= current.commandCount) return;
        appendRequest = true;
        loadPage();
    }

    /** 按当前 Tab 加载一页/整组。 */
    private void loadPage() {
        if (current == null) return;
        final String name = current.name;
        final int gen = generation;
        final boolean append = appendRequest;
        final int offset = blockOffset;

        loading = true;
        store.io(() -> {
            List<String> result;
            try {
                switch (currentTab) {
                    case TAB_BLOCK:
                        result = store.repository().getBlockCommands(name, offset, PAGE_SIZE);
                        break;
                    case TAB_OTHER:
                        result = store.repository().getOtherCommands(name);
                        break;
                    case TAB_MANUAL:
                    default:
                        result = store.repository().getManualCommands(name);
                        break;
                }
            } catch (Exception e) {
                AppLogger.w(TAG, "load commands failed: " + e.getMessage());
                result = new ArrayList<>();
            }
            List<String> data = result;
            store.main(() -> {
                loading = false;
                if (gen != generation || current == null) return; // 已切换，丢弃
                if (append) {
                    adapter.appendData(data);
                } else {
                    adapter.setData(data);
                }
                blockOffset = offset + data.size();
                updateEmpty();
            });
        });
    }

    private void updateEmpty() {
        binding.tvCommandsEmpty.setVisibility(
                adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }

    private void copyCommand(String command) {
        try {
            ClipboardManager cm = (ClipboardManager)
                    context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("building_command", command));
                toast("已复制命令");
            }
        } catch (Exception e) {
            AppLogger.w(TAG, "copy failed: " + e.getMessage());
            toast("复制失败");
        }
    }

    // ==================== 窗口生命周期 ====================

    public void attach() {
        if (isAttached()) return;
        closing = false;
        try {
            windowManager.addView(binding.getRoot(), params);
            AppLogger.d(TAG, "command panel attached");
            binding.getRoot().post(this::animateOpen);
        } catch (Exception e) {
            AppLogger.e(TAG, "attach failed: " + e.getMessage(), e);
        }
    }

    public void detach() {
        if (!isAttached()) return;
        try {
            windowManager.removeView(binding.getRoot());
            AppLogger.d(TAG, "command panel detached");
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

    private void applySystemBarInsets(View card) {
        ViewCompat.setOnApplyWindowInsetsListener(card, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
