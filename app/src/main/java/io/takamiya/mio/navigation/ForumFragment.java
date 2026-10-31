package io.takamiya.mio.navigation;
import io.takamiya.mio.utils.SecureStrings;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.UrlBuilder;
import io.takamiya.mio.CreateForumSectionActivity;
import io.takamiya.mio.ForumSectionActivity;
import io.takamiya.mio.PostSearchActivity;
import io.takamiya.mio.UserSearchActivity;
import io.takamiya.mio.adapter.ForumSectionAdapter;
import io.takamiya.mio.bean.ForumSection;
import io.takamiya.mio.databinding.FragmentForumBinding;
import io.takamiya.mio.utils.AppToken;
import io.takamiya.mio.utils.HttpManage;
import io.takamiya.mio.utils.UserToken;

public class ForumFragment extends Fragment {

    private static final String TAG = "ForumFragment";

    private FragmentForumBinding binding;
    private ForumSectionAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentForumBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new ForumSectionAdapter();
        adapter.setOnItemClickListener(section -> {
            Intent intent = new Intent(requireContext(), ForumSectionActivity.class);
            intent.putExtra("section_id", section.getId());
            intent.putExtra("section_name", section.getName());
            intent.putExtra("section_intro", section.getIntro());
            startActivity(intent);
        });

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);

        binding.fabCreate.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(),
                    CreateForumSectionActivity.class));
        });

        binding.btnSearchUsers.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), UserSearchActivity.class)));

        binding.btnSearchPosts.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), PostSearchActivity.class)));

        loadSections();
    }

    private void showLoading() {
        binding.layoutLoading.setVisibility(View.VISIBLE);
        binding.layoutEmpty.setVisibility(View.GONE);
        binding.recyclerView.setVisibility(View.GONE);
    }

    private void showEmpty() {
        binding.layoutLoading.setVisibility(View.GONE);
        binding.layoutEmpty.setVisibility(View.VISIBLE);
        binding.recyclerView.setVisibility(View.GONE);
    }

    private void showList() {
        binding.layoutLoading.setVisibility(View.GONE);
        binding.layoutEmpty.setVisibility(View.GONE);
        binding.recyclerView.setVisibility(View.VISIBLE);
    }

    private void loadSections() {
        showLoading();

        String url = UrlBuilder.getInstance()
                .longPath(SecureStrings.path(48))
                .param("action", "list")
                .param(SecureStrings.path(95), AppToken.getToken())
                .build();

        HttpManage.getInstance().get(url)
                .header(SecureStrings.path(96), UserToken.getToken())
                .onSuccess((code, body) -> {
                    if (!isAdded()) return;
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
                            requireActivity().runOnUiThread(() -> {
                                adapter.setData(list);
                                if (list.isEmpty()) {
                                    showEmpty();
                                } else {
                                    showList();
                                }
                            });
                        } else {
                            requireActivity().runOnUiThread(this::showEmpty);
                        }
                    } catch (Exception e) {
                        AppLogger.e(TAG, "parse sections failed: " + e.getMessage());
                        if (isAdded()) {
                            requireActivity().runOnUiThread(this::showEmpty);
                        }
                    }
                })
                .onFailure((code, err) -> {
                    AppLogger.e(TAG, "load sections failed: " + err);
                    if (isAdded()) {
                        requireActivity().runOnUiThread(this::showEmpty);
                    }
                })
                .execute();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadSections();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
