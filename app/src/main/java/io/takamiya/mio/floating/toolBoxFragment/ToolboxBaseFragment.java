package io.takamiya.mio.floating.toolBoxFragment;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.function.Consumer;

public abstract class ToolboxBaseFragment {

    protected View rootView;
    private Context context;
    private Consumer<String> showToastFn;

    public void onAttach(Context context) {
        this.context = context;
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
    }
}
