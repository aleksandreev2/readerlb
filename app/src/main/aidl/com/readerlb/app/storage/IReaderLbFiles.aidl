package com.readerlb.app.storage;

import android.os.ParcelFileDescriptor;

interface IReaderLbFiles {
    boolean probe();
    String diagnostics();
    String[] list(String relativePath);
    String[] listEntries(String relativePath);
    boolean exists(String relativePath);
    boolean isDirectory(String relativePath);
    long length(String relativePath);
    long lastModified(String relativePath);
    ParcelFileDescriptor open(String relativePath, String mode);
    boolean create(String relativePath, boolean directory);
    boolean delete(String relativePath);
    boolean rename(String from, String to);
}
