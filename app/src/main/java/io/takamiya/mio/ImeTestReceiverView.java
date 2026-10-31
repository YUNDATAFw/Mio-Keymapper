package io.takamiya.mio;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

/**
 * 输入法测试窗口的接收目标。
 * 不消费任何按键,注入的 KeyEvent 会沿视图层级冒泡到窗口的 dispatchKeyEvent,
 * 便于通过实时监听回读验证注入结果。
 */
public class ImeTestReceiverView extends View {

    public ImeTestReceiverView(Context context) {
        super(context);
        init();
    }

    public ImeTestReceiverView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ImeTestReceiverView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        outAttrs.inputType = EditorInfo.TYPE_CLASS_TEXT;
        outAttrs.imeOptions = EditorInfo.IME_NULL;
        return new BaseInputConnection(this, true);
    }
}