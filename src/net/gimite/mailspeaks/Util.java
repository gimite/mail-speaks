package net.gimite.mailspeaks;

import java.text.DateFormat;
import java.util.Date;

import android.database.Cursor;

public class Util {

    public static int getInt(Cursor cursor, String columnName) {
        int idx = cursor.getColumnIndexOrThrow(columnName);
        return cursor.getInt(idx);
    }
    
    public static long getLong(Cursor cursor, String columnName) {
        int idx = cursor.getColumnIndexOrThrow(columnName);
        return cursor.getLong(idx);
    }
    
    public static String getString(Cursor cursor, String columnName) {
        int idx = cursor.getColumnIndexOrThrow(columnName);
        return cursor.getString(idx);
    }
    
    public static Date getDate(Cursor cursor, String columnName) {
        int idx = cursor.getColumnIndexOrThrow(columnName);
        Date date = new Date();
        date.setTime(cursor.getLong(idx));
        return date;
    }
    
    public static String dateToString(Date date) {
        DateFormat format = DateFormat.getDateTimeInstance();
        return format.format(date);
    }
    
}
