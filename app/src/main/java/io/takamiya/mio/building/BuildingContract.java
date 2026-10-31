package io.takamiya.mio.building;

/**
 * 建筑 sqlite 常量：每个建筑文件（= 一次转换）一个独立 db。
 * 表：meta（键值元数据）+ commands（仅方块命令，seq 即执行顺序）。
 */
public final class BuildingContract {

    private BuildingContract() {
    }

    public static final int SCHEMA_VERSION = 1;

    public static final String TABLE_META = "meta";
    public static final String TABLE_COMMANDS = "commands";

    public static final String COL_KEY = "key";
    public static final String COL_VALUE = "value";

    public static final String COL_SEQ = "seq";
    public static final String COL_COMMAND = "command";

    // meta 键
    public static final String META_SCHEMA_VERSION = "schema_version";
    public static final String META_NAME = "name";
    public static final String META_SOURCE_FILE = "source_file";
    public static final String META_MODE = "mode";
    public static final String META_SX = "sx";
    public static final String META_SY = "sy";
    public static final String META_SZ = "sz";
    public static final String META_TOTAL_COORDS = "total_coords";
    public static final String META_BLOCK_COUNT = "block_count";
    public static final String META_COMMAND_COUNT = "command_count";
    public static final String META_SENSITIVE_FIX_COUNT = "sensitive_fix_count";
    public static final String META_SKIPPED_BLOCKS = "skipped_blocks";
    public static final String META_CREATED_AT = "created_at";
    /** 敏感词手动 setblock 命令，多行文本，无则空串。 */
    public static final String META_MANUAL_EXTRA = "manual_extra";

    public static final String MODE_SETBLOCK = "setblock";

    /** meta → commands 表的 DDL。 */
    public static final String SQL_CREATE_META =
            "CREATE TABLE IF NOT EXISTS " + TABLE_META + " ("
                    + COL_KEY + " TEXT PRIMARY KEY, "
                    + COL_VALUE + " TEXT NOT NULL)";

    public static final String SQL_CREATE_COMMANDS =
            "CREATE TABLE IF NOT EXISTS " + TABLE_COMMANDS + " ("
                    + COL_SEQ + " INTEGER PRIMARY KEY, "
                    + COL_COMMAND + " TEXT NOT NULL)";
}
