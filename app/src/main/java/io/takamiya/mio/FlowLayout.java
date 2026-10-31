package io.takamiya.mio;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

public class FlowLayout extends ViewGroup {

    public FlowLayout(Context context) {
        this(context, null);
    }

    public FlowLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FlowLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new MarginLayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateLayoutParams(LayoutParams p) {
        return new MarginLayoutParams(p);
    }

    @Override
    protected boolean checkLayoutParams(LayoutParams p) {
        return p instanceof MarginLayoutParams;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int paddingLeft = getPaddingLeft();
        int paddingRight = getPaddingRight();
        int paddingTop = getPaddingTop();
        int paddingBottom = getPaddingBottom();

        int maxChildWidth = widthSize - paddingLeft - paddingRight;
        int childWidthSpec = MeasureSpec.makeMeasureSpec(maxChildWidth,
                widthMode == MeasureSpec.EXACTLY ? MeasureSpec.AT_MOST : widthMode);

        int totalHeight = paddingTop + paddingBottom;
        int lineWidth = 0;
        int lineHeight = 0;
        int count = getChildCount();

        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == View.GONE) continue;
            MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
            measureChildWithMargins(child, childWidthSpec, 0, heightMeasureSpec, 0);
            int childWidth = child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
            int childHeight = child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;
            if (lineWidth > 0 && lineWidth + childWidth > maxChildWidth) {
                totalHeight += lineHeight;
                lineWidth = 0;
                lineHeight = 0;
            }
            lineWidth += childWidth;
            lineHeight = Math.max(lineHeight, childHeight);
        }
        totalHeight += lineHeight;

        setMeasuredDimension(
                resolveSize(widthSize, widthMeasureSpec),
                heightMode == MeasureSpec.EXACTLY ? heightSize : resolveSize(totalHeight, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int paddingLeft = getPaddingLeft();
        int maxWidth = r - l - paddingLeft - getPaddingRight();
        int x = paddingLeft;
        int y = getPaddingTop();
        int lineHeight = 0;
        int count = getChildCount();

        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == View.GONE) continue;
            MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();
            int required = lp.leftMargin + childWidth + lp.rightMargin;
            if (x != paddingLeft && x + required > paddingLeft + maxWidth) {
                x = paddingLeft;
                y += lineHeight;
                lineHeight = 0;
            }
            int left = x + lp.leftMargin;
            int top = y + lp.topMargin;
            child.layout(left, top, left + childWidth, top + childHeight);
            x += required;
            lineHeight = Math.max(lineHeight, lp.topMargin + childHeight + lp.bottomMargin);
        }
    }
}