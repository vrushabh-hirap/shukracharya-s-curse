package com.vrushabhhirap.shukracharyascurse.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SQLite database helper for caching offline manuscript catalogues,
 * local audit logs, and search indexing tables.
 */
public class ArchiveDatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "ArchiveDatabase";
    private static final String DATABASE_NAME = "sanjivani_archive.db";
    private static final int DATABASE_VERSION = 3;

    // Tables
    public static final String TABLE_MANUSCRIPTS = "tbl_manuscripts";
    public static final String TABLE_AUDIT_LOGS = "tbl_audit_logs";
    public static final String TABLE_SYNC_STATE = "tbl_sync_state";
    public static final String TABLE_METADATA_TAGS = "tbl_metadata_tags";

    // Columns - TABLE_MANUSCRIPTS
    public static final String COL_ID = "_id";
    public static final String COL_DOC_ID = "doc_id";
    public static final String COL_CATALOG_NO = "catalog_no";
    public static final String COL_TITLE = "title";
    public static final String COL_AUTHOR = "author";
    public static final String COL_PERIOD = "period";
    public static final String COL_CLASSIFICATION = "classification";
    public static final String COL_EXCERPT = "excerpt";
    public static final String COL_SECTION_COUNT = "section_count";
    public static final String COL_SEALED = "is_sealed";
    public static final String COL_CHECKSUM = "checksum";
    public static final String COL_UPDATED_AT = "updated_at";

    // Columns - TABLE_AUDIT_LOGS
    public static final String COL_LOG_ID = "_id";
    public static final String COL_EVENT_TYPE = "event_type";
    public static final String COL_DETAILS = "details";
    public static final String COL_TIMESTAMP = "timestamp";
    public static final String COL_SESSION_REF = "session_ref";

    // Columns - TABLE_SYNC_STATE
    public static final String COL_SYNC_KEY = "sync_key";
    public static final String COL_SYNC_VALUE = "sync_value";
    public static final String COL_LAST_SYNC_TS = "last_sync_ts";

    // Columns - TABLE_METADATA_TAGS
    public static final String COL_TAG_ID = "_id";
    public static final String COL_TAG_DOC_REF = "doc_ref";
    public static final String COL_TAG_NAME = "tag_name";
    public static final String COL_TAG_WEIGHT = "tag_weight";

    public static class ManuscriptEntity implements Serializable {
        public long id;
        public String docId;
        public String catalogNo;
        public String title;
        public String author;
        public String period;
        public String classification;
        public String excerpt;
        public int sectionCount;
        public boolean isSealed;
        public String checksum;
        public long updatedAt;

        public ManuscriptEntity() {}

        public ManuscriptEntity(String docId, String catalogNo, String title, String author,
                                String period, String classification, String excerpt,
                                int sectionCount, boolean isSealed, String checksum) {
            this.docId = docId;
            this.catalogNo = catalogNo;
            this.title = title;
            this.author = author;
            this.period = period;
            this.classification = classification;
            this.excerpt = excerpt;
            this.sectionCount = sectionCount;
            this.isSealed = isSealed;
            this.checksum = checksum;
            this.updatedAt = System.currentTimeMillis();
        }
    }

    public static class AuditLogEntity {
        public long id;
        public String eventType;
        public String details;
        public long timestamp;
        public String sessionRef;

        public AuditLogEntity(String eventType, String details, String sessionRef) {
            this.eventType = eventType;
            this.details = details;
            this.sessionRef = sessionRef;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private static ArchiveDatabaseHelper sInstance;

    public static synchronized ArchiveDatabaseHelper getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new ArchiveDatabaseHelper(context.getApplicationContext());
        }
        return sInstance;
    }

    private ArchiveDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_MANUSCRIPTS + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_DOC_ID + " TEXT UNIQUE NOT NULL, " +
                COL_CATALOG_NO + " TEXT NOT NULL, " +
                COL_TITLE + " TEXT NOT NULL, " +
                COL_AUTHOR + " TEXT NOT NULL, " +
                COL_PERIOD + " TEXT, " +
                COL_CLASSIFICATION + " TEXT NOT NULL, " +
                COL_EXCERPT + " TEXT, " +
                COL_SECTION_COUNT + " INTEGER DEFAULT 1, " +
                COL_SEALED + " INTEGER DEFAULT 0, " +
                COL_CHECKSUM + " TEXT, " +
                COL_UPDATED_AT + " INTEGER" +
                ");");

        db.execSQL("CREATE TABLE " + TABLE_AUDIT_LOGS + " (" +
                COL_LOG_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_EVENT_TYPE + " TEXT NOT NULL, " +
                COL_DETAILS + " TEXT, " +
                COL_TIMESTAMP + " INTEGER NOT NULL, " +
                COL_SESSION_REF + " TEXT" +
                ");");

        db.execSQL("CREATE TABLE " + TABLE_SYNC_STATE + " (" +
                COL_SYNC_KEY + " TEXT PRIMARY KEY, " +
                COL_SYNC_VALUE + " TEXT, " +
                COL_LAST_SYNC_TS + " INTEGER" +
                ");");

        db.execSQL("CREATE TABLE " + TABLE_METADATA_TAGS + " (" +
                COL_TAG_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_TAG_DOC_REF + " TEXT NOT NULL, " +
                COL_TAG_NAME + " TEXT NOT NULL, " +
                COL_TAG_WEIGHT + " REAL DEFAULT 1.0" +
                ");");

        db.execSQL("CREATE INDEX idx_manuscripts_class ON " + TABLE_MANUSCRIPTS + " (" + COL_CLASSIFICATION + ");");
        db.execSQL("CREATE INDEX idx_audit_time ON " + TABLE_AUDIT_LOGS + " (" + COL_TIMESTAMP + ");");
        db.execSQL("CREATE INDEX idx_tags_doc ON " + TABLE_METADATA_TAGS + " (" + COL_TAG_DOC_REF + ");");

        seedInitialData(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.i(TAG, "Upgrading database schema from " + oldVersion + " to " + newVersion);
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE_MANUSCRIPTS + " ADD COLUMN " + COL_CHECKSUM + " TEXT;");
        }
        if (oldVersion < 3) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_METADATA_TAGS + " (" +
                    COL_TAG_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_TAG_DOC_REF + " TEXT NOT NULL, " +
                    COL_TAG_NAME + " TEXT NOT NULL, " +
                    COL_TAG_WEIGHT + " REAL DEFAULT 1.0);");
        }
    }

    private void seedInitialData(SQLiteDatabase db) {
        insertManuscriptDirect(db, new ManuscriptEntity(
                "MS-001", "MS-RIG-8821", "Vedic Hymns to Ushas", "Sage Vamadeva",
                "Early Vedic", "PUBLIC", "Praise of the dawn light across celestial realms.", 12, false, "A189C001"
        ));
        insertManuscriptDirect(db, new ManuscriptEntity(
                "MS-002", "MS-BHR-1049", "Lineage of the Bhrigus", "Chyavana Mahamuni",
                "Mid Vedic", "RESTRICTED", "History of fire sacrifices and astronomical alignment.", 8, true, "B902D441"
        ));
        insertManuscriptDirect(db, new ManuscriptEntity(
                "MS-003", "MS-SAN-0091", "Treatise on Mrita-Sanjivani Vidya", "Acharya Shukracharya",
                "Treta Yuga", "SEALED", "The sacred herbal restoration mantra and ninefold seals.", 16, true, "E491A772"
        ));
    }

    private long insertManuscriptDirect(SQLiteDatabase db, ManuscriptEntity entity) {
        ContentValues cv = new ContentValues();
        cv.put(COL_DOC_ID, entity.docId);
        cv.put(COL_CATALOG_NO, entity.catalogNo);
        cv.put(COL_TITLE, entity.title);
        cv.put(COL_AUTHOR, entity.author);
        cv.put(COL_PERIOD, entity.period);
        cv.put(COL_CLASSIFICATION, entity.classification);
        cv.put(COL_EXCERPT, entity.excerpt);
        cv.put(COL_SECTION_COUNT, entity.sectionCount);
        cv.put(COL_SEALED, entity.isSealed ? 1 : 0);
        cv.put(COL_CHECKSUM, entity.checksum);
        cv.put(COL_UPDATED_AT, entity.updatedAt);
        return db.insertWithOnConflict(TABLE_MANUSCRIPTS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public long insertManuscript(ManuscriptEntity entity) {
        SQLiteDatabase db = getWritableDatabase();
        return insertManuscriptDirect(db, entity);
    }

    public List<ManuscriptEntity> getAllManuscripts() {
        List<ManuscriptEntity> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_MANUSCRIPTS, null, null, null, null, null, COL_TITLE + " ASC");
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    list.add(cursorToManuscript(cursor));
                }
            } finally {
                cursor.close();
            }
        }
        return list;
    }

    public ManuscriptEntity getManuscriptByDocId(String docId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_MANUSCRIPTS, null, COL_DOC_ID + " = ?", new String[]{docId}, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    return cursorToManuscript(cursor);
                }
            } finally {
                cursor.close();
            }
        }
        return null;
    }

    public List<ManuscriptEntity> getManuscriptsByClassification(String classification) {
        List<ManuscriptEntity> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_MANUSCRIPTS, null, COL_CLASSIFICATION + " = ?", new String[]{classification}, null, null, COL_TITLE + " ASC");
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    list.add(cursorToManuscript(cursor));
                }
            } finally {
                cursor.close();
            }
        }
        return list;
    }

    public List<ManuscriptEntity> searchManuscripts(String query) {
        List<ManuscriptEntity> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String pattern = "%" + query + "%";
        Cursor cursor = db.query(TABLE_MANUSCRIPTS, null,
                COL_TITLE + " LIKE ? OR " + COL_AUTHOR + " LIKE ? OR " + COL_EXCERPT + " LIKE ?",
                new String[]{pattern, pattern, pattern}, null, null, COL_TITLE + " ASC");
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    list.add(cursorToManuscript(cursor));
                }
            } finally {
                cursor.close();
            }
        }
        return list;
    }

    public void logAuditEvent(String eventType, String details, String sessionRef) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_EVENT_TYPE, eventType);
        cv.put(COL_DETAILS, details);
        cv.put(COL_TIMESTAMP, System.currentTimeMillis());
        cv.put(COL_SESSION_REF, sessionRef);
        db.insert(TABLE_AUDIT_LOGS, null, cv);
    }

    public List<AuditLogEntity> getRecentAuditLogs(int limit) {
        List<AuditLogEntity> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_AUDIT_LOGS, null, null, null, null, null,
                COL_TIMESTAMP + " DESC", String.valueOf(limit));
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    AuditLogEntity entity = new AuditLogEntity(
                            cursor.getString(cursor.getColumnIndexOrThrow(COL_EVENT_TYPE)),
                            cursor.getString(cursor.getColumnIndexOrThrow(COL_DETAILS)),
                            cursor.getString(cursor.getColumnIndexOrThrow(COL_SESSION_REF))
                    );
                    entity.id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_LOG_ID));
                    entity.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP));
                    list.add(entity);
                }
            } finally {
                cursor.close();
            }
        }
        return list;
    }

    public void setSyncState(String key, String value) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_SYNC_KEY, key);
        cv.put(COL_SYNC_VALUE, value);
        cv.put(COL_LAST_SYNC_TS, System.currentTimeMillis());
        db.insertWithOnConflict(TABLE_SYNC_STATE, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public String getSyncState(String key, String defaultValue) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_SYNC_STATE, new String[]{COL_SYNC_VALUE},
                COL_SYNC_KEY + " = ?", new String[]{key}, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    return cursor.getString(0);
                }
            } finally {
                cursor.close();
            }
        }
        return defaultValue;
    }

    public int getManuscriptCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_MANUSCRIPTS, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    return cursor.getInt(0);
                }
            } finally {
                cursor.close();
            }
        }
        return 0;
    }

    private ManuscriptEntity cursorToManuscript(Cursor c) {
        ManuscriptEntity m = new ManuscriptEntity();
        m.id = c.getLong(c.getColumnIndexOrThrow(COL_ID));
        m.docId = c.getString(c.getColumnIndexOrThrow(COL_DOC_ID));
        m.catalogNo = c.getString(c.getColumnIndexOrThrow(COL_CATALOG_NO));
        m.title = c.getString(c.getColumnIndexOrThrow(COL_TITLE));
        m.author = c.getString(c.getColumnIndexOrThrow(COL_AUTHOR));
        m.period = c.getString(c.getColumnIndexOrThrow(COL_PERIOD));
        m.classification = c.getString(c.getColumnIndexOrThrow(COL_CLASSIFICATION));
        m.excerpt = c.getString(c.getColumnIndexOrThrow(COL_EXCERPT));
        m.sectionCount = c.getInt(c.getColumnIndexOrThrow(COL_SECTION_COUNT));
        m.isSealed = c.getInt(c.getColumnIndexOrThrow(COL_SEALED)) == 1;
        m.checksum = c.getString(c.getColumnIndexOrThrow(COL_CHECKSUM));
        m.updatedAt = c.getLong(c.getColumnIndexOrThrow(COL_UPDATED_AT));
        return m;
    }
}
