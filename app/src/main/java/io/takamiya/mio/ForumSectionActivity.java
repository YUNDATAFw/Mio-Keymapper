package io.takamiya.mio;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import io.takamiya.mio.databinding.ActivityForumSectionBinding;
import io.takamiya.mio.navigation.ForumPostsFragment;
import io.takamiya.mio.navigation.ForumResourcesFragment;

public class ForumSectionActivity extends AppCompatActivity {

    private ActivityForumSectionBinding binding;
    private Fragment postsFragment;
    private Fragment resourcesFragment;
    private Fragment activeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityForumSectionBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets(binding);

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        int sectionId = getIntent().getIntExtra("section_id", 0);
        String sectionName = getIntent().getStringExtra("section_name");
        String sectionIntro = getIntent().getStringExtra("section_intro");

        binding.toolbar.setTitle(sectionName != null && !sectionName.isEmpty() ? sectionName : "板块");
        if (sectionIntro != null && !sectionIntro.isEmpty()) {
            binding.toolbar.setSubtitle(sectionIntro);
        }

        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("帖子"));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("资源"));

        if (savedInstanceState == null) {
            postsFragment = ForumPostsFragment.newInstance(sectionId);
            resourcesFragment = ForumResourcesFragment.newInstance(sectionId);
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.contentContainer, resourcesFragment, "resources").hide(resourcesFragment)
                    .add(R.id.contentContainer, postsFragment, "posts")
                    .commit();
            activeFragment = postsFragment;
        } else {
            postsFragment = getSupportFragmentManager().findFragmentByTag("posts");
            resourcesFragment = getSupportFragmentManager().findFragmentByTag("resources");
            // 旋转等重建场景下 FragmentManager 已恢复各 fragment 的 show/hide 状态，
            // 以实际可见者为准同步 activeFragment
            activeFragment = postsFragment;
            if (postsFragment != null && postsFragment.isHidden() && resourcesFragment != null) {
                activeFragment = resourcesFragment;
            }
        }

        binding.tabLayout.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                Fragment target = tab.getPosition() == 0 ? postsFragment : resourcesFragment;
                if (target != null && activeFragment != null && target != activeFragment) {
                    getSupportFragmentManager().beginTransaction()
                            .hide(activeFragment)
                            .show(target)
                            .commit();
                    activeFragment = target;
                }
            }

            @Override
            public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
        });
    }

    private void applyWindowInsets(ActivityForumSectionBinding binding) {
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
