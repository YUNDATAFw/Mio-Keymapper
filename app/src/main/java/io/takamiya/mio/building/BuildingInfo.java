package io.takamiya.mio.building;

/**
 * 建筑文件信息（一个已转化的 sqlite 文件）。
 */
public final class BuildingInfo {

    /** 用户输入的建筑名（= sqlite 文件名，不含扩展名）。 */
    public final String name;
    /** db 文件绝对路径。 */
    public final String path;
    /** db 文件大小（字节）。 */
    public final long fileBytes;
    /** 源投影文件名（如 花雨庭.schem）。 */
    public final String sourceFile;
    /** 转换模式，固定 setblock。 */
    public final String mode;
    public final int sx, sy, sz;
    public final long totalCoords;
    /** 非空气方块数。 */
    public final long blockCount;
    /** 方块命令条数（commands 表行数）。 */
    public final int commandCount;
    /** 敏感词拆分/丢弃处理次数。 */
    public final int sensitiveFixCount;
    /** 无法映射被跳过的方块数。 */
    public final int skippedBlocks;
    /** 创建时间（epoch ms）。 */
    public final long createdAt;

    public BuildingInfo(String name, String path, long fileBytes, String sourceFile,
                        String mode, int sx, int sy, int sz, long totalCoords,
                        long blockCount, int commandCount, int sensitiveFixCount,
                        int skippedBlocks, long createdAt) {
        this.name = name;
        this.path = path;
        this.fileBytes = fileBytes;
        this.sourceFile = sourceFile;
        this.mode = mode;
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.totalCoords = totalCoords;
        this.blockCount = blockCount;
        this.commandCount = commandCount;
        this.sensitiveFixCount = sensitiveFixCount;
        this.skippedBlocks = skippedBlocks;
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "BuildingInfo{name='" + name + "', " + sx + "x" + sy + "x" + sz
                + ", commands=" + commandCount + ", bytes=" + fileBytes + "}";
    }
}
