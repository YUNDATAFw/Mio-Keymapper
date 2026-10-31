package io.takamiya.mio;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import io.takamiya.mio.databinding.DialogCommentActionBinding;

/**
 * 评论/回复操作 BottomSheet：删除 + 举报
 */
public class CommentActionSheet {

    public interface OnActionListener {
        void onDelete();

        void onReport();
    }

    private final Context context;
    private final BottomSheetDialog dialog;
    private final DialogCommentActionBinding binding;

    private OnActionListener actionListener;

    public CommentActionSheet(Context context) {
        this.context = context;
        binding = DialogCommentActionBinding.inflate(LayoutInflater.from(context));
        dialog = new BottomSheetDialog(context);
        dialog.setContentView(binding.getRoot());

        binding.deleteActionRow.setOnClickListener(v -> {
            dialog.dismiss();
            if (actionListener != null) {
                actionListener.onDelete();
            }
        });
        binding.reportActionRow.setOnClickListener(v -> {
            dialog.dismiss();
            if (actionListener != null) {
                actionListener.onReport();
            }
        });
    }

    public Context getContext() {
        return context;
    }

    public void setOnActionListener(OnActionListener listener) {
        this.actionListener = listener;
    }

    /**
     * @param title      弹窗标题，如"评论操作"
     * @param canDelete  是否显示删除项（仅评论作者本人可见）
     * @param deleteText 删除项文字，如"删除评论"/"删除回复"
     * @param reportText 举报项文字，如"举报评论"/"举报回复"
     */
    public void show(String title, boolean canDelete, String deleteText, String reportText) {
        binding.actionSheetTitle.setText(title);
        binding.deleteActionText.setText(deleteText);
        binding.reportActionText.setText(reportText);
        binding.deleteActionRow.setVisibility(canDelete ? View.VISIBLE : View.GONE);
        dialog.show();
    }

    public void dismiss() {
        dialog.dismiss();
    }

    public boolean isShowing() {
        return dialog.isShowing();
    }
}