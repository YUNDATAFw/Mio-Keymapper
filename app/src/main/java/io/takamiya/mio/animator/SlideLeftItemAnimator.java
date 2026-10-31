package io.takamiya.mio.animator;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class SlideLeftItemAnimator extends DefaultItemAnimator {

    private final List<RecyclerView.ViewHolder> mPendingAdd = new ArrayList<>();

    @Override
    public boolean animateAdd(RecyclerView.ViewHolder holder) {
        View view = holder.itemView;

        view.setTranslationX(-view.getRootView().getWidth());
        view.setAlpha(0f);

        mPendingAdd.add(holder);
        return true;
    }

    @Override
    public void runPendingAnimations() {
        if (!mPendingAdd.isEmpty()) {
            final List<RecyclerView.ViewHolder> addList = new ArrayList<>(mPendingAdd);
            mPendingAdd.clear();

            for (int i = 0; i < addList.size(); i++) {
                final RecyclerView.ViewHolder holder = addList.get(i);
                final View view = holder.itemView;

                view.animate()
                        .translationX(0f)
                        .alpha(1f)
                        .setDuration(300)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                dispatchAddFinished(holder);
                            }

                            @Override
                            public void onAnimationCancel(Animator animation) {
                                view.setTranslationX(0f);
                                view.setAlpha(1f);
                            }
                        })
                        .start();
            }
        }

        super.runPendingAnimations();
    }

    @Override
    public boolean animateRemove(RecyclerView.ViewHolder holder) {
        View view = holder.itemView;

        view.animate()
                .translationX(-view.getRootView().getWidth())
                .alpha(0f)
                .setDuration(getRemoveDuration())
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.setTranslationX(0f);
                        view.setAlpha(1f);
                        dispatchRemoveFinished(holder);
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        view.setTranslationX(0f);
                        view.setAlpha(1f);
                    }
                })
                .start();
        return true;
    }

    @Override
    public boolean animateMove(RecyclerView.ViewHolder holder, int fromX, int fromY, int toX, int toY) {
        View view = holder.itemView;

        if (fromX != toX) {
            view.setTranslationX(fromX - toX);
            view.animate()
                    .translationX(0f)
                    .setDuration(getMoveDuration())
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            dispatchMoveFinished(holder);
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {
                            view.setTranslationX(0f);
                        }
                    })
                    .start();
        } else {
            return super.animateMove(holder, fromX, fromY, toX, toY);
        }
        return true;
    }

    @Override
    public boolean animateChange(@NonNull RecyclerView.ViewHolder oldHolder,
                                  @NonNull RecyclerView.ViewHolder newHolder,
                                  int fromLeft, int fromTop, int toLeft, int toTop) {
        if (oldHolder == newHolder) {
            return super.animateChange(oldHolder, newHolder, fromLeft, fromTop, toLeft, toTop);
        }

        final View oldView = oldHolder.itemView;
        final View newView = newHolder.itemView;

        oldView.setAlpha(0f);
        newView.setTranslationX(newView.getRootView().getWidth());
        newView.setAlpha(0f);

        ObjectAnimator oldAlpha = ObjectAnimator.ofFloat(oldView, "alpha", 0f);
        ObjectAnimator newTranslationX = ObjectAnimator.ofFloat(newView, "translationX", newView.getRootView().getWidth(), 0f);
        ObjectAnimator newAlpha = ObjectAnimator.ofFloat(newView, "alpha", 0f, 1f);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(oldAlpha, newTranslationX, newAlpha);
        set.setDuration(getChangeDuration());
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                oldView.setAlpha(1f);
                newView.setTranslationX(0f);
                newView.setAlpha(1f);
                dispatchChangeFinished(oldHolder, true);
                dispatchChangeFinished(newHolder, false);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                oldView.setAlpha(1f);
                newView.setTranslationX(0f);
                newView.setAlpha(1f);
            }
        });
        set.start();
        return true;
    }
}
