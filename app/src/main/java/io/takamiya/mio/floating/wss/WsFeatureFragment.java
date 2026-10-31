package io.takamiya.mio.floating.wss;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.takamiya.mio.databinding.ItemWsFeatureBinding;
import io.takamiya.mio.databinding.LayoutWsPageFeatureBinding;
import io.takamiya.mio.dsl.DslPluginManager;
import io.takamiya.mio.dsl.WsFeatureRegistry;
import io.takamiya.mio.ws.feature.WsFeature;
import io.takamiya.mio.ws.feature.WsFeatureCenter;

public class WsFeatureFragment extends WsBaseFragment {

    private LayoutWsPageFeatureBinding binding;
    private FeatureAdapter adapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container) {
        binding = LayoutWsPageFeatureBinding.inflate(inflater, container, false);

        adapter = new FeatureAdapter();
        adapter.setListener(new FeatureAdapter.Listener() {
            @Override public void onToggle(String key, boolean checked) {
                routeToggle(key, checked);
            }
        });
        binding.rvWsFeatures.setLayoutManager(new LinearLayoutManager(inflater.getContext()));
        binding.rvWsFeatures.setAdapter(adapter);

        binding.etWsFeatureFilter.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override public void afterTextChanged(Editable s) {
                if (adapter != null) {
                    adapter.filter(s == null ? "" : s.toString());
                }
            }
        });

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(View view) {
        refreshFeatures();
    }

    /** 合并展示宿主内置功能（Java 继承 WsFeature）与插件动态注册功能。 */
    private void refreshFeatures() {
        if (adapter == null) return;
        Context ctx = getContext();
        if (ctx == null) {
            adapter.updateFeatures(null, new FeatureDef[0]);
            return;
        }
        List<FeatureDef> merged = new ArrayList<>();
        for (WsFeature f : WsFeatureCenter.get().all()) {
            merged.add(new FeatureDef(f.key(), f.name(), f.desc(), true));
            adapter.updateEnabled(f.key(), f.isEnabled());
        }
        for (WsFeatureRegistry.Entry e : WsFeatureRegistry.all()) {
            merged.add(new FeatureDef(e.key, e.name, e.desc));
        }
        adapter.updateFeatures(ctx, merged.toArray(new FeatureDef[0]));
    }

    /** 功能开关：Java 内置走 WsFeatureCenter，插件走 DSL 服务；无归属则撤销开关。 */
    private void routeToggle(String key, boolean checked) {
        if (WsFeatureCenter.get().toggle(key, checked)) return;
        DslPluginManager manager = DslPluginManager.get();
        boolean routed = manager != null && manager.routeFeatureToggle(key, checked);
        if (!routed) {
            adapter.updateEnabled(key, false);
        }
    }

    @Override
    public void onDetach() {
        binding = null;
        adapter = null;
        super.onDetach();
    }

    private static class FeatureDef {
        final String key;
        final int nameRes;
        final int descRes;
        final String name;
        final String desc;
        final boolean internal;

        FeatureDef(String key, int nameRes, int descRes) {
            this(key, nameRes, descRes, null, null, false);
        }

        FeatureDef(String key, String name, String desc) {
            this(key, 0, 0, name, desc, false);
        }

        FeatureDef(String key, String name, String desc, boolean internal) {
            this(key, 0, 0, name, desc, internal);
        }

        FeatureDef(String key, int nameRes, int descRes, String name, String desc, boolean internal) {
            this.key = key;
            this.nameRes = nameRes;
            this.descRes = descRes;
            this.name = name;
            this.desc = desc;
            this.internal = internal;
        }
    }

    private static class FeatureAdapter extends RecyclerView.Adapter<FeatureAdapter.ViewHolder> {

        private final List<FeatureDef> full = new ArrayList<>();
        private final List<FeatureDef> shown = new ArrayList<>();
        private final java.util.Map<String, Boolean> enabledMap = new java.util.HashMap<>();
        private Context context;
        private String filter = "";

        static class ViewHolder extends RecyclerView.ViewHolder {
            final ItemWsFeatureBinding binding;

            ViewHolder(@NonNull ItemWsFeatureBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }

        Listener listener;

        interface Listener {
            void onToggle(String key, boolean checked);
        }

        void setListener(Listener l) {
            listener = l;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(ItemWsFeatureBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            final FeatureDef def = shown.get(position);
            holder.binding.tvFeatureName.setText(nameOf(def));
            holder.binding.tvFeatureDesc.setText(descOf(def));
            boolean enabled = Boolean.TRUE.equals(enabledMap.get(def.key));
            // 避免 setChecked 触发监听导致循环/误操作
            holder.binding.swFeatureToggle.setOnCheckedChangeListener(null);
            holder.binding.swFeatureToggle.setChecked(enabled);
            holder.binding.swFeatureToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (listener != null) listener.onToggle(def.key, isChecked);
            });
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }

        void updateFeatures(Context context, FeatureDef[] features) {
            this.context = context;
            full.clear();
            for (FeatureDef f : features) {
                full.add(f);
            }
            applyFilter();
        }

        void updateEnabled(String key, boolean enabled) {
            enabledMap.put(key, enabled);
            notifyDataSetChanged();
        }

        void filter(String f) {
            filter = f == null ? "" : f.trim().toLowerCase();
            applyFilter();
        }

        private String nameOf(FeatureDef def) {
            String raw;
            if (def.nameRes != 0 && context != null) {
                raw = context.getString(def.nameRes);
            } else {
                raw = def.name != null && !def.name.isEmpty() ? def.name : def.key;
            }
            return def.internal ? "内置 · " + raw : raw;
        }

        private String descOf(FeatureDef def) {
            if (def.descRes != 0 && context != null) return context.getString(def.descRes);
            return def.desc != null ? def.desc : "";
        }

        private void applyFilter() {
            shown.clear();
            for (FeatureDef def : full) {
                String name = nameOf(def);
                if (filter.isEmpty() || name.toLowerCase().contains(filter)) {
                    shown.add(def);
                }
            }
            notifyDataSetChanged();
        }
    }
}