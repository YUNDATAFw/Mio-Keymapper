package io.takamiya.mio.floating.toolBoxFragment.tool;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import io.takamiya.mio.databinding.FragmentToolBlockCoordinateConversionBinding;
import io.takamiya.mio.floating.toolBoxFragment.ToolboxBaseFragment;

public class ToolBlockCoordinateConversionFragment extends ToolboxBaseFragment {

    private static final int CHUNK_SIZE = 16;
    private static final int REGION_SIZE = 32;

    private FragmentToolBlockCoordinateConversionBinding binding;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container) {
        binding = FragmentToolBlockCoordinateConversionBinding.inflate(inflater, container, false);

        binding.btnToChunk.setOnClickListener(v -> convertToChunk());
        binding.btnToBlock.setOnClickListener(v -> convertToBlock());

        return binding.getRoot();
    }

    private void convertToChunk() {
        int bx = parseInput(binding.etBlockX);
        int by = parseInput(binding.etBlockY);
        int bz = parseInput(binding.etBlockZ);

        int chunkX = Math.floorDiv(bx, CHUNK_SIZE);
        int chunkY = Math.floorDiv(by, CHUNK_SIZE);
        int chunkZ = Math.floorDiv(bz, CHUNK_SIZE);

        binding.etChunkX.setText(String.valueOf(chunkX));
        binding.etChunkY.setText(String.valueOf(chunkY));
        binding.etChunkZ.setText(String.valueOf(chunkZ));

        int localX = Math.floorMod(bx, CHUNK_SIZE);
        int localY = Math.floorMod(by, CHUNK_SIZE);
        int localZ = Math.floorMod(bz, CHUNK_SIZE);

        int regionX = Math.floorDiv(chunkX, REGION_SIZE);
        int regionZ = Math.floorDiv(chunkZ, REGION_SIZE);

        int nwX = chunkX * CHUNK_SIZE;
        int nwZ = chunkZ * CHUNK_SIZE;
        int seX = nwX + CHUNK_SIZE - 1;
        int seZ = nwZ + CHUNK_SIZE - 1;
        int center = CHUNK_SIZE / 2;

        String result = String.format(
                "局部坐标: (%d, %d, %d)\n" +
                "区域文件: r.%d.%d.mca\n" +
                "区块边界:\n" +
                "  西北: (%d, %d, %d)\n" +
                "  东南: (%d, %d, %d)\n" +
                "  中心: (%d, %d, %d)",
                localX, localY, localZ,
                regionX, regionZ,
                nwX, by, nwZ,
                seX, by, seZ,
                nwX + center, by, nwZ + center);

        binding.tvResult.setText(result);
    }

    private void convertToBlock() {
        int cx = parseInput(binding.etChunkX);
        int cy = parseInput(binding.etChunkY);
        int cz = parseInput(binding.etChunkZ);

        int blockX = cx * CHUNK_SIZE;
        int blockY = cy * CHUNK_SIZE;
        int blockZ = cz * CHUNK_SIZE;

        binding.etBlockX.setText(String.valueOf(blockX));
        binding.etBlockY.setText(String.valueOf(blockY));
        binding.etBlockZ.setText(String.valueOf(blockZ));

        int regionX = Math.floorDiv(cx, REGION_SIZE);
        int regionZ = Math.floorDiv(cz, REGION_SIZE);

        String result = String.format(
                "方块范围:\n" +
                "  X: %d ~ %d\n" +
                "  Y: %d ~ %d\n" +
                "  Z: %d ~ %d\n\n" +
                "区域文件: r.%d.%d.mca",
                blockX, blockX + CHUNK_SIZE - 1,
                blockY, blockY + CHUNK_SIZE - 1,
                blockZ, blockZ + CHUNK_SIZE - 1,
                regionX, regionZ);

        binding.tvResult.setText(result);
    }

    private int parseInput(com.google.android.material.textfield.TextInputEditText et) {
        String text = et.getText().toString().trim();
        if (text.isEmpty()) return 0;
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            showToast("输入无效: " + text);
            return 0;
        }
    }

    @Override
    public void onDetach() {
        binding = null;
        super.onDetach();
    }
}
