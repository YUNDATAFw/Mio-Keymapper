package io.takamiya.mio.tile;

import android.content.Intent;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import io.takamiya.mio.R;

public class InputMethodTileService extends TileService {

    private static final String TAG = "InputMethodTileService";

    @Override
    public void onStartListening() {
        super.onStartListening();
        setTileAsButton();
    }

    @Override
    public void onClick() {
        super.onClick();
        Intent intent = new Intent(this, ImePickerActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityAndCollapse(intent);
        setTileAsButton();
    }

    private void setTileAsButton() {
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(Tile.STATE_INACTIVE);
        tile.setLabel(getString(R.string.tile_ime_label));
        tile.setIcon(Icon.createWithResource(this, R.drawable.material_symbols_keyboard_outline_rounded));
        tile.updateTile();
    }

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        setTileAsButton();
    }
}
