package com.frolo.muse.di.impl.permission;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.frolo.muse.permission.PermissionChecker;


public final class PermissionCheckerImpl implements PermissionChecker {

    private final Context mContext;

    public PermissionCheckerImpl(@NonNull Context context) {
        mContext = context;
    }

    @Override
    public boolean isQueryMediaContentPermissionGranted() {
        final String permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        final int result = ContextCompat.checkSelfPermission(mContext, permission);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void requireQueryMediaContentPermission() throws SecurityException {
        if (!isQueryMediaContentPermissionGranted()) {
            throw new SecurityException();
        }
    }

    @Override
    public boolean shouldRequestMediaPermissionInSettings() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
    }
}
