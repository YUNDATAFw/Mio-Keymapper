package io.takamiya.mio.building;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 每建筑一个独立 db 的读写封装（原生 SQLite，无 ORM）。
 * 写入走 tmp 文件 + rename，读取只读打开。
 */
final class BuildingDb {

    private BuildingDb() {
    }

    /** 创建（或打开）可写 db 并建表；用于转换写入流程。 */
    static SQLiteDatabase openForWrite(File dbFile) {
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(dbFile, null);
        db.execSQL(BuildingContract.SQL_CREATE_META);
        db.execSQL(BuildingContract.SQL_CREATE_COMMANDS);
        return db;
    }

    /** 只读打开；文件不存在或损坏时抛异常，由调用方处理。 */
    static SQLiteDatabase openRead(File dbFile) {
        return SQLiteDatabase.openDatabase(
                dbFile.getPath(), null, SQLiteDatabase.OPEN_READONLY);
    }

    static void putMeta(SQLiteDatabase db, String key, String value) {
        ContentValues cv = new ContentValues();
        cv.put(BuildingContract.COL_KEY, key);
        cv.put(BuildingContract.COL_VALUE, value);
        db.insertWithOnConflict(BuildingContract.TABLE_META, null, cv,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    static String getMeta(SQLiteDatabase db, String key) {
        try (Cursor c = db.query(BuildingContract.TABLE_META,
                new String[]{BuildingContract.COL_VALUE},
                BuildingContract.COL_KEY + "=?", new String[]{key},
                null, null, null)) {
            return c.moveToFirst() ? c.getString(0) : null;
        }
    }

    /** 读取全部 meta 为 Map。 */
    static Map<String, String> getAllMeta(SQLiteDatabase db) {
        Map<String, String> map = new HashMap<>();
        try (Cursor c = db.query(BuildingContract.TABLE_META,
                new String[]{BuildingContract.COL_KEY, BuildingContract.COL_VALUE},
                null, null, null, null, null)) {
            while (c.moveToNext()) {
                map.put(c.getString(0), c.getString(1));
            }
        }
        return map;
    }

    static int commandCount(SQLiteDatabase db) {
        try (Cursor c = db.rawQuery(
                "SELECT COUNT(*) FROM " + BuildingContract.TABLE_COMMANDS, null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    static int metaInt(SQLiteDatabase db, String key, int def) {
        String v = getMeta(db, key);
        if (v == null) return def;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    static long metaLong(SQLiteDatabase db, String key, long def) {
        String v = getMeta(db, key);
        if (v == null) return def;
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
