package io.takamiya.mio;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import io.takamiya.mio.databinding.DialogForumItemActionBinding;
import io.takamiya.mio.utils.UserToken;

/**
 * 帖子/资源列表长按 BottomSheet 操作菜单（Material3，样式同帖子详情页）：
 * 根据当前用户身份动态组合「置顶 / 推荐 / 修改 / 删除 / 举报」，
 * 版主与板块管理员拥有置顶、推荐与删除权限。
 */
public class ForumItemActions {

    public interface Callbacks {
        void onEdit();

        void onDelete();

        void onReport();

        void onSetTop(boolean top);

        void onSetRecommend(boolean recommend);
    }

    /**
     * @param targetName    "帖子" 或 "资源"
     * @param targetTitle   标题（过长会自动截断用于对话框标题）
     * @param isAuthor      当前用户是否为作者本人
     * @param isModerator   当前用户是否该板块版主/管理员
     * @param canEdit       是否显示「修改」（仅帖子作者）
     * @param isTop         当前是否已置顶
     * @param isRecommend   当前是否已推荐
     */
    public static void show(Context context, String targetName, String targetTitle,
                            boolean isAuthor, boolean isModerator, boolean canEdit,
                            boolean isTop, boolean isRecommend,
                            Callbacks callbacks) {
        DialogForumItemActionBinding binding =
                DialogForumItemActionBinding.inflate(LayoutInflater.from(context));
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        dialog.setContentView(binding.getRoot());

        binding.itemActionSheetTitle.setText(truncate(targetTitle));

        boolean showEdit = canEdit;
        boolean showDelete = isAuthor || isModerator;
        boolean showReport = !isAuthor;
        boolean showTop = isModerator;
        boolean showRecommend = isModerator;

        binding.topRow.setVisibility(showTop ? View.VISIBLE : View.GONE);
        binding.tvTopText.setText(isTop ? "取消置顶" : "置顶");
        binding.recommendRow.setVisibility(showRecommend ? View.VISIBLE : View.GONE);
        binding.tvRecommendText.setText(isRecommend ? "取消推荐" : "推荐");
        binding.editRow.setVisibility(showEdit ? View.VISIBLE : View.GONE);
        binding.tvEditText.setText("修改" + targetName);
        binding.deleteRow.setVisibility(showDelete ? View.VISIBLE : View.GONE);
        binding.tvDeleteText.setText("删除" + targetName);
        binding.reportRow.setVisibility(showReport ? View.VISIBLE : View.GONE);
        binding.tvReportText.setText("举报" + targetName);

        binding.topRow.setOnClickListener(v -> {
            dialog.dismiss();
            if (callbacks != null) callbacks.onSetTop(!isTop);
        });
        binding.recommendRow.setOnClickListener(v -> {
            dialog.dismiss();
            if (callbacks != null) callbacks.onSetRecommend(!isRecommend);
        });
        binding.editRow.setOnClickListener(v -> {
            dialog.dismiss();
            if (callbacks != null) callbacks.onEdit();
        });
        binding.deleteRow.setOnClickListener(v -> {
            dialog.dismiss();
            if (callbacks != null) callbacks.onDelete();
        });
        binding.reportRow.setOnClickListener(v -> {
            dialog.dismiss();
            if (!UserToken.isLoggedIn()) {
                Toast.makeText(context, "请先登录", Toast.LENGTH_SHORT).show();
                return;
            }
            if (callbacks != null) callbacks.onReport();
        });

        if (!showEdit && !showDelete && !showReport && !showTop && !showRecommend) {
            Toast.makeText(context, "暂无可用操作", Toast.LENGTH_SHORT).show();
            return;
        }
        dialog.show();
    }

    private static String truncate(String text) {
        String s = (text == null ? "" : text).replaceAll("\\s+", " ").trim();
        if (s.length() <= 18) return s.isEmpty() ? "操作" : s;
        return s.substring(0, 18) + "…";
    }
}