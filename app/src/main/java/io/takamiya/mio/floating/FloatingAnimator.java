package io.takamiya.mio.floating;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.Nullable;

public final class FloatingAnimator {

    public static final long FAST_MS = 150;
    public static final long NORMAL_MS = 300;
    public static final long SLOW_MS = 350;

    private static final TimeInterpolator OVERSHOOT = new OvershootInterpolator(1.3f);
    private static final TimeInterpolator DECELERATE = new DecelerateInterpolator(1.5f);
    private static final TimeInterpolator ACCEL_DECEL = new AccelerateDecelerateInterpolator();
    private static final TimeInterpolator ACCELERATE = new AccelerateInterpolator();

    private FloatingAnimator() {}

    public static void fadeIn(View v) {
        fadeIn(v, FAST_MS, null);
    }

    public static void fadeIn(View v, long duration, @Nullable Runnable onEnd) {
        v.animate().cancel();
        v.setAlpha(0f);
        v.animate()
                .alpha(1f)
                .setDuration(duration)
                .setInterpolator(DECELERATE)
                .withEndAction(onEnd)
                .start();
    }

    public static void fadeOut(View v, long duration, @Nullable Runnable onEnd) {
        v.animate().cancel();
        v.animate()
                .alpha(0f)
                .setDuration(duration)
                .setInterpolator(ACCELERATE)
                .withEndAction(onEnd)
                .start();
    }

    public static void popIn(View v, long duration) {
        popIn(v, duration, null);
    }

    public static void popIn(View v, long duration, @Nullable Runnable onEnd) {
        AnimatorSet s = new AnimatorSet();
        s.playTogether(
                ObjectAnimator.ofFloat(v, View.SCALE_X, 0f, 1.1f, 1f),
                ObjectAnimator.ofFloat(v, View.SCALE_Y, 0f, 1.1f, 1f),
                ObjectAnimator.ofFloat(v, View.ALPHA, 0f, 1f));
        s.setDuration(duration);
        s.setInterpolator(OVERSHOOT);
        if (onEnd != null) {
            s.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {}

                @Override
                public void onAnimationEnd(Animator animation) {
                    onEnd.run();
                }

                @Override
                public void onAnimationCancel(Animator animation) {}

                @Override
                public void onAnimationRepeat(Animator animation) {}
            });
        }
        s.start();
    }

    public static void popOut(View v, long duration, @Nullable Runnable onEnd) {
        AnimatorSet s = new AnimatorSet();
        s.playTogether(
                ObjectAnimator.ofFloat(v, View.SCALE_X, 0f),
                ObjectAnimator.ofFloat(v, View.SCALE_Y, 0f),
                ObjectAnimator.ofFloat(v, View.ALPHA, 0f));
        s.setDuration(duration);
        s.setInterpolator(ACCELERATE);
        if (onEnd != null) {
            s.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {}

                @Override
                public void onAnimationEnd(Animator animation) {
                    onEnd.run();
                }

                @Override
                public void onAnimationCancel(Animator animation) {}

                @Override
                public void onAnimationRepeat(Animator animation) {}
            });
        }
        s.start();
    }

    public static void slideInFromStart(View v, float fromX, long duration, @Nullable Runnable onEnd) {
        v.animate().cancel();
        v.setAlpha(0f);
        v.setTranslationX(fromX);
        v.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(duration)
                .setInterpolator(DECELERATE)
                .withEndAction(onEnd)
                .start();
    }

    public static void slideOutToStart(View v, float toX, long duration, @Nullable Runnable onEnd) {
        v.animate().cancel();
        v.animate()
                .alpha(0f)
                .translationX(toX)
                .setDuration(duration)
                .setInterpolator(ACCELERATE)
                .withEndAction(onEnd)
                .start();
    }

    public static void zoomFadeIn(View v, long duration, @Nullable Runnable onEnd) {
        v.animate().cancel();
        v.setAlpha(0f);
        v.setScaleX(0.8f);
        v.setScaleY(0.8f);
        v.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(duration)
                .setInterpolator(ACCEL_DECEL)
                .withEndAction(onEnd)
                .start();
    }

    public static void zoomFadeOut(View v, long duration, @Nullable Runnable onEnd) {
        v.animate().cancel();
        v.animate()
                .alpha(0f)
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(duration)
                .setInterpolator(ACCEL_DECEL)
                .withEndAction(onEnd)
                .start();
    }

    public static void rotateIn(View v, long duration, @Nullable Runnable onEnd) {
        v.animate().cancel();
        v.setAlpha(0f);
        v.setRotation(-90f);
        v.animate()
                .alpha(1f)
                .rotation(0f)
                .setDuration(duration)
                .setInterpolator(OVERSHOOT)
                .withEndAction(onEnd)
                .start();
    }
}
