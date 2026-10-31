package io.takamiya.mio;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textview.MaterialTextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cn.panda.utils.library.HttpUtils.java.HttpClientJava;
import cn.panda.utils.library.UrlBuilder;
import io.takamiya.mio.databinding.ActivitySubscriptionBinding;
import io.takamiya.mio.databinding.RowBenefitBinding;
import io.takamiya.mio.utils.AppToken;
import io.takamiya.mio.utils.HttpManage;
import io.takamiya.mio.utils.SecureStrings;
import io.takamiya.mio.utils.UserApi;
import io.takamiya.mio.utils.UserToken;
import o.s.o.l;

public class SubscriptionActivity extends AppCompatActivity {

    private ActivitySubscriptionBinding binding;
    private final HttpClientJava client = HttpManage.getInstance();

    private static class PriceItem {
        final int memberLevel;
        final String levelName;
        final int durationDays;
        final String durationLabel;
        final String priceRmb;
        final int priceGold;

        PriceItem(int memberLevel, String levelName, int durationDays,
                  String durationLabel, String priceRmb, int priceGold) {
            this.memberLevel = memberLevel;
            this.levelName = levelName;
            this.durationDays = durationDays;
            this.durationLabel = durationLabel;
            this.priceRmb = priceRmb;
            this.priceGold = priceGold;
        }
    }

    private static final String[][] BENEFITS = {
        /* lowest  */ { "较低配额", "较低配额", "基础奖励", "基础经验", "较高抽成", "有限定价", "普通徽章" },
        /* middle  */ { "较高配额", "较高配额", "双倍奖励", "额外经验", "较低抽成", "较宽定价", "大会员徽章" },
        /* highest */ { "较高配额", "较高配额", "双倍奖励", "额外经验", "最低抽成", "灵活定价", "超级大会员徽章" }
    };

    private static final String[] BENEFIT_LABELS = {
        "每日发帖上限", "每日发资源上限", "每日登录金币",
        "每日登录经验", "发布抽成（付费资源）", "发布定价上限", "身份标识"
    };

    private static final int[] BENEFIT_ICONS = {
        R.drawable.material_symbols_forum_outline_rounded,
        R.drawable.material_symbols_description_outline_rounded,
        R.drawable.material_symbols_paid_rounded,
        R.drawable.material_symbols_rocket_launch_outline_rounded,
        R.drawable.material_symbols_call_split_rounded,
        R.drawable.material_symbols_currency_yen_rounded,
        R.drawable.material_symbols_crown_outline_rounded
    };

    private final List<PriceItem> plans = new ArrayList<>();
    private final List<Integer> levelOrder = new ArrayList<>();
    private final Map<PriceItem, MaterialCardView> planCardViews = new HashMap<>();
    private PriceItem selectedItem;
    private double coinBalance = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySubscriptionBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets(binding);

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        binding.btnSubscribe.setOnClickListener(v -> confirmCoinRedeem());

        binding.btnBuyCard.setOnClickListener(v -> confirmBuyCardCode());

        binding.btnActivateCard.setOnClickListener(v -> showCardCodeDialog());

