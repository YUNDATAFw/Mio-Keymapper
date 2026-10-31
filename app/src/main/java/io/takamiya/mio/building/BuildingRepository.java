package io.takamiya.mio.building;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import io.takamiya.mio.building.core.Converter;

/**
 * 建筑文件管理工具：转换（写入 sqlite）与查询/管理。
 * 每个建筑文件 = 外部私有目录 buildings/&lt;名&gt;.db 一个独立 sqlite。
 * 转换单线程串行；查询方法在调用方线程执行。
 */
public final class BuildingRepository {

    /** 转换进度/结果回调（在后台线程触发）。 */
    public interface ConvertListener {
        void onProgress(int percent);

        void onSuccess(BuildingInfo info);

        void onError(Exception e);
    }

    /** 方块命令流式访问者；iterate 期间保持游标打开。 */
    public interface CommandVisitor {
        /** @return false 提前终止遍历 */
        boolean visit(int seq, String command);
    }

    private static final String BUILDINGS_DIR = "buildings";
    private static final String DB_SUFFIX = ".db";
    /** 非法文件名字符 → 下划线。 */
    private static final Pattern ILLEGAL = Pattern.compile("[\\\\/:*?\"<>|\\u0000-\\u001f]");
    private static final int MAX_NAME_LENGTH = 64;

