package io.takamiya.mio.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.HashMap;
import java.util.Map;

/**
 * 高度自定义二维码生成工具类
 * 支持：圆点像素、圆角矩形像素、圆角边框、自定义探测图形样式、Logo嵌入
 */
public class CustomQRCodeUtil {

    // ========== 像素形状枚举 ==========
    public enum PixelShape {
        RECT,           // 矩形（默认）
        CIRCLE,         // 圆点
        ROUND_RECT,     // 圆角矩形
        DIAMOND,        // 菱形（钻石）
        HEXAGON,        // 六边形
    }

    // ========== 探测图形样式枚举 ==========
    public enum DetectStyle {
        RECT,           // 矩形（默认）
        ROUND_RECT,     // 圆角矩形
        CIRCLE,         // 圆形
    }

    // ========== 配置类 ==========
    public static class QRConfig {
        public int width = 500;                     // 二维码宽度
        public int height = 500;                    // 二维码高度
        public int margin = 2;                      // 白边（单位：像素块数量）
        public int foregroundColor = Color.BLACK;   // 前景色
        public int backgroundColor = Color.WHITE;   // 背景色
        public PixelShape pixelShape = PixelShape.RECT;      // 普通像素形状
        public DetectStyle detectStyle = DetectStyle.RECT;   // 探测图形样式
        public int detectOutColor = Color.BLACK;    // 探测图形外圈颜色
        public int detectInnerColor = Color.BLACK;  // 探测图形内圈颜色
        public int borderRadius = 0;                // 整体圆角半径（0=直角）
        public int borderWidth = 0;                 // 边框宽度（0=无边框）
        public int borderColor = Color.BLACK;       // 边框颜色
        public float pixelScale = 0.8f;             // 像素缩放比例（0~1，圆点/菱形用）
        public Bitmap logo = null;                  // Logo图片
        public float logoScale = 0.2f;              // Logo占二维码比例（建议0.15~0.25）
    }

    /**
     * 生成自定义样式二维码
     */
    public static Bitmap createQRCode(String content, QRConfig config) {
        try {
            // 1. 设置编码参数
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H); // 最高容错，支持Logo
            hints.put(EncodeHintType.MARGIN, config.margin);

            // 2. 生成 BitMatrix
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, config.width, config.height, hints);

            // 3. 计算实际参数
            int matrixWidth = matrix.getWidth();
            int matrixHeight = matrix.getHeight();
            int cellSize = Math.min(config.width / matrixWidth, config.height / matrixHeight);
            int actualWidth = cellSize * matrixWidth;
            int actualHeight = cellSize * matrixHeight;

            // 4. 创建画布
            Bitmap bitmap = Bitmap.createBitmap(actualWidth, actualHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            // 5. 裁剪画布为圆角（确保所有后续绘制都被限制在圆角区域内）
            if (config.borderRadius > 0) {
                Path clipPath = new Path();
                clipPath.addRoundRect(
                    new RectF(0, 0, actualWidth, actualHeight),
                    config.borderRadius, config.borderRadius, Path.Direction.CW);
                canvas.clipPath(clipPath);
            }

            // 6. 绘制背景
            paint.setColor(config.backgroundColor);
            canvas.drawRect(0, 0, actualWidth, actualHeight, paint);

            // 6. 探测图形大小（通常是 7x7）
            int detectSize = 7;

            // 7. 遍历矩阵绘制每个像素
            for (int y = 0; y < matrixHeight; y++) {
                for (int x = 0; x < matrixWidth; x++) {
                    if (!matrix.get(x, y)) continue; // 跳过空白点

                    float cx = x * cellSize + cellSize / 2f;
                    float cy = y * cellSize + cellSize / 2f;
                    float size = cellSize * config.pixelScale;
                    float halfSize = size / 2f;

                    // 判断是否为探测图形（三个定位角）
                    boolean isDetect = (x < detectSize && y < detectSize)           // 左上
                            || (x < detectSize && y >= matrixHeight - detectSize)   // 左下
                            || (x >= matrixWidth - detectSize && y < detectSize);    // 右上

                    if (isDetect) {
                        // 探测图形分层绘制：外圈 + 内圈
                        drawDetectPixel(canvas, paint, cx, cy, cellSize, x, y, detectSize, config);
                    } else {
                        // 普通数据点
                        drawPixel(canvas, paint, cx, cy, halfSize, config.pixelShape, config.foregroundColor);
                    }
                }
            }

            // 8. 绘制边框（如果有）
            if (config.borderWidth > 0) {
                drawBorder(canvas, paint, actualWidth, actualHeight, config);
            }

            // 9. 嵌入 Logo（如果有）
            if (config.logo != null) {
                bitmap = addLogo(bitmap, config.logo, config.logoScale);
            }

            return bitmap;

        } catch (WriterException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 绘制普通像素点
     */
    private static void drawPixel(Canvas canvas, Paint paint, float cx, float cy, float halfSize,
                                   PixelShape shape, int color) {
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);

        switch (shape) {
            case RECT:
                canvas.drawRect(cx - halfSize, cy - halfSize, cx + halfSize, cy + halfSize, paint);
                break;

            case CIRCLE:
                canvas.drawCircle(cx, cy, halfSize, paint);
                break;

            case ROUND_RECT:
                float radius = halfSize * 0.5f;
                canvas.drawRoundRect(
                    cx - halfSize, cy - halfSize, cx + halfSize, cy + halfSize,
                    radius, radius, paint);
                break;

            case DIAMOND:
                Path diamondPath = new Path();
                diamondPath.moveTo(cx, cy - halfSize);
                diamondPath.lineTo(cx + halfSize, cy);
                diamondPath.lineTo(cx, cy + halfSize);
                diamondPath.lineTo(cx - halfSize, cy);
                diamondPath.close();
                canvas.drawPath(diamondPath, paint);
                break;

            case HEXAGON:
                Path hexPath = new Path();
                float r = halfSize;
                for (int i = 0; i < 6; i++) {
                    float angle = (float) (Math.PI / 3 * i - Math.PI / 6);
                    float px = cx + r * (float) Math.cos(angle);
                    float py = cy + r * (float) Math.sin(angle);
                    if (i == 0) hexPath.moveTo(px, py);
                    else hexPath.lineTo(px, py);
                }
                hexPath.close();
                canvas.drawPath(hexPath, paint);
                break;
        }
    }

