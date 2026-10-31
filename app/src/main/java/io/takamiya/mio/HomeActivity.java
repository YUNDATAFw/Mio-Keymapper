package io.takamiya.mio;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.utilities.CorePalette;
import com.google.android.material.color.utilities.QuantizerCelebi;
import com.google.android.material.color.utilities.Score;

import java.util.List;
import java.util.Map;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.databinding.ActivityHomeBinding;


public class HomeActivity extends AppCompatActivity {
    private static final String TAG = "HomeActivity";
    private static final String HEADER_IMAGE_URL =
            "https://uapis.cn/api/v1/random/image?category=general_anime";
    private static final int HEADER_SEED_FALLBACK = 0xFF6750A4;
    private ActivityHomeBinding binding;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // nativeEnforceGate 由 Mio.onActivityCreated 全局回调统一执行,
        // 此处仅防御:若已被 native finish 则不要继续 setContentView
        if (isFinishing() || isDestroyed()) {
            return;
        }

        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets();
        setupDrawerHeader();

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.navHostFragment);
        NavController navController = navHostFragment.getNavController();

        binding.toolbar.setNavigationOnClickListener(v ->
                binding.drawerLayout.openDrawer(GravityCompat.START));

        // 自行接管抽屉导航：NavigationUI 内部 navigate 无法携带过渡动画，
        // 这里显式带 NavOptions anim，同时保持 popUpTo/startDestination 语义一致
        binding.navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (navController.getGraph().findNode(id) == null) {
                return false;
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START);
            NavDestination current = navController.getCurrentDestination();
            if (current != null && current.getId() == id) {
                return true;
            }
            navController.navigate(id, null, navOptions(navController));
            return true;
        });

        // 标题随目的地切换为页面名；勾选状态同步（分组子菜单里的目的地需自行递归勾选）
        navController.addOnDestinationChangedListener((controller, destination, args) -> {
            binding.toolbar.setTitle(destination.getLabel());
            syncDrawerChecked(binding.navigationView.getMenu(), destination.getId());
        });

        if (savedInstanceState == null
                && "building_list".equals(getIntent().getStringExtra("navigate_to"))) {
            navController.navigate(R.id.building_list, null, navOptions(navController));
        }

        binding.toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.permission_management) {
                startActivity(new Intent(this, PermissionActivity.class));
                return true;
            } else if (id == R.id.terms_of_service) {
                Intent intent = new Intent(this, UserAgreementActivity.class);
                intent.putExtra("from_toolbar", true);
                startActivity(intent);
                return true;
            } else if (id == R.id.open_source_license) {
                startActivity(new Intent(this, OpenSourceLicenseActivity.class));
                return true;
            }
            return false;
        });
    }

    /** 侧边栏头部：加载随机动漫图作背景，并按图提取 MD3 色板给头部组件调色。 */
    private void setupDrawerHeader() {
        View header = binding.navigationView.getHeaderView(0);
        ImageView bg = header.findViewById(R.id.headerBg);
        View scrim = header.findViewById(R.id.headerScrim);
        MaterialCardView avatarCard = header.findViewById(R.id.headerAvatarCard);
        ImageView avatar = header.findViewById(R.id.headerAvatar);
        TextView title = header.findViewById(R.id.headerTitle);
        MaterialCardView badgeCard = header.findViewById(R.id.headerBadgeCard);
        TextView badge = header.findViewById(R.id.headerBadge);
        TextView subtitle = header.findViewById(R.id.headerSubtitle);

        // 带随机后缀穿透缓存，保证每次进主页换图；禁用磁盘缓存防止 URL 唯一导致缓存膨胀
        String url = HEADER_IMAGE_URL + "&r=" + System.nanoTime();
        Glide.with(this).asBitmap()
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .listener(new RequestListener<Bitmap>() {
                    @Override
                    public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target,
                                                   DataSource dataSource, boolean isFirstResource) {
                        applyHeaderPalette(resource, scrim, avatarCard, avatar, title, badgeCard, badge, subtitle);
                        return false;
                    }

                    @Override
                    public boolean onLoadFailed(GlideException e, Object model, Target<Bitmap> target,
                                                boolean isFirstResource) {
                        // 保持 XML 原色与透明遮罩，静默回退
                        return false;
                    }
                })
                .into(bg);
    }

    /** 图片 → QuantizerCelebi 量化 → Score 选 seed → CorePalette 色带 → 给头部组件调色。 */
    private static void applyHeaderPalette(Bitmap bitmap, View scrim, MaterialCardView avatarCard,
                                           ImageView avatar, TextView title, MaterialCardView badgeCard,
                                           TextView badge, TextView subtitle) {
        try {
            // 降采样到最长边 128px，量化在万级像素内完成，主线程开销可忽略
            int maxEdge = Math.max(bitmap.getWidth(), bitmap.getHeight());
            float scale = maxEdge > 128 ? 128f / maxEdge : 1f;
            int w = Math.max(1, Math.round(bitmap.getWidth() * scale));
            int h = Math.max(1, Math.round(bitmap.getHeight() * scale));
            Bitmap sample = scale == 1f ? bitmap : Bitmap.createScaledBitmap(bitmap, w, h, true);
            int[] pixels = new int[w * h];
            sample.getPixels(pixels, 0, w, 0, 0, w, h);
            if (sample != bitmap) {
                sample.recycle();
            }

            Map<Integer, Integer> quantized = QuantizerCelebi.quantize(pixels, 128);
            List<Integer> seeds = Score.score(quantized);
            int seed = (seeds == null || seeds.isEmpty()) ? HEADER_SEED_FALLBACK : seeds.get(0);
            CorePalette palette = CorePalette.of(seed);

            title.setTextColor(palette.a1.tone(95));
            subtitle.setTextColor(palette.n1.tone(80));
            avatarCard.setCardBackgroundColor(palette.a1.tone(90));
            avatar.setImageTintList(ColorStateList.valueOf(palette.a1.tone(20)));
            badgeCard.setCardBackgroundColor(palette.a1.tone(30));
            badge.setTextColor(palette.a1.tone(90));

            // 遮罩渐变取该图的暗中性色（非纯黑），上淡下浓保证标题/副标题对比度
            int dark = palette.n1.tone(20);
            scrim.setBackground(new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{ColorUtils.setAlphaComponent(dark, 0x66),
                            ColorUtils.setAlphaComponent(dark, 0xCC)}));
        } catch (Throwable t) {
            AppLogger.e(TAG, "applyHeaderPalette failed: " + t.getMessage(), t);
        }
    }

    private static NavOptions navOptions(NavController navController) {
        return new NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setPopUpTo(navController.getGraph().getStartDestinationId(), false)
                .setEnterAnim(R.anim.nav_enter)
                .setExitAnim(R.anim.nav_exit)
                .setPopEnterAnim(R.anim.nav_pop_enter)
                .setPopExitAnim(R.anim.nav_pop_exit)
                .build();
    }

    private static boolean syncDrawerChecked(Menu menu, int destinationId) {
        boolean matched = false;
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (item.hasSubMenu()) {
                if (syncDrawerChecked(item.getSubMenu(), destinationId)) {
                    matched = true;
                }
            } else if (item.getItemId() == destinationId) {
                item.setChecked(true);
                matched = true;
            } else if (item.isCheckable()) {
                item.setChecked(false);
            }
        }
        return matched;
    }

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // AppBarLayout 延伸到状态栏下方（不加顶部 padding）
            // MaterialToolbar 加顶部 padding 避免与状态栏重叠
            binding.toolbar.setPadding(
                    binding.toolbar.getPaddingLeft(),
                    bars.top,
                    binding.toolbar.getPaddingRight(),
                    binding.toolbar.getPaddingBottom());
            // NavigationView 加系统栏 padding，抽屉内容不与状态栏重叠
            binding.navigationView.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });
    }
}
