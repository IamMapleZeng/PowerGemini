package com.vince.geminimi;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

public final class TimingSettingsProvider extends ContentProvider {
    private volatile Bundle diagnostics;

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (Diagnostics.READ.equals(method) && Binder.getCallingUid() == Process.myUid()) {
            Bundle snapshot = diagnostics;
            return snapshot == null ? null : new Bundle(snapshot);
        }
        if (Diagnostics.REPORT.equals(method) && Binder.getCallingUid() == Process.SYSTEM_UID) {
            diagnostics = new Bundle(extras);
            getContext().getContentResolver().notifyChange(Diagnostics.RESULT, null, 0);
            return Bundle.EMPTY;
        }
        throw new SecurityException("Diagnostics access denied");
    }

    @Override public boolean onCreate() { return true; }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        int uid = Binder.getCallingUid();
        if (uid != Process.SYSTEM_UID && uid != Process.myUid()) {
            throw new SecurityException("Timing settings are only readable by the app and Android");
        }
        if (!TimingSettings.URI.equals(uri)) throw new IllegalArgumentException("Unknown URI");
        MatrixCursor cursor = new MatrixCursor(new String[]{"delay_ms", TimingSettings.SCREEN_CONTEXT}, 1);
        cursor.addRow(new Object[]{TimingSettings.read(getContext()),
                TimingSettings.screenContextEnabled(getContext()) ? 1 : 0});
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
