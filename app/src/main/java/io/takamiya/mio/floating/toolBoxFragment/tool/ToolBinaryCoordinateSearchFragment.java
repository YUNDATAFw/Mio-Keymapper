package io.takamiya.mio.floating.toolBoxFragment.tool;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import io.takamiya.mio.databinding.FragmentToolBinaryCoordinateSearchBinding;
import io.takamiya.mio.floating.toolBoxFragment.ToolboxBaseFragment;

public class ToolBinaryCoordinateSearchFragment extends ToolboxBaseFragment {

    private FragmentToolBinaryCoordinateSearchBinding binding;

    private int initialRange = 1000;
    private int yLevel = -63;
    private String playerName = "";

    private int startX, widthX;
    private int startZ, widthZ;
    private int quadrant;
    private int totalRoundsX, totalRoundsZ;
    private int roundX, roundZ;
    private boolean searchStarted = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container) {
        binding = FragmentToolBinaryCoordinateSearchBinding.inflate(inflater, container, false);
        setupListeners();
        return binding.getRoot();
    }

    private void setupListeners() {
        binding.btnStart.setOnClickListener(v -> startSearch());
        binding.btnQ1.setOnClickListener(v -> selectQuadrant(1));
        binding.btnQ2.setOnClickListener(v -> selectQuadrant(2));
        binding.btnQ3.setOnClickListener(v -> selectQuadrant(3));
        binding.btnQ4.setOnClickListener(v -> selectQuadrant(4));
        binding.btnHasTarget.setOnClickListener(v -> onHasTarget());
        binding.btnNoTarget.setOnClickListener(v -> onNoTarget());
        binding.btnCopyQuadrant.setOnClickListener(v -> copyCommandToClipboard(binding.tvQuadrantCommand));
        binding.btnCopyPrecision.setOnClickListener(v -> copyCommandToClipboard(binding.tvPrecisionCommand));
        binding.btnCopyTeleport.setOnClickListener(v -> {
            String cmd = binding.tvTeleportCommand.getText().toString();
            copyToClipboard(cmd);
        });
        binding.btnResetError.setOnClickListener(v -> resetSession());
        binding.btnResetSession.setOnClickListener(v -> resetSession());
    }

    private void startSearch() {
        try {
            String rangeStr = binding.etSearchRange.getText().toString().trim();
            String yStr = binding.etYLevel.getText().toString().trim();
            playerName = binding.etPlayerName.getText().toString().trim();

            if (rangeStr.isEmpty()) {
                showToast("请输入搜索范围");
                return;
            }

            initialRange = Integer.parseInt(rangeStr);
            if (initialRange < 4) {
                showToast("搜索范围至少为4格");
                return;
            }

            if (!yStr.isEmpty()) {
                yLevel = Integer.parseInt(yStr);
            }

            totalRoundsX = (int) Math.ceil(Math.log(initialRange) / Math.log(2));
            totalRoundsZ = totalRoundsX;
            roundX = 0;
            roundZ = 0;

            String quadrantCmd = generateQuadrantDetectionCommand();
            binding.tvQuadrantCommand.setText(quadrantCmd);

            binding.circularProgressIndicator.setMax(100);
            binding.circularProgressIndicator.setProgress(0);
            binding.tvProgressText.setText("0%");

            searchStarted = true;
            updateVisibility(false, false, false, false, true);
        } catch (NumberFormatException e) {
            showToast("请输入有效的数字");
        }
    }

    private void selectQuadrant(int q) {
        quadrant = q;
        boolean xNegative = (q == 2 || q == 3);
        boolean zNegative = (q == 1 || q == 2);

        startX = xNegative ? -initialRange : 0;
        widthX = initialRange;
        startZ = zNegative ? -initialRange : 0;
        widthZ = initialRange;

        roundX = 0;
        roundZ = 0;
        generateAndShowPrecisionCommand();
        updateVisibility(false, false, true, false, false);
    }

    private void onHasTarget() {
        int total = widthX > 0 ? widthX : widthZ;
        int half = total / 2;

        if (widthX > 1) {
            widthX = half;
            roundX++;
        } else if (widthZ > 1) {
            widthZ = half;
            roundZ++;
        }

        if (widthX <= 1 && widthZ <= 1) {
            showResult();
        } else {
            generateAndShowPrecisionCommand();
        }
        updateProgress();
    }

    private void onNoTarget() {
        int total = widthX > 0 ? widthX : widthZ;
        int half = total / 2;

        if (widthX > 1) {
            startX += getSearchDirection() * half;
            widthX = total - half;
            roundX++;
        } else if (widthZ > 1) {
            startZ += getSearchDirection() * half;
            widthZ = total - half;
            roundZ++;
        }

        if (widthX <= 0 || widthZ <= 0) {
            showError("搜索范围异常，请重置");
            return;
        }

        if (widthX <= 1 && widthZ <= 1) {
            showResult();
        } else {
            generateAndShowPrecisionCommand();
        }
        updateProgress();
    }

    private int getSearchDirection() {
        if (widthX > 1) {
            return (quadrant == 2 || quadrant == 3) ? -1 : 1;
        } else {
            return (quadrant == 1 || quadrant == 2) ? -1 : 1;
        }
    }

    private void generateAndShowPrecisionCommand() {
        if (widthX > 1) {
            int remX = (int) Math.ceil(Math.log(widthX) / Math.log(2));
            int remZ = (int) Math.ceil(Math.log(Math.max(widthZ, 1)) / Math.log(2));
            binding.tvAxisInfo.setText("当前搜索 X 轴 · 剩余 " + (remX + remZ) + " 步");
            binding.tvCurrentRange.setText(formatRange("X", startX, widthX, quadrant == 2 || quadrant == 3));
            String cmd = generateXPrecisionCommand();
            binding.tvPrecisionCommand.setText(cmd);
        } else if (widthZ > 1) {
            int remZ = (int) Math.ceil(Math.log(widthZ) / Math.log(2));
            binding.tvAxisInfo.setText("当前搜索 Z 轴 · 剩余 " + remZ + " 步");
            binding.tvCurrentRange.setText(formatRange("Z", startZ, widthZ, quadrant == 1 || quadrant == 2));
            String cmd = generateZPrecisionCommand();
            binding.tvPrecisionCommand.setText(cmd);
        }
    }

    private String generateQuadrantDetectionCommand() {
        StringBuilder sb = new StringBuilder("/w @s ");
        sb.append("§6第一象限(X+ Z-):@a[x=~,y=").append(yLevel).append(",z=~,dx=").append(initialRange - 1).append(",dy=500,dz=-").append(initialRange - 1).append(playerFilter()).append("] ");
        sb.append("§e第二象限(X- Z-):@a[x=~-").append(initialRange).append(",y=").append(yLevel).append(",z=~,dx=").append(initialRange - 1).append(",dy=500,dz=-").append(initialRange - 1).append(playerFilter()).append("] ");
        sb.append("§a第三象限(X- Z+):@a[x=~-").append(initialRange).append(",y=").append(yLevel).append(",z=~,dx=").append(initialRange - 1).append(",dy=500,dz=").append(initialRange - 1).append(playerFilter()).append("] ");
        sb.append("§b第四象限(X+ Z+):@a[x=~,y=").append(yLevel).append(",z=~,dx=").append(initialRange - 1).append(",dy=500,dz=").append(initialRange - 1).append(playerFilter()).append("]");
        return sb.toString();
    }

    private String generateXPrecisionCommand() {
        int total = widthX;
        int half = total / 2;
        boolean negative = (quadrant == 2 || quadrant == 3);

        StringBuilder sb = new StringBuilder("/w @s ");

        if (total <= 3) {
            for (int i = 0; i < total; i++) {
                int coord = negative ? (startX - i) : (startX + i);
                sb.append("§6").append(coord).append(":@a[x=~").append(coord)
                  .append(",y=").append(yLevel)
                  .append(",z=~,dx=0,dy=500,").append(dzFull()).append(playerFilter()).append("]");
                if (i < total - 1) sb.append(" ");
            }
        } else {
            int innerDx = half - 1;
            int outerDx = (total - half) - 1;
            int outerX = negative ? (startX - half) : (startX + half);

            sb.append("§6内区:@a[x=~").append(startX)
              .append(",y=").append(yLevel)
              .append(",z=~,dx=").append(negative ? -innerDx : innerDx)
              .append(",dy=500,").append(dzFull()).append(playerFilter()).append("] ");

            sb.append("§b外区:@a[x=~").append(outerX)
              .append(",y=").append(yLevel)
              .append(",z=~,dx=").append(negative ? -outerDx : outerDx)
              .append(",dy=500,").append(dzFull()).append(playerFilter()).append("]");
        }
        return sb.toString();
    }

    private String generateZPrecisionCommand() {
        int total = widthZ;
        int half = total / 2;
        boolean negative = (quadrant == 1 || quadrant == 2);

        StringBuilder sb = new StringBuilder("/w @s ");
        sb.append("X固定:").append(startX).append(" ");

        if (total <= 3) {
            for (int i = 0; i < total; i++) {
                int coord = negative ? (startZ - i) : (startZ + i);
                sb.append("§6").append(coord).append(":@a[x=~").append(startX)
                  .append(",y=").append(yLevel)
                  .append(",z=~").append(coord).append(",dx=0,dy=500,dz=0").append(playerFilter()).append("]");
                if (i < total - 1) sb.append(" ");
            }
        } else {
            int innerDz = half - 1;
            int outerDz = (total - half) - 1;
            int outerZ = negative ? (startZ - half) : (startZ + half);

            sb.append("§6内区:@a[x=~").append(startX)
              .append(",y=").append(yLevel)
              .append(",z=~").append(startZ)
              .append(",dx=0,dy=500,dz=").append(negative ? -innerDz : innerDz).append(playerFilter()).append("] ");

            sb.append("§b外区:@a[x=~").append(startX)
              .append(",y=").append(yLevel)
              .append(",z=~").append(outerZ)
              .append(",dx=0,dy=500,dz=").append(negative ? -outerDz : outerDz).append(playerFilter()).append("]");
        }
        return sb.toString();
    }

    private String playerFilter() {
        return playerName.isEmpty() ? "" : ",name=" + playerName;
    }

    private String dzFull() {
        boolean zNegative = (quadrant == 1 || quadrant == 2);
        int fullDz = initialRange - 1;
        return "dz=" + (zNegative ? "-" + fullDz : fullDz);
    }

    private String formatRange(String axis, int start, int width, boolean negative) {
        int end = negative ? (start - width + 1) : (start + width - 1);
        int min = Math.min(start, end);
        int max = Math.max(start, end);
        return axis + ": " + min + " ~ " + max + " (" + width + "格)";
    }

    private void showResult() {
        int finalX = startX;
        int finalZ = startZ;
        String coord = "X=" + finalX + "  Y=" + yLevel + "  Z=" + finalZ;
        String tp = "/tp @s " + finalX + " " + yLevel + " " + finalZ;

        binding.tvFinalCoordinate.setText(coord);
        binding.tvTeleportCommand.setText(tp);

        binding.circularProgressIndicator.setProgress(100);
        binding.linearProgress.setProgress(100);
        binding.tvProgressText.setText("100%");

        updateVisibility(false, false, false, true, false);
    }

    private void showError(String message) {
        binding.tvErrorMessage.setText(message);
        updateVisibility(false, true, false, false, false);
    }

    private void resetSession() {
        searchStarted = false;
        startX = 0; widthX = 0;
        startZ = 0; widthZ = 0;
        roundX = 0; roundZ = 0;

        binding.etSearchRange.setText("1000");
        binding.etYLevel.setText("-63");
        binding.etPlayerName.setText("");
        binding.tvProgressText.setText("0%");
        binding.circularProgressIndicator.setProgress(0);

        updateVisibility(true, false, false, false, false);
    }

    private void updateProgress() {
        int totalSteps = (int) Math.ceil(Math.log(initialRange) / Math.log(2)) * 2;
        int remX = widthX > 1 ? (int) Math.ceil(Math.log(widthX) / Math.log(2)) : 0;
        int remZ = widthZ > 1 ? (int) Math.ceil(Math.log(widthZ) / Math.log(2)) : 0;
        int remaining = remX + remZ;

        int percent = totalSteps > 0 ? ((totalSteps - remaining) * 100 / totalSteps) : 100;
        percent = Math.min(percent, 100);

        binding.circularProgressIndicator.setProgress(percent);
        binding.linearProgress.setProgress(percent);
        binding.tvProgressText.setText(percent + "%");
    }

    private void updateVisibility(boolean settings, boolean error, boolean precision,
                                  boolean result, boolean quadrant) {
        binding.cardSettings.setVisibility(settings ? View.VISIBLE : View.GONE);
        binding.cardError.setVisibility(error ? View.VISIBLE : View.GONE);
        binding.cardPrecision.setVisibility(precision ? View.VISIBLE : View.GONE);
        binding.cardResult.setVisibility(result ? View.VISIBLE : View.GONE);
        binding.cardQuadrant.setVisibility(quadrant ? View.VISIBLE : View.GONE);
    }

    private void copyCommandToClipboard(com.google.android.material.textview.MaterialTextView tv) {
        String text = tv.getText().toString();
        if (text.isEmpty() || text.equals("--")) {
            showToast("暂无内容可复制");
            return;
        }
        copyToClipboard(text);
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Minecraft Command", text);
        clipboard.setPrimaryClip(clip);
        showToast("已复制到剪贴板");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