    /**
     * 绘制探测图形（定位角）
     */
    private static void drawDetectPixel(Canvas canvas, Paint paint, float cx, float cy, float cellSize,
                                         int x, int y, int detectSize, QRConfig config) {
        // 绝对坐标 → 相对探测图形坐标（0~6）
        int rx = x % detectSize;
        int ry = y % detectSize;

        // 内点区域（中心 3x3）
        boolean isCenter = (rx >= 2 && rx <= 4) && (ry >= 2 && ry <= 4);
        // 外环区域（7x7 最外层周长）
        boolean isOuter = rx == 0 || rx == 6 || ry == 0 || ry == 6;
        // 中间环（白色间隔区）保持背景色，不绘制

        float halfSize = cellSize / 2f;

        switch (config.detectStyle) {
            case CIRCLE: {
                if (isOuter || isCenter) {
                    // 每个格子画一个分离的圆（用 pixelScale 缩小半径，圆点风格更统一）
                    paint.setColor(isCenter ? config.detectInnerColor : config.detectOutColor);
                    float r = cellSize * config.pixelScale / 2f;
                    canvas.drawCircle(cx, cy, r, paint);
                }
                break;
            }

            case ROUND_RECT: {
                float radius = halfSize * 0.3f;
                if (isOuter) {
                    paint.setColor(config.detectOutColor);
                    canvas.drawRoundRect(
                            cx - halfSize, cy - halfSize, cx + halfSize, cy + halfSize,
                            radius, radius, paint);
                } else if (isCenter) {
                    paint.setColor(config.detectInnerColor);
                    canvas.drawRoundRect(
                            cx - halfSize * 0.6f, cy - halfSize * 0.6f,
                            cx + halfSize * 0.6f, cy + halfSize * 0.6f,
                            radius, radius, paint);
                }
                break;
            }

            default: { // RECT
                if (isOuter) {
                    paint.setColor(config.detectOutColor);
                    canvas.drawRect(cx - halfSize, cy - halfSize, cx + halfSize, cy + halfSize, paint);
                } else if (isCenter) {
                    paint.setColor(config.detectInnerColor);
                    canvas.drawRect(cx - halfSize * 0.6f, cy - halfSize * 0.6f,
                            cx + halfSize * 0.6f, cy + halfSize * 0.6f, paint);
                }
                break;
            }
        }
    }

    /**
     * 绘制边框
     */
    private static void drawBorder(Canvas canvas, Paint paint, int width, int height, QRConfig config) {
        paint.setColor(config.borderColor);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(config.borderWidth);

        if (config.borderRadius > 0) {
            canvas.drawRoundRect(
                new RectF(config.borderWidth / 2f, config.borderWidth / 2f,
                    width - config.borderWidth / 2f, height - config.borderWidth / 2f),
                config.borderRadius, config.borderRadius, paint);
        } else {
            canvas.drawRect(
                config.borderWidth / 2f, config.borderWidth / 2f,
                width - config.borderWidth / 2f, height - config.borderWidth / 2f, paint);
        }
    }

    /**
     * 添加 Logo 到二维码中心
     */
    private static Bitmap addLogo(Bitmap qrBitmap, Bitmap logo, float scale) {
        int qrWidth = qrBitmap.getWidth();
        int qrHeight = qrBitmap.getHeight();

        // 计算 Logo 大小
        int logoSize = (int) (qrWidth * scale);
        Bitmap scaledLogo = Bitmap.createScaledBitmap(logo, logoSize, logoSize, true);

        // 创建新 Bitmap
        Bitmap result = Bitmap.createBitmap(qrWidth, qrHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        canvas.drawBitmap(qrBitmap, 0, 0, null);

        // 计算 Logo 位置（居中）
        int left = (qrWidth - logoSize) / 2;
        int top = (qrHeight - logoSize) / 2;

        // 绘制白色圆角背景（可选）
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        float bgRadius = logoSize * 0.15f;
        canvas.drawRoundRect(
            new RectF(left - 4, top - 4, left + logoSize + 4, top + logoSize + 4),
            bgRadius, bgRadius, paint);

        // 绘制 Logo
        canvas.drawBitmap(scaledLogo, left, top, null);

        return result;
    }

    // ========== 便捷方法 ==========

    /**
     * 快速生成圆点风格二维码
     */
    public static Bitmap createCircleQRCode(String content, int size, int color) {
        QRConfig config = new QRConfig();
        config.width = size;
        config.height = size;
        config.pixelShape = PixelShape.CIRCLE;
        config.foregroundColor = color;
        config.pixelScale = 0.85f;
        return createQRCode(content, config);
    }

    /**
     * 快速生成圆角矩形风格二维码
     */
    public static Bitmap createRoundRectQRCode(String content, int size, int color, int borderRadius) {
        QRConfig config = new QRConfig();
        config.width = size;
        config.height = size;
        config.pixelShape = PixelShape.ROUND_RECT;
        config.foregroundColor = color;
        config.borderRadius = borderRadius;
        config.pixelScale = 0.9f;
        return createQRCode(content, config);
    }
}