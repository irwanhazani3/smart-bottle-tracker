package com.example.smartbottle;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "smartbottle.db";
    private static final int DATABASE_VERSION = 1;

    // Table names
    public static final String TABLE_DRINK_LOGS = "drink_logs";
    public static final String TABLE_ACHIEVEMENTS = "achievements";

    // Column names
    public static final String COL_ID = "id";
    public static final String COL_AMOUNT = "amount";
    public static final String COL_TIMESTAMP = "timestamp";

    public static final String COL_ACH_ID = "ach_id";
    public static final String COL_TITLE = "title";
    public static final String COL_UNLOCKED_AT = "unlocked_at";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create Drink Logs Table
        String createDrinkLogsTable = "CREATE TABLE " + TABLE_DRINK_LOGS + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_AMOUNT + " INTEGER, " +
                COL_TIMESTAMP + " INTEGER" +
                ")";
        db.execSQL(createDrinkLogsTable);

        // Create Achievements Table
        String createAchievementsTable = "CREATE TABLE " + TABLE_ACHIEVEMENTS + " (" +
                COL_ACH_ID + " TEXT PRIMARY KEY, " +
                COL_TITLE + " TEXT, " +
                COL_UNLOCKED_AT + " INTEGER" +
                ")";
        db.execSQL(createAchievementsTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_DRINK_LOGS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ACHIEVEMENTS);
        onCreate(db);
    }

    // Insert new drink event
    public long addDrinkLog(int amountMl) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_AMOUNT, amountMl);
        values.put(COL_TIMESTAMP, System.currentTimeMillis());
        long id = db.insert(TABLE_DRINK_LOGS, null, values);
        db.close();
        return id;
    }

    // Get all drink logs for today
    public List<DrinkLog> getTodayLogs() {
        List<DrinkLog> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        long startOfToday = getStartOfToday();
        long endOfToday = startOfToday + (24 * 60 * 60 * 1000) - 1;

        Cursor cursor = db.query(TABLE_DRINK_LOGS,
                null,
                COL_TIMESTAMP + " >= ? AND " + COL_TIMESTAMP + " <= ?",
                new String[]{String.valueOf(startOfToday), String.valueOf(endOfToday)},
                null, null, COL_TIMESTAMP + " DESC");

        if (cursor.moveToFirst()) {
            do {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID));
                int amount = cursor.getInt(cursor.getColumnIndexOrThrow(COL_AMOUNT));
                long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP));
                list.add(new DrinkLog(id, amount, timestamp));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return list;
    }

    // Get total water consumed today
    public int getTodayTotalIntake() {
        SQLiteDatabase db = this.getReadableDatabase();
        long startOfToday = getStartOfToday();
        long endOfToday = startOfToday + (24 * 60 * 60 * 1000) - 1;

        Cursor cursor = db.rawQuery("SELECT SUM(" + COL_AMOUNT + ") FROM " + TABLE_DRINK_LOGS +
                " WHERE " + COL_TIMESTAMP + " >= ? AND " + COL_TIMESTAMP + " <= ?",
                new String[]{String.valueOf(startOfToday), String.valueOf(endOfToday)});

        int total = 0;
        if (cursor.moveToFirst()) {
            total = cursor.getInt(0);
        }
        cursor.close();
        db.close();
        return total;
    }

    // Delete a specific drink log
    public void deleteDrinkLog(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_DRINK_LOGS, COL_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    // Get weekly hydration data (Monday to Sunday)
    // Returns array of 7 integers corresponding to Mon (0) through Sun (6) of the CURRENT week
    public int[] getWeeklyData() {
        int[] weeklyData = new int[7];
        SQLiteDatabase db = this.getReadableDatabase();

        // Get starting timestamp of current Monday
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        
        // Adjust calendar to current Monday
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK); // Sunday=1, Monday=2, ...
        int daysSinceMonday = (dayOfWeek == Calendar.SUNDAY) ? 6 : (dayOfWeek - Calendar.MONDAY);
        cal.add(Calendar.DAY_OF_YEAR, -daysSinceMonday);
        
        long mondayStart = cal.getTimeInMillis();

        for (int i = 0; i < 7; i++) {
            long dayStart = mondayStart + (i * 24L * 60 * 60 * 1000);
            long dayEnd = dayStart + (24L * 60 * 60 * 1000) - 1;

            Cursor cursor = db.rawQuery("SELECT SUM(" + COL_AMOUNT + ") FROM " + TABLE_DRINK_LOGS +
                    " WHERE " + COL_TIMESTAMP + " >= ? AND " + COL_TIMESTAMP + " <= ?",
                    new String[]{String.valueOf(dayStart), String.valueOf(dayEnd)});

            if (cursor.moveToFirst()) {
                weeklyData[i] = cursor.getInt(0);
            } else {
                weeklyData[i] = 0;
            }
            cursor.close();
        }
        db.close();
        return weeklyData;
    }

    // Calculate current streak of meeting daily goals
    public int getCurrentStreak(int dailyGoal) {
        if (dailyGoal <= 0) return 0;

        SQLiteDatabase db = this.getReadableDatabase();
        int streak = 0;
        
        long startOfToday = getStartOfToday();
        long oneDayMs = 24L * 60 * 60 * 1000;

        // Check today first
        boolean metToday = false;
        Cursor cursorToday = db.rawQuery("SELECT SUM(" + COL_AMOUNT + ") FROM " + TABLE_DRINK_LOGS +
                " WHERE " + COL_TIMESTAMP + " >= ? AND " + COL_TIMESTAMP + " <= ?",
                new String[]{String.valueOf(startOfToday), String.valueOf(startOfToday + oneDayMs - 1)});
        if (cursorToday.moveToFirst()) {
            if (cursorToday.getInt(0) >= dailyGoal) {
                metToday = true;
                streak = 1;
            }
        }
        cursorToday.close();

        // Check backward from yesterday
        int offset = 1;
        while (true) {
            long dayStart = startOfToday - (offset * oneDayMs);
            long dayEnd = dayStart + oneDayMs - 1;

            Cursor cursor = db.rawQuery("SELECT SUM(" + COL_AMOUNT + ") FROM " + TABLE_DRINK_LOGS +
                    " WHERE " + COL_TIMESTAMP + " >= ? AND " + COL_TIMESTAMP + " <= ?",
                    new String[]{String.valueOf(dayStart), String.valueOf(dayEnd)});

            boolean met = false;
            if (cursor.moveToFirst()) {
                if (cursor.getInt(0) >= dailyGoal) {
                    met = true;
                }
            }
            cursor.close();

            if (met) {
                if (offset == 1 && !metToday) {
                    // Today not met, but yesterday is met. Streak is 1 so far
                    streak = 1;
                } else {
                    streak++;
                }
                offset++;
            } else {
                break;
            }
        }
        db.close();
        return streak;
    }

    // Unlock achievement
    public boolean unlockAchievement(String achId, String title) {
        SQLiteDatabase db = this.getWritableDatabase();
        // Check if already unlocked
        Cursor cursor = db.query(TABLE_ACHIEVEMENTS, null, COL_ACH_ID + " = ?", new String[]{achId}, null, null, null);
        boolean exists = cursor.getCount() > 0;
        cursor.close();

        if (exists) {
            db.close();
            return false; // already unlocked
        }

        ContentValues values = new ContentValues();
        values.put(COL_ACH_ID, achId);
        values.put(COL_TITLE, title);
        values.put(COL_UNLOCKED_AT, System.currentTimeMillis());
        db.insert(TABLE_ACHIEVEMENTS, null, values);
        db.close();
        return true; // newly unlocked
    }

    // Check if achievement is unlocked
    public boolean isAchievementUnlocked(String achId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_ACHIEVEMENTS, null, COL_ACH_ID + " = ?", new String[]{achId}, null, null, null);
        boolean unlocked = cursor.getCount() > 0;
        cursor.close();
        db.close();
        return unlocked;
    }

    // Get total water consumed ever
    public int getTotalIntakeEver() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT SUM(" + COL_AMOUNT + ") FROM " + TABLE_DRINK_LOGS, null);
        int total = 0;
        if (cursor.moveToFirst()) {
            total = cursor.getInt(0);
        }
        cursor.close();
        db.close();
        return total;
    }

    private long getStartOfToday() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
}