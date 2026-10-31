package io.takamiya.mio.navigation;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.HttpUtils.java.HttpClientJava;
import cn.panda.utils.library.Registry.Registry;
import cn.panda.utils.library.UrlBuilder;
import io.takamiya.mio.LoginActivity;
import io.takamiya.mio.MyMessagesActivity;
import io.takamiya.mio.ManuscriptActivity;
import io.takamiya.mio.ChatListActivity;
import io.takamiya.mio.DownloadManagerActivity;
import io.takamiya.mio.MyFavoritesActivity;
import io.takamiya.mio.FollowListActivity;
import io.takamiya.mio.MyPostsActivity;
import io.takamiya.mio.MyResourcesActivity;
import io.takamiya.mio.R;
import io.takamiya.mio.databinding.FragmentUserBinding;
import io.takamiya.mio.utils.AppToken;
import io.takamiya.mio.utils.DataManage;
import io.takamiya.mio.utils.HttpManage;
import io.takamiya.mio.utils.RegistryUtils;
import io.takamiya.mio.utils.SecureStrings;
import io.takamiya.mio.utils.UserApi;
import io.takamiya.mio.utils.UserToken;
import o.s.o.l;

public class UserFragment extends Fragment {

    private static final String TAG = "UserFragment";
    private static final long MAX_AVATAR_SIZE = 2 * 1024 * 1024;
    private static final String[] GENDER_TITLES = {"保密", "男", "女", "其他"};

    private FragmentUserBinding binding;
    private HttpClientJava client = HttpManage.getInstance();
    private UserViewModel viewModel;

