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

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
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
import io.takamiya.mio.databinding.ActivityCreateResourceBinding;
import io.takamiya.mio.utils.AppToken;
import io.takamiya.mio.utils.HttpManage;
import io.takamiya.mio.utils.UserToken;

public class CreateResourceActivity extends AppCompatActivity {

    private static final String TAG = "CreateResourceActivity";

    private ActivityCreateResourceBinding binding;

    private int selectedSectionId;
    private String selectedSectionName;

    private static final int RES_TYPE_DIRECT = 1;
    private static final int RES_TYPE_NETDISK = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateResourceBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets(binding);

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        if (!UserToken.isLoggedIn()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        binding.chipTypeDirect.setTag(RES_TYPE_DIRECT);
        binding.chipTypeNetdisk.setTag(RES_TYPE_NETDISK);
        binding.chipExpire7.setTag(7);
        binding.chipExpire30.setTag(30);
        binding.chipExpire90.setTag(90);
        binding.chipExpireForever.setTag(0);

        binding.rgType.setOnCheckedChangeListener((group, checkedId) -> {
            boolean netdisk = isNetdiskSelected();
            binding.tilResPwd.setVisibility(netdisk ? View.VISIBLE : View.GONE);
            if (!netdisk) {
                binding.etResPwd.getText().clear();
                binding.tilResPwd.setError(null);
            }
        });
        binding.swPaid.setOnCheckedChangeListener((buttonView, isChecked) -> {
            binding.tilCoinPrice.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            int expireVisibility = isChecked ? View.VISIBLE : View.GONE;
            binding.labelExpire.setVisibility(expireVisibility);
            binding.tvExpireHint.setVisibility(expireVisibility);
            binding.rgExpire.setVisibility(expireVisibility);
        });

        binding.btnPickSection.setOnClickListener(v -> showSectionPicker());
        binding.btnSubmit.setOnClickListener(v -> submit());

        int sectionId = getIntent().getIntExtra("section_id", 0);
        if (sectionId > 0) {
            selectedSectionId = sectionId;
            if (getIntent().getStringExtra("section_name") != null) {
                selectedSectionName = getIntent().getStringExtra("section_name");
                applySectionSelection();
                loadTags();
            } else {
                resolveSectionName(sectionId);
            }
        }
    }

    private Chip getCheckedChipOf(ChipGroup group) {
        if (group == null) return null;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof Chip && ((Chip) child).isChecked()) {
                return (Chip) child;
            }
        }
        return null;
    }

    private boolean isNetdiskSelected() {
        Chip checked = getCheckedChipOf(binding.rgType);
        return checked != null && RES_TYPE_NETDISK == (int) checked.getTag();
    }

    private void applySectionSelection() {
        binding.tvSection.setText(selectedSectionName);
        binding.tvSection.setTextColor(getColor(R.color.colorOnSurface));
    }

    private void resolveSectionName(int sectionId) {
        String url = UrlBuilder.getInstance()
                .longPath(SecureStrings.path(48))
                .param("action", "detail")
                .param("id", String.valueOf(sectionId))
                .param(SecureStrings.path(95), AppToken.getToken())
                .build();

        HttpManage.getInstance().get(url)
                .header(SecureStrings.path(96), UserToken.getToken())
                .onSuccess((code, body) -> {
                    try {
                        JSONObject resp = new JSONObject(body);
                        if (code == 200 && resp.getInt("code") == 200) {
                            JSONObject data = resp.optJSONObject("data");
                            if (data != null) {
                                String name = data.optString("name", "");
                                if (!name.isEmpty()) {
                                    selectedSectionName = name;
                                    runOnUiThread(() -> {
                                        applySectionSelection();
                                        loadTags();
                                    });
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
                .param("action", "list")
                .param(SecureStrings.path(95), AppToken.getToken())
                .build();

        HttpManage.getInstance().get(url)
                .header(SecureStrings.path(96), UserToken.getToken())
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
                    loadTags();
                    d.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ══════════════════════════════════════════════
    //  标签
    // ══════════════════════════════════════════════

    private void loadTags() {
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
                                    Chip chip = (Chip) LayoutInflater.from(this)
                                            .inflate(R.layout.item_tag_chip, binding.cgTags, false);
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

    // ══════════════════════════════════════════════
    //  发布
    // ══════════════════════════════════════════════

    private void submit() {
        String title = binding.etTitle.getText().toString().trim();
        String intro = binding.etIntro.getText().toString().trim();
        boolean valid = true;

        if (title.isEmpty()) {
            binding.tilTitle.setError("标题不能为空");
            valid = false;
        } else {
            binding.tilTitle.setError(null);
        }
        if (selectedSectionId <= 0) {
            Toast.makeText(this, "请选择板块", Toast.LENGTH_SHORT).show();
            valid = false;
        }
        Chip typeChip = getCheckedChipOf(binding.rgType);
        if (typeChip == null) {
            Toast.makeText(this, "请选择资源类型", Toast.LENGTH_SHORT).show();
            valid = false;
        }
        int resType = typeChip != null ? (int) typeChip.getTag() : 0;

        String resUrl = binding.etResUrl.getText().toString().trim();
        if (resUrl.isEmpty()) {
            binding.tilResUrl.setError("资源链接不能为空");
            valid = false;
        } else {
            binding.tilResUrl.setError(null);
        }

        String resPwd = binding.etResPwd.getText().toString().trim();
        binding.tilResPwd.setError(null);

        boolean isPaid = binding.swPaid.isChecked();
        double coinPrice = 0;
        if (isPaid) {
            try {
                coinPrice = Math.round(Double.parseDouble(binding.etCoinPrice.getText().toString().trim()) * 100) / 100.0;
            } catch (NumberFormatException e) {
                coinPrice = 0;
            }
            if (coinPrice <= 0) {
                binding.tilCoinPrice.setError("请输入正确的硬币价格");
                valid = false;
            } else {
                binding.tilCoinPrice.setError(null);
            }
        }

        if (!valid) return;

        setSubmitting(true);

        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        data.put("intro", intro);
        data.put("section_id", selectedSectionId);
        data.put("res_type", resType);
        data.put("res_url", resUrl);
        if (resType == RES_TYPE_NETDISK && !resPwd.isEmpty()) {
            data.put("res_pwd", resPwd);
        }
        List<Integer> tagIds = new ArrayList<>();
        for (int i = 0; i < binding.cgTags.getChildCount(); i++) {
            View child = binding.cgTags.getChildAt(i);
            if (child instanceof Chip && ((Chip) child).isChecked()) {
                tagIds.add((int) child.getTag());
            }
        }
        if (!tagIds.isEmpty()) {
            data.put("tag_ids", tagIds);
        }
        data.put("is_paid", isPaid ? 1 : 0);
        if (isPaid) {
            data.put("coin_price", coinPrice);
            int expireDays = 7;
            Chip expireChip = getCheckedChipOf(binding.rgExpire);
            if (expireChip != null) {
                expireDays = (int) expireChip.getTag();
            }
            data.put("expire_days", expireDays);
        }

        String url = UrlBuilder.getInstance()
                .longPath(SecureStrings.path(51))
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
                            String msg = resp.optString("msg", "资源发布成功，等待管理员审核");
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
        binding.btnSubmit.setText(submitting ? "发布中..." : "发布资源");
        binding.btnSubmit.setVisibility(submitting ? View.GONE : View.VISIBLE);
        binding.progressSubmit.setVisibility(submitting ? View.VISIBLE : View.GONE);
    }

    private void applyWindowInsets(ActivityCreateResourceBinding binding) {
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