package com.vince.geminimi;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Process;

public final class TimingSettingsProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        int uid = Binder.getCallingUid();
        if (uid != Process.SYSTEM_UID && uid != Process.myUid()) {
            throw new SecurityException("Timing settings are only readable by the app and Android");
        }
        if (!TimingSettings.URI.equals(uri)) throw new IllegalArgumentException("Unknown URI");
        MatrixCursor cursor = new MatrixCursor(new String[]{"delay_ms"}, 1);
        cursor.addRow(new Object[]{TimingSettings.read(getContext())});
        return cursor;
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String s, String[] args) {
        throw new UnsupportedOperationException();
    }
    @Override public int delete(Uri uri, String s, String[] args) {
        throw new UnsupportedOperationException();
    }
}