    private final Context appContext;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "building-convert");
        t.setDaemon(true);
        return t;
    });

    public BuildingRepository(Context context) {
        this.appContext = context.getApplicationContext();
    }

    // ==================== 目录与命名 ====================

    /** 外部私有根目录 <externalFiles>/buildings。 */
    public File buildingsDir() throws IOException {
        File ext = appContext.getExternalFilesDir(null);
        if (ext == null) {
            throw new IOException("外部存储不可用");
        }
        File dir = new File(ext, BUILDINGS_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建建筑目录: " + dir);
        }
        return dir;
    }

    /**
     * 清洗用户输入名：去空白、非法字符替换为 _、限长；清洗后为空则拒绝。
     *
     * @return 清洗后的名字
     * @throws IllegalArgumentException 原始名为空或清洗后为空
     */
    public static String sanitizeName(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("名称不能为空");
        }
        String name = ILLEGAL.matcher(raw.trim()).replaceAll("_");
        name = name.replaceAll("^[.]+", "_");           // 防 .. 与隐藏文件
        if (name.length() > MAX_NAME_LENGTH) {
            name = name.substring(0, MAX_NAME_LENGTH);
        }
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("名称无效");
        }
        return name;
    }

    private File dbFile(String name) {
        return new File(buildingsDirUnsafe(), name + DB_SUFFIX);
    }

    /** 内部使用：目录创建失败时仍给出路径对象用于存在性判断。 */
    private File buildingsDirUnsafe() {
        File ext = appContext.getExternalFilesDir(null);
        File root = ext != null ? ext : new File(appContext.getFilesDir(), "external");
        return new File(root, BUILDINGS_DIR);
    }

    // ==================== 转换 ====================

    /**
     * 转换并落盘（后台线程）。
     *
     * @param raw            投影文件字节
     * @param sourceFileName 原始文件名（判断 schem/litematic）
     * @param rawName        用户输入的建筑名（会 sanitize）
     */
    public void convertAsync(byte[] raw, String sourceFileName, String rawName,
                             ConvertListener listener) {
        final String name;
        try {
            name = sanitizeName(rawName);
        } catch (IllegalArgumentException e) {
            listener.onError(e);
            return;
        }
        executor.execute(() -> {
            try {
                BuildingInfo info = convertBlocking(raw, sourceFileName, name, listener);
                listener.onSuccess(info);
            } catch (Exception e) {
                listener.onError(e);
            }
        });
    }

    /**
     * 转换并落盘（当前线程；勿在主线程调用）。
     * 全程写 &lt;名&gt;.db.tmp，成功后 rename；失败删除 tmp 不留半成品。
     * 重名直接报错不覆盖。
     */
    public BuildingInfo convertBlocking(byte[] raw, String sourceFileName, String rawName,
                                        ConvertListener listener) throws Exception {
        String name = sanitizeName(rawName);
        File dir = buildingsDir();
        File target = new File(dir, name + DB_SUFFIX);
        if (target.exists()) {
            throw new IOException("建筑文件已存在: " + name);
        }
        File tmp = new File(dir, name + DB_SUFFIX + ".tmp");
        // 清理上次残留
        //noinspection ResultOfMethodCallIgnored
        tmp.delete();

        Converter.ProgressListener progress = listener == null ? null : listener::onProgress;
        Converter.Result result;
        try {
            result = Converter.convert(raw, sourceFileName,
                    MappingLoader.getMappings(appContext),
                    MappingLoader.getSensitive(appContext),
                    progress);
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw e;
        }

        long createdAt = System.currentTimeMillis();
        try {
            SQLiteDatabase db = BuildingDb.openForWrite(tmp);
            try {
                db.beginTransaction();
                putMeta(db, name, sourceFileName, result, createdAt);
                insertCommands(db, result.blockCommands);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
                db.close();
            }
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw e;
        }

        if (!tmp.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw new IOException("无法移动转换结果: " + target);
        }
        return getInfo(name);
    }

    private static void putMeta(SQLiteDatabase db, String name, String sourceFileName,
                                Converter.Result r, long createdAt) {
        BuildingDb.putMeta(db, BuildingContract.META_SCHEMA_VERSION,
                String.valueOf(BuildingContract.SCHEMA_VERSION));
        BuildingDb.putMeta(db, BuildingContract.META_NAME, name);
        BuildingDb.putMeta(db, BuildingContract.META_SOURCE_FILE, sourceFileName);
        BuildingDb.putMeta(db, BuildingContract.META_MODE, BuildingContract.MODE_SETBLOCK);
        BuildingDb.putMeta(db, BuildingContract.META_SX, String.valueOf(r.sx));
        BuildingDb.putMeta(db, BuildingContract.META_SY, String.valueOf(r.sy));
        BuildingDb.putMeta(db, BuildingContract.META_SZ, String.valueOf(r.sz));
        BuildingDb.putMeta(db, BuildingContract.META_TOTAL_COORDS, String.valueOf(r.totalCoords));
        BuildingDb.putMeta(db, BuildingContract.META_BLOCK_COUNT, String.valueOf(r.blockCount));
        BuildingDb.putMeta(db, BuildingContract.META_COMMAND_COUNT,
                String.valueOf(r.blockCommands.size()));
        BuildingDb.putMeta(db, BuildingContract.META_SENSITIVE_FIX_COUNT,
                String.valueOf(r.sensitiveFixCount));
        BuildingDb.putMeta(db, BuildingContract.META_SKIPPED_BLOCKS,
                String.valueOf(r.skippedBlocks));
        BuildingDb.putMeta(db, BuildingContract.META_CREATED_AT, String.valueOf(createdAt));
        BuildingDb.putMeta(db, BuildingContract.META_MANUAL_EXTRA, join(r.manualExtraCommands));
    }

    private static void insertCommands(SQLiteDatabase db, List<String> commands) {
        ContentValues cv = new ContentValues(2);
        for (int i = 0; i < commands.size(); i++) {
            cv.clear();
            cv.put(BuildingContract.COL_SEQ, i + 1);
            cv.put(BuildingContract.COL_COMMAND, commands.get(i));
            db.insert(BuildingContract.TABLE_COMMANDS, null, cv);
        }
    }

    private static String join(List<String> lines) {
        if (lines == null || lines.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines.get(i));
        }
        return sb.toString();
    }

    private static List<String> splitLines(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        for (String line : text.split("\n", -1)) {
            out.add(line);
        }
        return out;
    }

    // ==================== 管理：列表 / 信息 / 删除 / 重命名 ====================

    /** 列出全部建筑文件信息；损坏文件跳过。 */
    public List<BuildingInfo> listBuildings() {
        List<BuildingInfo> out = new ArrayList<>();
        File dir = buildingsDirUnsafe();
        File[] files = dir.listFiles((d, n) -> n.endsWith(DB_SUFFIX));
        if (files == null) return out;
        for (File f : files) {
            String n = f.getName();
            n = n.substring(0, n.length() - DB_SUFFIX.length());
            try {
                out.add(getInfo(n));
            } catch (Exception ignored) {
                // 损坏的 db 跳过
            }
        }
        return out;
    }

    public boolean exists(String name) {
        return dbFile(sanitizeName(name)).exists();
    }

    /** 建筑文件信息；不存在抛 IOException。 */
    public BuildingInfo getInfo(String name) throws IOException {
        name = sanitizeName(name);
        File f = dbFile(name);
        if (!f.exists()) {
            throw new IOException("建筑文件不存在: " + name);
        }
        SQLiteDatabase db;
        try {
            db = BuildingDb.openRead(f);
        } catch (Exception e) {
            throw new IOException("建筑文件无法打开: " + name, e);
        }
        try {
            return readInfo(name, f, db);
        } finally {
            db.close();
        }
    }

    private static BuildingInfo readInfo(String name, File f, SQLiteDatabase db) {
        String sourceFile = str(db, BuildingContract.META_SOURCE_FILE, "");
        String mode = str(db, BuildingContract.META_MODE, BuildingContract.MODE_SETBLOCK);
        int sx = BuildingDb.metaInt(db, BuildingContract.META_SX, 0);
        int sy = BuildingDb.metaInt(db, BuildingContract.META_SY, 0);
        int sz = BuildingDb.metaInt(db, BuildingContract.META_SZ, 0);
        long totalCoords = BuildingDb.metaLong(db, BuildingContract.META_TOTAL_COORDS, 0);
        long blockCount = BuildingDb.metaLong(db, BuildingContract.META_BLOCK_COUNT, 0);
        int commandCount = BuildingDb.metaInt(db, BuildingContract.META_COMMAND_COUNT, 0);
        int fix = BuildingDb.metaInt(db, BuildingContract.META_SENSITIVE_FIX_COUNT, 0);
        int skipped = BuildingDb.metaInt(db, BuildingContract.META_SKIPPED_BLOCKS, 0);
        long createdAt = BuildingDb.metaLong(db, BuildingContract.META_CREATED_AT, 0);
        return new BuildingInfo(name, f.getAbsolutePath(), f.length(), sourceFile, mode,
                sx, sy, sz, totalCoords, blockCount, commandCount, fix, skipped, createdAt);
    }

    private static String str(SQLiteDatabase db, String key, String def) {
        String v = BuildingDb.getMeta(db, key);
        return v != null ? v : def;
    }

    /** 删除建筑文件（含 wal/shm）。不存在静默成功。 */
    public void delete(String name) {
        name = sanitizeName(name);
        File base = dbFile(name);
        //noinspection ResultOfMethodCallIgnored
        base.delete();
        //noinspection ResultOfMethodCallIgnored
        new File(base.getPath() + "-wal").delete();
        //noinspection ResultOfMethodCallIgnored
        new File(base.getPath() + "-shm").delete();
        //noinspection ResultOfMethodCallIgnored
        new File(base.getPath() + ".tmp").delete();
    }

    /** 重命名（文件级）；新名已存在则报错。 */
    public void rename(String oldName, String newName) throws IOException {
        oldName = sanitizeName(oldName);
        newName = sanitizeName(newName);
        File oldF = dbFile(oldName);
        File newF = dbFile(newName);
        if (!oldF.exists()) {
            throw new IOException("建筑文件不存在: " + oldName);
        }
        if (newF.exists()) {
            throw new IOException("建筑文件已存在: " + newName);
        }
        if (!oldF.renameTo(newF)) {
            throw new IOException("重命名失败: " + oldName + " -> " + newName);
        }
        // 同步 meta 内的名字（尽力而为，失败不影响文件已改名的事实）
        try {
            SQLiteDatabase db = BuildingDb.openForWrite(newF);
            try {
                BuildingDb.putMeta(db, BuildingContract.META_NAME, newName);
            } finally {
                db.close();
            }
        } catch (Exception ignored) {
        }
    }

    // ==================== 命令读取 ====================

    /** 只读打开（sanitizedName 校验 + 存在性检查）。 */
    private SQLiteDatabase openForRead(String name) throws IOException {
        name = sanitizeName(name);
        File f = dbFile(name);
        if (!f.exists()) {
            throw new IOException("建筑文件不存在: " + name);
        }
        try {
            return BuildingDb.openRead(f);
        } catch (Exception e) {
            throw new IOException("建筑文件无法打开: " + name, e);
        }
    }

    /** [0] 手动执行命令（固定 6 条 + 敏感词手动行，由 meta 重建；过滤非命令的提示文本）。 */
    public List<String> getManualCommands(String name) throws IOException {
        SQLiteDatabase db = openForRead(name);
        try {
            List<String> extra = splitLines(
                    BuildingDb.getMeta(db, BuildingContract.META_MANUAL_EXTRA));
            List<String> all = CommandTemplates.buildManualCommands(extra);
            // 模板里的“你需要在聊天栏执行以下命令喵～”等提示行不是命令，不入列表
            List<String> commands = new ArrayList<>(all.size());
            for (String line : all) {
                if (line != null && line.startsWith("/")) commands.add(line);
            }
            return commands;
        } finally {
            db.close();
        }
    }

    /** [1] 其他命令（模板动态 11 条，由 meta 尺寸重建）。 */
    public List<String> getOtherCommands(String name) throws IOException {
        SQLiteDatabase db = openForRead(name);
        try {
            int sx = BuildingDb.metaInt(db, BuildingContract.META_SX, 0);
            int sz = BuildingDb.metaInt(db, BuildingContract.META_SZ, 0);
            long totalCoords = BuildingDb.metaLong(db, BuildingContract.META_TOTAL_COORDS, 0);
            return CommandTemplates.buildOtherCommands(sx, sz, totalCoords);
        } finally {
            db.close();
        }
    }

    /**
     * [2] 方块命令分页读取（seq 升序），用于大建筑的列表展示。
     *
     * @param offset 跳过条数（0 起）
     * @param limit  本页条数
     */
    public List<String> getBlockCommands(String name, int offset, int limit) throws IOException {
        SQLiteDatabase db = openForRead(name);
        List<String> out = new ArrayList<>();
        try (Cursor c = db.query(BuildingContract.TABLE_COMMANDS,
                new String[]{BuildingContract.COL_COMMAND},
                null, null, null, null,
                BuildingContract.COL_SEQ + " ASC",
                Math.max(0, offset) + "," + Math.max(1, limit))) {
            while (c.moveToNext()) {
                out.add(c.getString(0));
            }
        } finally {
            db.close();
        }
        return out;
    }

    /**
     * 完整可执行命令序列 = [0]手动（固定+敏感词）+ [1]其他（模板动态）+ [2]方块（sqlite）。
     * 大建筑注意内存；流式访问用 {@link #iterateBlockCommands}。
     */
    public List<String> getAllCommands(String name) throws IOException {
        name = sanitizeName(name);
        File f = dbFile(name);
        if (!f.exists()) {
            throw new IOException("建筑文件不存在: " + name);
        }
        SQLiteDatabase db;
        try {
            db = BuildingDb.openRead(f);
        } catch (Exception e) {
            throw new IOException("建筑文件无法打开: " + name, e);
        }
        try {
            List<String> manualExtra = splitLines(
                    BuildingDb.getMeta(db, BuildingContract.META_MANUAL_EXTRA));
            int sx = BuildingDb.metaInt(db, BuildingContract.META_SX, 0);
            int sz = BuildingDb.metaInt(db, BuildingContract.META_SZ, 0);
            long totalCoords = BuildingDb.metaLong(db, BuildingContract.META_TOTAL_COORDS, 0);

            List<String> all = new ArrayList<>();
            all.addAll(CommandTemplates.buildManualCommands(manualExtra));
            all.addAll(CommandTemplates.buildOtherCommands(sx, sz, totalCoords));
            try (Cursor c = db.query(BuildingContract.TABLE_COMMANDS,
                    new String[]{BuildingContract.COL_COMMAND},
                    null, null, null, null,
                    BuildingContract.COL_SEQ + " ASC")) {
                while (c.moveToNext()) {
                    all.add(c.getString(0));
                }
            }
            return all;
        } finally {
            db.close();
        }
    }

    /** 仅 [2] 方块命令（sqlite 全量，按 seq 升序）。 */
    public List<String> getBlockCommands(String name) throws IOException {
        List<String> out = new ArrayList<>();
        iterateBlockCommands(name, (seq, cmd) -> {
            out.add(cmd);
            return true;
        });
        return out;
    }

    /** 指定序号的单条方块命令（seq 从 1 起）；越界返回 null。 */
    public String getCommand(String name, int seq) throws IOException {
        name = sanitizeName(name);
        File f = dbFile(name);
        if (!f.exists()) {
            throw new IOException("建筑文件不存在: " + name);
        }
        SQLiteDatabase db;
        try {
            db = BuildingDb.openRead(f);
        } catch (Exception e) {
            throw new IOException("建筑文件无法打开: " + name, e);
        }
        try (Cursor c = db.query(BuildingContract.TABLE_COMMANDS,
                new String[]{BuildingContract.COL_COMMAND},
                BuildingContract.COL_SEQ + "=?", new String[]{String.valueOf(seq)},
                null, null, null)) {
            return c.moveToFirst() ? c.getString(0) : null;
        } finally {
            db.close();
        }
    }

    /**
     * 流式遍历方块命令（游标窗口读取，不全量载入内存）。
     * visitor 返回 false 提前结束。
     */
    public void iterateBlockCommands(String name, CommandVisitor visitor) throws IOException {
        name = sanitizeName(name);
        File f = dbFile(name);
        if (!f.exists()) {
            throw new IOException("建筑文件不存在: " + name);
        }
        SQLiteDatabase db;
        try {
            db = BuildingDb.openRead(f);
        } catch (Exception e) {
            throw new IOException("建筑文件无法打开: " + name, e);
        }
        try (Cursor c = db.query(BuildingContract.TABLE_COMMANDS,
                new String[]{BuildingContract.COL_SEQ, BuildingContract.COL_COMMAND},
                null, null, null, null,
                BuildingContract.COL_SEQ + " ASC")) {
            int seqIdx = c.getColumnIndex(BuildingContract.COL_SEQ);
            int cmdIdx = c.getColumnIndex(BuildingContract.COL_COMMAND);
            while (c.moveToNext()) {
                if (!visitor.visit(c.getInt(seqIdx), c.getString(cmdIdx))) {
                    break;
                }
            }
        } finally {
            db.close();
        }
    }

    /** 释放后台转换线程。 */
    public void shutdown() {
        executor.shutdown();
    }
}
