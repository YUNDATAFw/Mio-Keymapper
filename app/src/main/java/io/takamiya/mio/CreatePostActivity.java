package io.takamiya.mio;
import io.takamiya.mio.utils.SecureStrings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.UrlBuilder;
import io.takamiya.mio.bean.ForumSection;
import io.takamiya.mio.databinding.ActivityCreatePostBinding;
import io.takamiya.mio.utils.AppToken;
import io.takamiya.mio.utils.HttpManage;
import io.takamiya.mio.utils.UserToken;

public class CreatePostActivity extends AppCompatActivity {

    private static final String TAG = "CreatePostActivity";

    private ActivityCreatePostBinding binding;

    private int selectedSectionId;
    private String selectedSectionName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreatePostBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets(binding);

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        if (!UserToken.isLoggedIn()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        int sectionId = getIntent().getIntExtra("section_id", 0);
        String sectionName = getIntent().getStringExtra("section_name");
        if (sectionId > 0) {
            selectedSectionId = sectionId;
            if (sectionName != null && !sectionName.isEmpty()) {
                selectedSectionName = sectionName;
                applySectionSelection();
            } else {
                resolveSectionName(sectionId);
            }
        }

        binding.btnPickSection.setOnClickListener(v -> showSectionPicker());
        binding.btnSubmit.setOnClickListener(v -> submit());
    }

    private void applySectionSelection() {
        binding.tvSection.setText(selectedSectionName);
        binding.tvSection.setTextColor(getColor(R.color.colorOnSurface));
        loadPostTags();
    }

    private void loadPostTags() {
        if (selectedSectionId <= 0) return;
        binding.tvTagsHint.setText("加载中...");

        String url = UrlBuilder.getInstance()
                .longPath(SecureStrings.path(49))
                .param("action", "list")
                .param("section_id", String.valueOf(selectedSectionId))
                .param(SecureStrings.path(95), AppToken.getToken())
                .build();

        HttpManage.getInstance().get(url)
                .header(SecureStrings.path(96), UserToken.getToken())
                .onSuccess((code, body) -> runOnUiThread(() -> {
                    try {
                        JSONObject resp = new JSONObject(body);
                        if (code == 200 && resp.getInt("code") == 200) {
                            JSONObject data = resp.optJSONObject("data");
                            JSONArray arr = data != null ? data.optJSONArray("list") : null;
                            binding.cgTags.removeAllViews();
                            if (arr != null && arr.length() > 0) {
                                for (int i = 0; i < arr.length(); i++) {
                                    JSONObject tag = arr.getJSONObject(i);
                                    com.google.android.material.chip.Chip chip =
                                            (com.google.android.material.chip.Chip) LayoutInflater
                                                    .from(this).inflate(R.layout.item_tag_chip, binding.cgTags, false);
                                    chip.setCheckable(true);
                                    chip.setTag(tag.optInt("id", 0));
                                    chip.setText(tag.optString("name", ""));
                                    binding.cgTags.addView(chip);
                                }
                                binding.tvTagsHint.setText("最多可选 1 个");
                            } else {
                                binding.tvTagsHint.setText("该板块暂无标签");
                            }
                        } else {
                            binding.tvTagsHint.setText("标签加载失败");
                        }
                    } catch (Exception e) {
                        AppLogger.e(TAG, "parse tags failed: " + e.getMessage());
                        binding.tvTagsHint.setText("标签加载失败");
                    }
                }))
                .onFailure((code, err) -> runOnUiThread(() -> {
                    AppLogger.e(TAG, "load tags failed: " + err);
                    binding.tvTagsHint.setText("标签加载失败");
                }))
                .execute();
    }

    private void resolveSectionName(int sectionId) {
        String url = UrlBuilder.getInstance()
                .longPath(SecureStrings.path(48))
                .param("action", "detail")
                .param("id", String.valueOf(sectionId))
                .param(SecureStrings.path(95), AppToken.getToken())
                .build();

        HttpManage.getInstance().get(url)
                .onSuccess((code, body) -> {
                    try {
                        JSONObject resp = new JSONObject(body);
                        if (code == 200 && resp.getInt("code") == 200) {
                            JSONObject data = resp.optJSONObject("data");
                            if (data != null) {
                                String name = data.optString("name", "");
                                if (!name.isEmpty()) {
                                    selectedSectionName = name;
                                    runOnUiThread(this::applySectionSelection);
                                }
                            }
                        }
                    } catch (Exception e) {
                        AppLogger.e(TAG, "resolve section name failed: " + e.getMessage());
                    }
                })
                .onFailure((code, err) -> AppLogger.e(TAG, "resolve section name failed: " + err))
                .execute();
    }

    // ══════════════════════════════════════════════
    //  板块选择
    // ══════════════════════════════════════════════

