package com.readerlb.app.shizuku;

import android.os.ParcelFileDescriptor;

interface IRanobeLibPrivilegedService {
    void destroy() = 16777114;

    boolean rootExists();
    boolean exists(String relativePath);
    boolean isDirectory(String relativePath);
    String[] listNames(String relativePath);
    long length(String relativePath);
    boolean mkdirs(String relativePath);
    boolean deleteRecursively(String relativePath);
    boolean rename(String fromRelativePath, String toRelativePath);
    ParcelFileDescriptor openRead(String relativePath);
    ParcelFileDescriptor openWrite(String relativePath);
}