        updatePrice();
        loadCoinBalance();
        loadPrices();
    }

    private void applyWindowInsets(ActivitySubscriptionBinding binding) {
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

    private void loadCoinBalance() {
        UserApi.getProfile((code, body) -> {
            try {
                JSONObject info = new JSONObject(body.trim());
                if (code == 200 && info.getInt("code") == 200) {
                    JSONObject data = info.optJSONObject("data");
                    double gold = data != null ? data.optDouble("gold", 0.0) : 0.0;
                    runOnUiThread(() -> {
                        coinBalance = gold;
                        binding.tvCoinBalance.setText(String.format(Locale.CHINA, "%.0f", gold));
                    });
                }
            } catch (Exception ignored) {
            }
        }, (code, error) -> { });
    }

    private void loadPrices() {
        client.get(UrlBuilder.getInstance().longPath("user-api/api/member-prices.php")
                        .param(SecureStrings.path(95), AppToken.getToken()).build())
                .header(SecureStrings.path(96), UserToken.getToken())
                .onSuccess((responseCode, responseBody) -> {
                    try {
                        JSONObject info = new JSONObject(responseBody.trim());
                        if (responseCode != 200 || info.getInt("code") != 200) {
                            runOnUiThread(() ->
                                    Toast.makeText(this, "获取价格失败", Toast.LENGTH_SHORT).show());
                            return;
                        }
                        JSONObject data = info.optJSONObject("data");
                        JSONArray list = data != null ? data.optJSONArray("list") : null;
                        if (list == null || list.length() == 0) {
                            runOnUiThread(() ->
                                    Toast.makeText(this, "暂无可购买的会员档位", Toast.LENGTH_SHORT).show());
                            return;
                        }
                        plans.clear();
                        levelOrder.clear();
                        for (int i = 0; i < list.length(); i++) {
                            JSONObject j = list.getJSONObject(i);
                            PriceItem item = new PriceItem(
                                    j.optInt("member_level"),
                                    j.optString("member_level_name", "会员"),
                                    j.optInt("duration_days"),
                                    j.optString("duration_label", ""),
                                    j.optString("price_rmb", "0.00"),
                                    j.optInt("price_gold", 0));
                            plans.add(item);
                            if (!levelOrder.contains(item.memberLevel)) {
                                levelOrder.add(item.memberLevel);
                            }
                        }
                        double gold = data.optDouble("gold", -1);
                        runOnUiThread(() -> {
                            if (gold >= 0) {
                                coinBalance = gold;
                                binding.tvCoinBalance.setText(String.format(Locale.CHINA, "%.0f", gold));
                            }
                            buildPlanCards();
                        });
                    } catch (Exception e) {
                        runOnUiThread(() ->
                                Toast.makeText(this, "价格解析失败", Toast.LENGTH_SHORT).show());
                    }
                })
                .onFailure((responseCode, error) ->
                        runOnUiThread(() -> {
                            Toast.makeText(this, "获取价格失败", Toast.LENGTH_SHORT).show();
                            binding.btnSubscribe.setEnabled(false);
                        }))
                .execute();
    }

    private void buildPlanCards() {
        binding.linearPlans.removeAllViews();
        planCardViews.clear();
        for (PriceItem it : plans) {
            View root = LayoutInflater.from(this)
                    .inflate(R.layout.item_member_plan, binding.linearPlans, false);
            MaterialCardView card = (MaterialCardView) root;
            MaterialTextView name = root.findViewById(R.id.tv_plan_name);
            MaterialTextView duration = root.findViewById(R.id.tv_plan_duration);
            MaterialTextView rmb = root.findViewById(R.id.tv_plan_rmb);
            MaterialTextView coins = root.findViewById(R.id.tv_plan_coins);
            name.setText(it.levelName);
            duration.setText(it.durationLabel.isEmpty() ? it.durationDays + " 天" : it.durationLabel);
            rmb.setText("¥" + it.priceRmb);
            coins.setText(" " + it.priceGold + " 硬币");

            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, dp(8), dp(8));
            binding.linearPlans.addView(card, lp);
            planCardViews.put(it, card);
            card.setOnClickListener(v -> selectItem(it));
        }
        if (selectedItem == null || (plans.isEmpty() || !plans.contains(selectedItem))) {
            if (!plans.isEmpty()) {
                selectItem(plans.get(0));
            } else {
                updatePrice();
            }
        }
    }

    private void selectItem(PriceItem item) {
        selectedItem = item;
        for (Map.Entry<PriceItem, MaterialCardView> e : planCardViews.entrySet()) {
            e.getValue().setStrokeColor(getColor(e.getKey() == item
                    ? R.color.colorPrimary : R.color.colorOutlineVariant));
        }
        updatePrice();
        updateBenefits();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private void updatePrice() {
        if (selectedItem == null) {
            binding.tvPriceLabel.setText("请选择套餐");
            binding.tvPriceValue.setText("—");
            binding.tvPricePeriod.setText("");
            binding.tvPriceCoins.setText("");
            binding.btnSubscribe.setEnabled(false);
            return;
        }
        binding.tvPriceLabel.setText(selectedItem.levelName + " · " + selectedItem.durationLabel);
        binding.tvPriceValue.setText("¥" + selectedItem.priceRmb);
        binding.tvPricePeriod.setText(selectedItem.durationLabel.isEmpty()
                ? "" : selectedItem.durationLabel);
        binding.tvPriceCoins.setText(selectedItem.priceGold + " 硬币");
        binding.btnSubscribe.setEnabled(true);
        binding.btnSubscribe.setText("金币购买会员");
    }

    private void updateBenefits() {
        if (selectedItem == null) return;
        int rank = levelOrder.indexOf(selectedItem.memberLevel);
        int idx = rank < 0 ? 0 : Math.min(rank, BENEFITS.length - 1);
        String[] vals = BENEFITS[idx];
        RowBenefitBinding[] rows = {
            binding.rowPost, binding.rowResource, binding.rowGold,
            binding.rowExp, binding.rowCommission, binding.rowMaxPrice, binding.rowBadge
        };
        for (int i = 0; i < rows.length; i++) {
            rows[i].tvLabel.setText(BENEFIT_LABELS[i]);
            rows[i].tvValue.setText(vals[i]);
            rows[i].tvIcon.setImageResource(BENEFIT_ICONS[i]);
        }
    }

    private void confirmCoinRedeem() {
        if (!UserToken.isLoggedIn()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedItem == null || selectedItem.priceGold <= 0) {
            Toast.makeText(this, "请先选择档位", Toast.LENGTH_SHORT).show();
            return;
        }
        int need = selectedItem.priceGold;
        boolean enough = coinBalance >= need;
        String balance = coinBalance >= 0
                ? String.format(Locale.CHINA, "%.2f 硬币", coinBalance) : "—";
        new MaterialAlertDialogBuilder(this)
                .setTitle("金币购买会员")
                .setMessage(String.format(Locale.CHINA,
                        "购买 %s · %s 将消耗 %d 硬币（约 ¥%s）\n当前硬币：%s",
                        selectedItem.levelName, selectedItem.durationLabel, need,
                        selectedItem.priceRmb, balance))
                .setPositiveButton(enough ? "确认购买" : "硬币不足", (d, w) -> {
                    if (enough) {
                        buyMember();
                    } else {
                        Toast.makeText(this, "硬币不足，无法购买", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(enough ? "取消" : null, null)
                .show();
    }

    private void buyMember() {
        Map<String, Object> body = new HashMap<>();
        body.put("member_level", selectedItem.memberLevel);
        body.put("duration_days", selectedItem.durationDays);
        client.post(UrlBuilder.getInstance().longPath("user-api/api/buy-member.php")
                        .param(SecureStrings.path(95), AppToken.getToken()).build())
                .header(SecureStrings.path(96), UserToken.getToken())
                .json(body)
                .onSuccess((responseCode, responseBody) -> {
                    try {
                        JSONObject info = new JSONObject(responseBody.trim());
                        if (responseCode == 200 && info.getInt("code") == 200) {
                            JSONObject data = info.optJSONObject("data");
                            runOnUiThread(() -> {
                                showResultDialog("购买成功", buildBuyResult(data));
                                loadCoinBalance();
                            });
                        } else {
                            runOnUiThread(() ->
                                    showResultDialog("购买失败", info.optString("msg", "购买失败")));
                        }
                    } catch (Exception e) {
                        runOnUiThread(() -> showResultDialog("购买失败", "购买失败"));
                    }
                })
                .onFailure((responseCode, error) ->
                        runOnUiThread(() -> showResultDialog("购买失败", "购买失败：" + error)))
                .execute();
    }

    private String buildBuyResult(JSONObject data) {
        StringBuilder sb = new StringBuilder();
        if (data == null) return "购买成功";
        String levelName = data.optString("member_level_name", "");
        if (!levelName.isEmpty()) {
            sb.append("会员等级：").append(levelName).append('\n');
        }
        boolean isPermanent = data.isNull("expire_time");
        String expire = data.optString("expire_time", "");
        if (isPermanent || expire.isEmpty()) {
            sb.append("到期时间：永久\n");
        } else {
            sb.append("到期时间：").append(expire).append('\n');
        }
        sb.append("本次花费：").append(data.optInt("cost_gold", 0)).append(" 硬币");
        int diff = data.optInt("upgrade_diff_gold", 0);
        if (diff > 0) {
            sb.append("\n升级补差：").append(diff).append(" 硬币");
        }
        return sb.toString();
    }

    private void confirmBuyCardCode() {
        if (!UserToken.isLoggedIn()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedItem == null || selectedItem.priceGold <= 0) {
            Toast.makeText(this, "请先选择档位", Toast.LENGTH_SHORT).show();
            return;
        }
        int need = selectedItem.priceGold;
        boolean enough = coinBalance >= need;
        String balance = coinBalance >= 0
                ? String.format(Locale.CHINA, "%.0f 硬币", coinBalance) : "—";
        new MaterialAlertDialogBuilder(this)
                .setTitle("金币购买卡密")
                .setMessage(String.format(Locale.CHINA,
                        "为 %s · %s 生成一张全新卡密（可赠送他人激活），消耗 %d 硬币（约 ¥%s）\n当前硬币：%s",
                        selectedItem.levelName, selectedItem.durationLabel, need,
                        selectedItem.priceRmb, balance))
                .setPositiveButton(enough ? "确认购买" : "硬币不足", (d, w) -> {
                    if (enough) {
                        requestBuyCardCode();
                    } else {
                        Toast.makeText(this, "硬币不足，无法购买", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(enough ? "取消" : null, null)
                .show();
    }

    private void requestBuyCardCode() {
        Map<String, Object> body = new HashMap<>();
        body.put("member_level", selectedItem.memberLevel);
        body.put("duration_days", selectedItem.durationDays);
        client.post(UrlBuilder.getInstance().longPath(l.get().a()[106])
                        .param(SecureStrings.path(95), AppToken.getToken()).build())
                .header(SecureStrings.path(96), UserToken.getToken())
                .json(body)
                .onSuccess((responseCode, responseBody) -> {
                    try {
                        JSONObject info = new JSONObject(responseBody.trim());
                        if (responseCode == 200 && info.getInt("code") == 200) {
                            JSONObject data = info.optJSONObject("data");
                            String cardCode = data != null ? data.optString("card_code", "") : "";
                            runOnUiThread(() -> {
                                showResultDialog("购买卡密成功", buildCardCodeResult(data));
                                if (!cardCode.isEmpty()) {
                                    copyCardCode(cardCode);
                                }
                                loadCoinBalance();
                            });
                        } else {
                            runOnUiThread(() ->
                                    showResultDialog("购买卡密失败", info.optString("msg", "购买卡密失败")));
                        }
                    } catch (Exception e) {
                        runOnUiThread(() -> showResultDialog("购买卡密失败", "购买卡密失败"));
                    }
                })
                .onFailure((responseCode, error) ->
                        runOnUiThread(() -> showResultDialog("购买卡密失败", "购买卡密失败：" + error)))
                .execute();
    }

    private String buildCardCodeResult(JSONObject data) {
        if (data == null) return "购买成功";
        StringBuilder sb = new StringBuilder();
        String code = data.optString("card_code", "");
        if (!code.isEmpty()) {
            sb.append("卡密：").append(code).append('\n');
        }
        String name = data.optString("card_name", "");
        if (!name.isEmpty()) {
            sb.append("卡名：").append(name).append('\n');
        }
        String label = data.optString("duration_label", "");
        if (!label.isEmpty()) {
            sb.append("时长：").append(label).append('\n');
        }
        sb.append("花费：").append(data.optInt("cost_gold", 0)).append(" 硬币\n");
        sb.append("剩余金币：").append(data.optInt("remaining_gold", 0));
        return sb.toString();
    }

    private void copyCardCode(String cardCode) {
        android.content.ClipboardManager cm =
                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm == null) return;
        cm.setPrimaryClip(android.content.ClipData.newPlainText("card_code", cardCode));
        Toast.makeText(this, "卡密已复制到剪贴板", Toast.LENGTH_SHORT).show();
    }

    private void showCardCodeDialog() {
        if (!UserToken.isLoggedIn()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_text, null);
        EditText input = view.findViewById(R.id.editInput);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("请输入卡密");

        new MaterialAlertDialogBuilder(this)
                .setTitle("卡密激活")
                .setView(view)
                .setPositiveButton("激活", (d, w) -> activationCardCode(input.getText().toString().trim()))
                .setNegativeButton("取消", null)
                .show();
    }

    private void activationCardCode(String code) {
        if (code.isEmpty()) {
            Toast.makeText(this, "卡密不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("cardCode", code);
        // [106]=buy-card-code.php（购买），[108]=activate-card-code.php（激活）
        client.post(UrlBuilder.getInstance().longPath(l.get().a()[108])
                        .param(SecureStrings.path(95), AppToken.getToken()).build())
                .header(SecureStrings.path(96), UserToken.getToken())
                .json(body)
                .onSuccess((responseCode, responseBody) -> {
                    try {
                        JSONObject info = new JSONObject(responseBody.trim());
                        if (responseCode == 200 && info.getInt("code") == 200) {
                            JSONObject data = info.optJSONObject("data");
                            runOnUiThread(() -> {
                                showResultDialog("卡密激活成功",
                                        buildActivationDetail(info.optString("msg", "卡密激活成功"), data));
                            });
                        } else {
                            runOnUiThread(() ->
                                    showResultDialog("卡密激活失败", info.optString("msg", "卡密激活失败")));
                        }
                    } catch (Exception e) {
                        runOnUiThread(() -> showResultDialog("卡密激活失败", "卡密激活失败"));
                    }
                })
                .onFailure((responseCode, error) ->
                        runOnUiThread(() -> showResultDialog("卡密激活失败", "卡密激活失败：" + error)))
                .execute();
    }

    private String buildActivationDetail(String msg, JSONObject data) {
        StringBuilder sb = new StringBuilder(msg);
        if (data == null) return sb.toString();
        sb.append("\n\n");
        String cardName = data.optString("card_name", "");
        if (!cardName.isEmpty()) {
            sb.append("卡名：").append(cardName).append('\n');
        }
        int cardType = data.optInt("card_type", 0);
        if (cardType == 1) {
            sb.append("类型：时长卡\n");
            String memberTitle = data.optString("member_title", "");
            if (!memberTitle.isEmpty()) {
                sb.append("会员等级：").append(memberTitle).append('\n');
            }
            if (data.has("valid_days") && !data.isNull("valid_days")) {
                sb.append("有效时长：").append(data.optDouble("valid_days", 0)).append("天\n");
            }
            JSONObject expire = data.optJSONObject("expire_time");
            if (expire != null && expire.optBoolean("is_permanent", false)) {
                sb.append("到期时间：永久会员");
            } else if (expire != null && !expire.optString("cn_datetime", "").isEmpty()) {
                sb.append("到期时间：").append(expire.optString("cn_datetime"));
            }
        } else if (cardType == 2) {
            sb.append("类型：积分卡\n");
            sb.append("获得经验：").append(data.optInt("points", 0));
        }
        return sb.toString();
    }

    private void showResultDialog(String title, String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show();
    }
}