    private void showSectionPicker() {
        androidx.appcompat.app.AlertDialog loading = new MaterialAlertDialogBuilder(this)
                .setTitle("选择板块")
                .setMessage("加载中...")
                .setCancelable(false)
                .show();

        String url = UrlBuilder.getInstance()
                .longPath(SecureStrings.path(48))
                .param(SecureStrings.path(95), AppToken.getToken())
                .build();

        HttpManage.getInstance().get(url)
                .onSuccess((code, body) -> {
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
                            runOnUiThread(() -> {
                                loading.dismiss();
                                if (list.isEmpty()) {
                                    Toast.makeText(this, "暂无可选板块", Toast.LENGTH_SHORT).show();
                                } else {
                                    showSectionChoiceDialog(list);
                                }
                            });
                        } else {
                            runOnUiThread(() -> {
                                loading.dismiss();
                                Toast.makeText(this, "加载板块失败：" + resp.optString("msg", "未知错误"),
                                        Toast.LENGTH_SHORT).show();
                            });
                        }
                    } catch (Exception e) {
                        AppLogger.e(TAG, "parse sections failed: " + e.getMessage());
                        runOnUiThread(() -> {
                            loading.dismiss();
                            Toast.makeText(this, "加载板块失败", Toast.LENGTH_SHORT).show();
                        });
                    }
                })
                .onFailure((code, err) -> {
                    AppLogger.e(TAG, "load sections failed: " + err);
                    runOnUiThread(() -> {
                        loading.dismiss();
                        Toast.makeText(this, "加载板块失败", Toast.LENGTH_SHORT).show();
                    });
                })
                .execute();
    }

    private void showSectionChoiceDialog(List<ForumSection> list) {
        String[] names = new String[list.size()];
        final int[] ids = new int[list.size()];
        int checked = -1;
        for (int i = 0; i < list.size(); i++) {
            ForumSection s = list.get(i);
            names[i] = s.getName();
            ids[i] = s.getId();
            if (s.getId() == selectedSectionId) {
                checked = i;
            }
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("选择板块")
                .setSingleChoiceItems(names, checked, (d, which) -> {
                    selectedSectionId = ids[which];
                    selectedSectionName = names[which];
                    applySectionSelection();
                    d.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ══════════════════════════════════════════════
    //  发布
    // ══════════════════════════════════════════════

    private void submit() {
        String title = binding.etTitle.getText().toString().trim();
        String content = binding.etContent.getText().toString().trim();

        boolean valid = true;
        if (title.isEmpty()) {
            binding.tilTitle.setError("标题不能为空");
            valid = false;
        } else {
            binding.tilTitle.setError(null);
        }
        if (content.isEmpty()) {
            binding.tilContent.setError("正文不能为空");
            valid = false;
        } else {
            binding.tilContent.setError(null);
        }
        if (selectedSectionId <= 0) {
            Toast.makeText(this, "请选择板块", Toast.LENGTH_SHORT).show();
            valid = false;
        }
        if (!valid) return;

        setSubmitting(true);

        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        data.put("content", content);
        data.put("section_id", selectedSectionId);

        List<Integer> tagIds = new ArrayList<>();
        for (int i = 0; i < binding.cgTags.getChildCount(); i++) {
            View child = binding.cgTags.getChildAt(i);
            if (child instanceof com.google.android.material.chip.Chip
                    && ((com.google.android.material.chip.Chip) child).isChecked()) {
                tagIds.add((int) child.getTag());
            }
        }
        if (!tagIds.isEmpty()) {
            data.put("tag_ids", tagIds);
        }

        String url = UrlBuilder.getInstance()
                .longPath(SecureStrings.path(50))
                .param("action", "create")
                .param(SecureStrings.path(95), AppToken.getToken())
                .build();

        HttpManage.getInstance().post(url)
                .header(SecureStrings.path(96), UserToken.getToken())
                .json(data)
                .onSuccess((code, body) -> runOnUiThread(() -> {
                    try {
                        JSONObject resp = new JSONObject(body);
                        int respCode = resp.getInt("code");
                        if (code == 200 && respCode == 200) {
                            String msg = resp.optString("msg", "发布成功，等待管理员审核");
                            new MaterialAlertDialogBuilder(this)
                                    .setTitle("发布成功")
                                    .setMessage(msg)
                                    .setCancelable(false)
                                    .setPositiveButton("知道了", (d, w) -> finish())
                                    .show();
                        } else {
                            setSubmitting(false);
                            Toast.makeText(this, resp.optString("msg", "发布失败"),
                                    Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        setSubmitting(false);
                        Toast.makeText(this, "解析响应失败", Toast.LENGTH_SHORT).show();
                    }
                }))
                .onFailure((code, err) -> runOnUiThread(() -> {
                    setSubmitting(false);
                    Toast.makeText(this, "网络异常：" + err, Toast.LENGTH_SHORT).show();
                }))
                .execute();
    }

    private void setSubmitting(boolean submitting) {
        binding.btnSubmit.setEnabled(!submitting);
        binding.btnSubmit.setText(submitting ? "发布中..." : "发布帖子");
        binding.btnSubmit.setVisibility(submitting ? View.GONE : View.VISIBLE);
        binding.progressSubmit.setVisibility(submitting ? View.VISIBLE : View.GONE);
    }

    private void applyWindowInsets(ActivityCreatePostBinding binding) {
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
