package io.takamiya.mio.floating;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.tabs.TabLayout;

import java.util.function.Consumer;

import androidx.lifecycle.ViewModelStoreOwner;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.databinding.FloatingWindowWsPanelBinding;
import io.takamiya.mio.floating.wss.WsBaseFragment;
import io.takamiya.mio.floating.wss.WsChatFragment;
import io.takamiya.mio.floating.wss.WsCmdFragment;
import io.takamiya.mio.floating.wss.WsConfigFragment;
import io.takamiya.mio.floating.wss.WsFeatureFragment;
import io.takamiya.mio.floating.wss.WsLinkFragment;
import io.takamiya.mio.floating.wss.WsLogFragment;
import io.takamiya.mio.floating.wss.WsTab;

/**
 * WS 服务面板：复用工具箱详情面板的骨架,
 * 作为独立悬浮窗口打开,主设置面板保持在下层不关闭。
 * 内容按 tab 切换"伪 Fragment"纯 View 页面,不依赖 FragmentActivity。
 */
public class FloatingWsPanel {

    private static final String TAG = "FloatingWsPanel";

    private static final WsTab[] TABS = {
            new WsTab("连接", WsLinkFragment.class),
            new WsTab("配置", WsConfigFragment.class),
            new WsTab("命令", WsCmdFragment.class),
            new WsTab("聊天", WsChatFragment.class),
            new WsTab("功能", WsFeatureFragment.class),
            new WsTab("日志", WsLogFragment.class)
    };

    private final Context context;
    private final ViewModelStoreOwner viewModelStoreOwner;
    private final WindowManager windowManager;
    private final WindowManager.LayoutParams params;
    private final FloatingWindowWsPanelBinding binding;

    private WsBaseFragment currentFragment;
    private Consumer<String> showToastFn;
    private boolean closing;

    public FloatingWsPanel(Context context, ViewModelStoreOwner viewModelStoreOwner) {
        this.context = context;
        this.viewModelStoreOwner = viewModelStoreOwner;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.binding = FloatingWindowWsPanelBinding.inflate(LayoutInflater.from(context));

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

        binding.btnClose.setOnClickListener(v -> close());
        // 点击卡片外空白区域直接关闭面板；卡片本身消费点击避免误关。
        binding.getRoot().setOnClickListener(v -> close());
        binding.wsPanelCard.setOnClickListener(v -> { });
        binding.tabGroups.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                loadTab(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                loadTab(tab.getPosition());
            }
        });

        applySystemBarInsets(binding.wsPanelCard);
        loadTab(0);
    }

    public void setShowToast(Consumer<String> fn) {
        this.showToastFn = fn;
    }

    private void loadTab(int position) {
        if (position < 0 || position >= TABS.length) return;
        try {
            if (currentFragment != null) {
                binding.wsContentContainer.removeAllViews();
                currentFragment.onDetach();
                currentFragment = null;
            }

            WsTab tab = TABS[position];
            currentFragment = tab.getFragmentClass().newInstance();
            currentFragment.onAttach(context);
            currentFragment.setViewModelStoreOwner(viewModelStoreOwner);
            if (showToastFn != null) currentFragment.setShowToast(showToastFn);

            LayoutInflater inflater = LayoutInflater.from(context);
            ViewGroup container = binding.wsContentContainer;
            currentFragment.create(inflater, container);

            binding.tvWsTitle.setText(tab.getName());

            if (currentFragment.getView() != null) {
                container.removeAllViews();
                container.addView(currentFragment.getView());
            }
        } catch (Exception e) {
            AppLogger.e(TAG, "load tab failed: " + e.getMessage(), e);
        }
    }

    public void attach() {
        if (isAttached()) return;
        closing = false;
        try {
            windowManager.addView(binding.getRoot(), params);
            AppLogger.d(TAG, "ws panel attached");
            binding.getRoot().post(this::animateOpen);
        } catch (Exception e) {
            AppLogger.e(TAG, "attach failed: " + e.getMessage(), e);
        }
    }

    public void detach() {
        if (!isAttached()) return;
        try {
            windowManager.removeView(binding.getRoot());
            AppLogger.d(TAG, "ws panel detached");
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
                binding.wsPanelCard, slideDistance(), FloatingAnimator.NORMAL_MS, null);
    }

    private void animateClose() {
        FloatingAnimator.slideOutToStart(
                binding.wsPanelCard, slideDistance(), FloatingAnimator.NORMAL_MS, this::detach);
        FloatingAnimator.fadeOut(binding.getRoot(), FloatingAnimator.NORMAL_MS, null);
    }

    private float slideDistance() {
        int width = binding.wsPanelCard.getWidth();
        if (width <= 0) {
            width = context.getResources().getDisplayMetrics().widthPixels;
        }
        return -width;
    }

    private void resetAnimState() {
        closing = false;
        binding.getRoot().setAlpha(1f);
        binding.wsPanelCard.setAlpha(1f);
        binding.wsPanelCard.setTranslationX(0f);
    }

    private void applySystemBarInsets(View card) {
        ViewCompat.setOnApplyWindowInsetsListener(card, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
