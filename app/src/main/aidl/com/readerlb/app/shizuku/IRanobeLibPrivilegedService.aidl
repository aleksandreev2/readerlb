package com.readerlb.app.shizuku;

import android.os.ParcelFileDescriptor;

interface IRanobeLibPrivilegedService {
    void destroy() = 16777114;

    boolean rootExists() = 1;
    boolean exists(String relativePath) = 2;
    boolean isDirectory(String relativePath) = 3;
    String[] listNames(String relativePath) = 4;
    long length(String relativePath) = 5;
    boolean mkdirs(String relativePath) = 6;
    boolean deleteRecursively(String relativePath) = 7;
    boolean rename(String fromRelativePath, String toRelativePath) = 8;
    ParcelFileDescriptor openRead(String relativePath) = 9;
    ParcelFileDescriptor openWrite(String relativePath) = 10;
}
