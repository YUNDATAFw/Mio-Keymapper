package io.takamiya.mio.floating.wss;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelStoreOwner;

import java.util.function.Consumer;

public abstract class WsBaseFragment {

    protected View rootView;
    private Context context;
    private ViewModelStoreOwner viewModelStoreOwner;
    private Consumer<String> showToastFn;

    public void onAttach(Context context) {
        this.context = context;
    }

    public void setViewModelStoreOwner(ViewModelStoreOwner owner) {
        this.viewModelStoreOwner = owner;
    }

    protected ViewModelStoreOwner getViewModelStoreOwner() {
        return viewModelStoreOwner;
    }

    /** 返回绑定的生命周期 Owner（浮窗服务，Service 销毁即销毁）。 */
    protected LifecycleOwner getLifecycleOwner() {
        return viewModelStoreOwner instanceof LifecycleOwner
                ? (LifecycleOwner) viewModelStoreOwner : null;
    }

    public void setShowToast(Consumer<String> fn) {
        this.showToastFn = fn;
    }

    protected void showToast(String message) {
        if (showToastFn != null) {
            showToastFn.accept(message);
        }
    }

    public abstract View onCreateView(LayoutInflater inflater, ViewGroup container);

    public void onViewCreated(View view) {
    }

    public View getView() {
        return rootView;
    }

    public Context getContext() {
        return context;
    }

    public void create(LayoutInflater inflater, ViewGroup container) {
        rootView = onCreateView(inflater, container);
        onViewCreated(rootView);
    }

    public void onDestroyView() {
    }

    public void onDetach() {
        rootView = null;
        context = null;
        viewModelStoreOwner = null;
    }
}