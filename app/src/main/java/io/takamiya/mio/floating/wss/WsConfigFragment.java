package io.takamiya.mio.floating.wss;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.List;

import io.takamiya.mio.databinding.LayoutWsPageConfigBinding;
import io.takamiya.mio.ws.WsViewModel;

public class WsConfigFragment extends WsBaseFragment {

    private LayoutWsPageConfigBinding binding;
    private WsViewModel viewModel;
    private ArrayAdapter<String> playerAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container) {
        binding = LayoutWsPageConfigBinding.inflate(inflater, container, false);

        playerAdapter = new ArrayAdapter<>(inflater.getContext(),
                android.R.layout.simple_list_item_1, new ArrayList<>());
        AutoCompleteTextView actv = binding.actvWsPlayer;
        actv.setAdapter(playerAdapter);
        actv.setThreshold(0);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(View view) {
        LifecycleOwner owner = getLifecycleOwner();
        viewModel = (owner == null)
                ? null
                : new ViewModelProvider((androidx.lifecycle.ViewModelStoreOwner) owner)
                        .get(WsViewModel.class);
        if (viewModel == null) return;

        viewModel.players().observe(owner, players -> {
            if (binding == null || playerAdapter == null) return;
            String current = binding.actvWsPlayer.getText() == null
                    ? "" : binding.actvWsPlayer.getText().toString().trim();
            playerAdapter.clear();
            if (players != null) {
                playerAdapter.addAll(players);
            }
            playerAdapter.notifyDataSetChanged();
            if (!current.isEmpty()) {
                binding.actvWsPlayer.setText(current, false);
            } else if (players != null && players.size() == 1) {
                binding.actvWsPlayer.setText(players.get(0), false);
            }
        });
    }

    @Override
    public void onDetach() {
        viewModel = null;
        binding = null;
        playerAdapter = null;
        super.onDetach();
    }
}