    private final ActivityResultLauncher<String> avatarPicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), this::onAvatarPicked);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentUserBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(UserViewModel.class);

        if ((!UserToken.isLoggedIn())) return;
        if (UserToken.isExpired()) {
            // 已过期重新获取token
            return;
        }
        loadAvatar();
        if (viewModel.isProfileLoaded()) {
            applyUserData(viewModel.getProfileData());
        } else {
            loadUserData();
        }
        setupListeners();
        loadUnreadBadges();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding == null || viewModel == null) return;
        if (!UserToken.isLoggedIn() || UserToken.isExpired()) return;
        // 仅在已有缓存(即已显示过)时重新拉取，避免首次加载的重复请求
        // 从关注/粉丝/帖子/资源等页面返回时刷新统计数字
        if (viewModel.isProfileLoaded()) {
            loadUserData();
        }
        loadUnreadBadges();
    }

    // ==================== 数据加载 ====================

    private void loadUnreadBadges() {
        if (!UserToken.isLoggedIn() || binding == null) return;
        loadChatUnread();
        loadMessageUnread();
    }

    private void loadChatUnread() {
        String url = UrlBuilder.getInstance()
                .longPath(l.get().a()[103])
                .param("action", "unread_count")
                .param(SecureStrings.path(95), AppToken.getToken())
                .build();
        client.get(url)
                .header(SecureStrings.path(96), UserToken.getToken())
                .onSuccess((code, body) -> {
                    try {
                        JSONObject info = new JSONObject(body.trim());
                        if (code == 200 && info.getInt("code") == 200) {
                            JSONObject data = info.optJSONObject("data");
                            int count = data != null ? data.optInt("unread_count", 0) : 0;
                            postOnUiThread(() -> setBadge(binding.tvBadgeChat, binding.badgeChat, count));
                        }
                    } catch (Exception ignored) {
                    }
                })
                .execute();
    }

    private void loadMessageUnread() {
        String url = UrlBuilder.getInstance()
                .longPath(SecureStrings.path(60))
                .param("action", "unread_count")
                .param(SecureStrings.path(95), AppToken.getToken())
                .build();
        client.get(url)
                .header(SecureStrings.path(96), UserToken.getToken())
                .onSuccess((code, body) -> {
                    try {
                        JSONObject info = new JSONObject(body.trim());
                        if (code == 200 && info.getInt("code") == 200) {
                            JSONObject data = info.optJSONObject("data");
                            int count = data != null ? data.optInt("unread_count", 0) : 0;
                            postOnUiThread(() -> setBadge(binding.tvBadgeMessages, binding.badgeMessages, count));
                        }
                    } catch (Exception ignored) {
                    }
                })
                .execute();
    }

    private void setBadge(android.widget.TextView tv, View card, int count) {
        if (count <= 0) {
            card.setVisibility(View.GONE);
        } else {
            tv.setText(count > 99 ? "99+" : String.valueOf(count));
            card.setVisibility(View.VISIBLE);
        }
    }

    private void loadUserData() {
        viewModel.loadProfile(new UserViewModel.ProfileCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                postOnUiThread(() -> applyUserData(data));
            }

            @Override
            public void onError(String message) {
                postOnUiThread(() -> toast(message));
            }
        });
    }

    private void applyUserData(JSONObject data) {
        binding.tvNickname.setText(data.optString("nickname", "-"));
        binding.tvAccount.setText(data.optString("username", "-"));
            binding.tvGold.setText(String.format(Locale.CHINA, "金币:%.2f", data.optDouble("gold", 0.0)));

        JSONObject info = data.optJSONObject("info");
        boolean permanent = data != null && data.optInt("member_level", 0) > 0;
        binding.cardVip.setVisibility(permanent ? View.VISIBLE : View.GONE);
        boolean isPermanentMember = info != null && info.optBoolean("is_permanent_member", false);
        if (permanent) {
            binding.tvVip.setText(info != null ? info.optString("member_title", "会员") : "会员");
            binding.tvMemberExpire.setVisibility(View.VISIBLE);
            if (isPermanentMember) {
                binding.tvMemberExpire.setText("会员到期:永久");
            } else {
                String expire = data.optString("member_expire_time", "");
                binding.tvMemberExpire.setText(expire.isEmpty() ? "会员到期:未知"                        : "会员到期:" + expire);
            }
        } else {
            binding.tvMemberExpire.setVisibility(View.GONE);
        }

        JSONObject bbs = data.optJSONObject("bbs");
        if (bbs != null) {
            binding.tvStatFollowing.setText(bbs.optString("follow_count", "0"));
            binding.tvStatFollowers.setText(bbs.optString("fans_count", "0"));
            binding.tvStatPosts.setText(bbs.optString("posts_count", "0"));
            binding.tvStatResources.setText(bbs.optString("resources_count", "0"));
        }

        JSONObject userLevel = data.optJSONObject("user_level_info");
        if (userLevel != null) {
            binding.tvLevel.setText(userLevel.optString("title", "Lv.0"));
            binding.levelProgress.setProgressCompat(userLevel.optInt("progress_percent", 0), true);
            binding.levelText.setText(new StringBuilder().append(userLevel.optInt("experience", 0))
                    .append("/").append(userLevel.optInt("next_threshold", 0)).toString());
        }

        binding.tvNicknameValue.setText(data.optString("nickname", "-"));
        binding.tvGender.setText(genderTitle(data));
        binding.tvSignature.setText(emptyToDash(data.optString("bio")));
        binding.tvQq.setText(emptyToDash(data.optString("qq")));
        binding.tvWechat.setText(emptyToDash(data.optString("wechat")));
        binding.tvPhone.setText(emptyToDash(data.optString("phone")));
        binding.tvBirthday.setText(emptyToDash(data.optString("birthday")));
        binding.tvBlog.setText(emptyToDash(data.optString("website")));
        viewModel.setCurrentGender(data.optInt("gender", 0));
    }

    private String genderTitle(JSONObject data) {
        String title = data.optString("gender_title", "");
        if (!title.isEmpty()) {
            return title;
        }
        int g = data.optInt("gender", 0);
        return g >= 0 && g < GENDER_TITLES.length ? GENDER_TITLES[g] : "-";
    }

    private String emptyToDash(String s) {
        return s == null || s.equals("null") || s.isEmpty() ? "-" : s;
    }

    private void loadAvatar() {
        String url = UrlBuilder.getInstance().longPath(SecureStrings.path(9))
                .param(SecureStrings.path(95), AppToken.getToken())
                .param("username", UserToken.getUsername())
                .param("t", String.valueOf(System.currentTimeMillis()))
                .build();
        Glide.with(requireActivity())
                .load(url)
                .into(binding.ivAvatar);
    }

    // ==================== 事件监听 ====================

    private void setupListeners() {
        binding.btnEditAvatar.setOnClickListener(v -> avatarPicker.launch("image/*"));

        binding.btnEditNickname.setOnClickListener(v ->
                showTextEditDialog("修改昵称", binding.tvNicknameValue.getText().toString(),
                        InputType.TYPE_CLASS_TEXT, value -> {
                    if (value.isEmpty()) {
                        toast("昵称不能为空");
                        return;
                    }
                    Map<String, Object> fields = new HashMap<>();
                    fields.put("nickname", value);
                    submitProfile(fields, "昵称更新成功", () -> {
                        binding.tvNickname.setText(value);
                        binding.tvNicknameValue.setText(value);
                    });
                }));

        binding.btnEditGender.setOnClickListener(v -> showGenderDialog());

        binding.btnEditSignature.setOnClickListener(v ->
                showTextEditDialog("修改个性签名", binding.tvSignature.getText().toString(),
                        InputType.TYPE_CLASS_TEXT, value -> {
                    Map<String, Object> fields = new HashMap<>();
                    fields.put("bio", value);
                    submitProfileInfo(fields, "签名更新成功",
                            () -> binding.tvSignature.setText(emptyToDash(value)));
                }));

        binding.btnEditQq.setOnClickListener(v ->
                showTextEditDialog("修改QQ", binding.tvQq.getText().toString(),
                        InputType.TYPE_CLASS_TEXT, value -> {
                    Map<String, Object> fields = new HashMap<>();
                    fields.put("qq", value);
                    submitProfileInfo(fields, "QQ更新成功",
                            () -> binding.tvQq.setText(emptyToDash(value)));
                }));

        binding.btnEditWechat.setOnClickListener(v ->
                showTextEditDialog("修改微信", binding.tvWechat.getText().toString(),
                        InputType.TYPE_CLASS_TEXT, value -> {
                    Map<String, Object> fields = new HashMap<>();
                    fields.put("wechat", value);
                submitProfileInfo(fields, "微信更新成功",
                            () -> binding.tvWechat.setText(emptyToDash(value)));
                }));

        binding.btnEditPhone.setOnClickListener(v ->
                showTextEditDialog("修改手机号", binding.tvPhone.getText().toString(),
                        InputType.TYPE_CLASS_PHONE, value -> {
                    if (!value.isEmpty() && !value.matches("^1[3-9]\\d{9}$")) {
                        toast("手机号格式不正确");
                        return;
                    }
                    Map<String, Object> fields = new HashMap<>();
                    fields.put("phone", value);
                    submitProfileInfo(fields, "手机号更新成功",
                            () -> binding.tvPhone.setText(emptyToDash(value)));
                }));

        binding.btnEditBirthday.setOnClickListener(v -> showBirthdayDialog());

        binding.btnEditBlog.setOnClickListener(v ->
                showTextEditDialog("修改博客", binding.tvBlog.getText().toString(),
                        InputType.TYPE_TEXT_VARIATION_URI, value -> {
                    Map<String, Object> fields = new HashMap<>();
                    fields.put("website", value);
                    submitProfileInfo(fields, "博客更新成功",
                            () -> binding.tvBlog.setText(emptyToDash(value)));
                }));

        binding.btnRefreshLogin.setOnClickListener(v -> refreshLogin());

        binding.btnMessages.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), MyMessagesActivity.class)));

        binding.btnChat.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), ChatListActivity.class)));

        binding.statFollowingLayout.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), FollowListActivity.class)
                        .putExtra(FollowListActivity.EXTRA_MODE, FollowListActivity.MODE_FOLLOWING)));

        binding.statFollowersLayout.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), FollowListActivity.class)
                        .putExtra(FollowListActivity.EXTRA_MODE, FollowListActivity.MODE_FOLLOWERS)));

        binding.statPostsLayout.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), MyPostsActivity.class)));

        binding.statResourcesLayout.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), MyResourcesActivity.class)));

        binding.btnDownloads.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), DownloadManagerActivity.class)));

        binding.btnFavorites.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), MyFavoritesActivity.class)));

        binding.btnCardCode.setOnClickListener(v ->
                showTextEditDialog("卡密激活", "", InputType.TYPE_CLASS_TEXT,
                        this::activationCardCode));

        binding.btnManuscript.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), ManuscriptActivity.class)));

        binding.btnLogout.setOnClickListener(v -> logout());
    }

    // ══════════════════════════════════════════════
    // 提交资料
    // ══════════════════════════════════════════════

    private void submitProfile(Map<String, Object> fields, String successMsg, Runnable refresh) {
        UserApi.updateProfile(fields,
                (code, body) -> handleSubmitResponse(code, body, successMsg, refresh),
                (code, error) -> postOnUiThread(() -> toast("修改失败：" + safe(error))));
    }

    private void submitProfileInfo(Map<String, Object> fields, String successMsg, Runnable refresh) {
        UserApi.updateProfileInfo(fields,
                (code, body) -> handleSubmitResponse(code, body, successMsg, refresh),
                (code, error) -> postOnUiThread(() -> toast("修改失败：" + safe(error))));
    }

    private void handleSubmitResponse(int code, String body, String successMsg, Runnable refresh) {
        try {
            JSONObject info = new JSONObject(body.trim());
            if (code == 200 && info.getInt("code") == 200) {
                postOnUiThread(() -> {
                    toast(successMsg);
                    refresh.run();
                });
            } else {
                postOnUiThread(() -> toast(info.optString("msg", "修改失败")));
            }
        } catch (Exception e) {
            postOnUiThread(() -> toast("解析失败"));
        }
    }

    // ══════════════════════════════════════════════
    // 卡密激活
    // ══════════════════════════════════════════════

    private void activationCardCode(String code) {
        if (code.isEmpty()) {
            toast("卡密不能为空");
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("cardCode", code);
        client.post(UrlBuilder.getInstance().longPath(l.get().a()[108])
                        .param(SecureStrings.path(95), AppToken.getToken()).build())
                .header(SecureStrings.path(96), UserToken.getToken())
                .json(body)
                .onSuccess((responseCode, responseBody) -> {
                    try {
                        JSONObject info = new JSONObject(responseBody.trim());
                        if (responseCode == 200 && info.getInt("code") == 200) {
                            JSONObject data = info.optJSONObject("data");
                            postOnUiThread(() -> {
                                showResultDialog("卡密激活成功",
                                        buildActivationDetail(info.optString("msg", "卡密激活成功"), data));
                                loadUserData();
                            });
                        } else {
                            postOnUiThread(() ->
                                    showResultDialog("卡密激活失败", info.optString("msg", "卡密激活失败")));
                        }
                    } catch (Exception e) {
                        postOnUiThread(() -> showResultDialog("卡密激活失败", "卡密激活失败：" + safe(e.getMessage())));
                    }
                })
                .onFailure((responseCode, error) ->
                        postOnUiThread(() -> showResultDialog("卡密激活失败", "卡密激活失败：" + safe(error))))
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
                sb.append("有效时长：").append(data.optDouble("valid_days", 0))
                        .append("天\n");
            }
            JSONObject expire = data.optJSONObject("expire_time");
            if (expire != null && expire.optBoolean("is_permanent", false)) {
                sb.append("到期时间：永久会员");
            } else if (expire != null && expire.optString("cn_datetime", "").isEmpty() == false) {
                sb.append("到期时间：").append(expire.optString("cn_datetime"));
            }
        } else if (cardType == 2) {
            sb.append("类型：积分卡\n");
            sb.append("获得经验：").append(data.optInt("points", 0));
        }
        return sb.toString();
    }

    private void showResultDialog(String title, String message) {
        if (getContext() == null) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show();
    }

    // ==================== 编辑弹窗 ====================

    private void showTextEditDialog(String title, String prefill, int inputType,
                                    Consumer<String> onConfirm) {
        String initial = (prefill == null || "-".equals(prefill)) ? "" : prefill;
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_text, null);
        EditText input = view.findViewById(R.id.editInput);
        input.setInputType(inputType);
        input.setText(initial);
        input.setSelection(initial.length());

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setView(view)
                .setPositiveButton("确定", (d, w) -> onConfirm.accept(input.getText().toString().trim()))
                .setNegativeButton("取消", null)
                .show();
    }

    private void showGenderDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("选择性别")
                .setSingleChoiceItems(GENDER_TITLES, viewModel.getCurrentGender(), (d, which) -> {
                    Map<String, Object> fields = new HashMap<>();
                    fields.put("gender", which);
                    submitProfileInfo(fields, "性别更新成功", () -> {
                        viewModel.setCurrentGender(which);
                        binding.tvGender.setText(GENDER_TITLES[which]);
                    });
                    d.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showBirthdayDialog() {
        Calendar cal = Calendar.getInstance();
        String current = binding.tvBirthday.getText().toString();
        if (!"-".equals(current)) {
            try {
                String[] parts = current.split("-");
                if (parts.length == 3) {
                    cal.set(Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1]) - 1,
                            Integer.parseInt(parts[2]));
                }
            } catch (Exception ignored) {
            }
        }

        new DatePickerDialog(requireContext(), (view, year, month, dayOfMonth) -> {
            String birthday = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
            Map<String, Object> fields = new HashMap<>();
            fields.put("birthday", birthday);
            submitProfileInfo(fields, "生日更新成功", () -> binding.tvBirthday.setText(birthday));
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    // ==================== 头像 ====================

    private void onAvatarPicked(Uri uri) {
        if (uri == null) {
            return;
        }

        String mime = requireContext().getContentResolver().getType(uri);
        String ext;
        if ("image/png".equals(mime)) {
            ext = "png";
        } else if ("image/gif".equals(mime)) {
            ext = "gif";
        } else if ("image/jpeg".equals(mime)) {
            ext = "jpg";
        } else {
            toast("仅支持 JPG、PNG、GIF 格式的图片");
            return;
        }

        File out = new File(requireContext().getCacheDir(),
                "avatar_upload_" + System.currentTimeMillis() + "." + ext);
        long size = copyUriToFile(uri, out);
        if (size < 0) {
            toast("读取图片失败");
            return;
        }
        if (size > MAX_AVATAR_SIZE) {
            toast("图片大小不能超过2MB");
            out.delete();
            return;
        }

        UserApi.uploadAvatar(out, out.getName(), mime, (code, body) -> {
            out.delete();
            try {
                JSONObject info = new JSONObject(body.trim());
                if (code == 200 && info.getInt("code") == 200) {
                    postOnUiThread(() -> {
                        toast("头像上传成功");
                        loadAvatar();
                    });
                } else {
                    postOnUiThread(() -> toast(info.optString("msg", "头像上传失败")));
                }
            } catch (Exception e) {
                postOnUiThread(() -> toast("头像上传失败"));
            }
        }, (code, error) -> {
            out.delete();
            postOnUiThread(() -> toast("头像上传失败：" + safe(error)));
        });
    }

    private long copyUriToFile(Uri uri, File out) {
        try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            long total = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                total += n;
                fos.write(buf, 0, n);
            }
            fos.flush();
            return total;
        } catch (Exception e) {
            return -1;
        }
    }

    // ==================== ==================== 登录状态 ==================== ====================

    private void refreshLogin() {
        Registry reg = RegistryUtils.get();
        String account = reg.getString(DataManage.Tag.USER_ACCOUNT.getValue());
        String password = reg.getString(DataManage.Tag.USER_PASSWORD.getValue());

        if (TextUtils.isEmpty(account) || TextUtils.isEmpty(password)) {
            toast("未保存登录凭证，请重新登录");
            goLogin();
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("loginName", account);
        data.put("password", password);

        client.post(UrlBuilder.getInstance().longPath(SecureStrings.path(3))
                        .param(SecureStrings.path(95), AppToken.getToken()).build())
                .json(data)
                .onSuccess((code, body) -> {
                    try {
                        JSONObject info = new JSONObject(body.trim());
                        if (code == 200 && info.getInt("code") == 200) {
                            String token = info.getJSONObject("data").getString("token");
                            if (UserToken.parseAndStore(token)) {
                                postOnUiThread(() -> {
                                    toast("登录状态已刷新");
                                    loadAvatar();
                                    loadUserData();
                                });
                            } else {
                                postOnUiThread(() -> toast("Token解析失败"));
                            }
                        } else {
                            postOnUiThread(() -> toast(info.optString("msg", "刷新登录状态失败")));
                        }
                    } catch (Exception e) {
                        postOnUiThread(() -> toast("刷新登录状态失败"));
                    }
                })
                .onFailure((code, error) -> postOnUiThread(() -> toast("刷新登录状态失败: " + safe(error))))
                .execute();
    }

    private void logout() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("退出登录")
                .setMessage("确定要退出当前账号吗？")
                    .setPositiveButton("确定", (d, w) -> {
                    UserToken.clear();
                    Registry reg = RegistryUtils.get();
                    reg.delete(DataManage.Tag.USER_ACCOUNT.getValue());
                    reg.delete(DataManage.Tag.USER_PASSWORD.getValue());
                    reg.saveAsync();
                    goLogin();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void goLogin() {
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void toast(String msg) {
        if (getContext() != null) {
            Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
        }
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private void postOnUiThread(Runnable r) {
        Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(r);
        }
    }
}
