package com.happyu;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class HappyuVirtualFile extends VirtualFile {
    private static final byte[] EMPTY_CONTENT = new byte[0];
    private static final String NAME = "happyu.java";
    private static final String PATH = HappyuVirtualFileSystem.PROTOCOL + "://browser";

    private final long createdAt = System.currentTimeMillis();
    private boolean valid = true;

    @Override
    public @NotNull String getName() {
        return NAME;
    }

    @Override
    public @NotNull VirtualFileSystem getFileSystem() {
        return HappyuVirtualFileSystem.INSTANCE;
    }

    @Override
    public @NotNull String getPath() {
        return PATH;
    }

    @Override
    public boolean isWritable() {
        return false;
    }

    @Override
    public boolean isDirectory() {
        return false;
    }

    @Override
    public boolean isValid() {
        return valid;
    }

    void invalidate() {
        valid = false;
    }

    @Override
    public @Nullable VirtualFile getParent() {
        return null;
    }

    @Override
    public VirtualFile @NotNull [] getChildren() {
        return EMPTY_ARRAY;
    }

    @Override
    public @NotNull FileType getFileType() {
        return UnknownFileType.INSTANCE;
    }

    @Override
    public @NotNull OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
        throw new IOException("happyu tab is read-only");
    }

    @Override
    public byte @NotNull [] contentsToByteArray() {
        return EMPTY_CONTENT;
    }

    @Override
    public long getTimeStamp() {
        return createdAt;
    }

    @Override
    public long getLength() {
        return 0;
    }

    @Override
    public void refresh(boolean asynchronous, boolean recursive, @Nullable Runnable postRunnable) {
        if (postRunnable != null) {
            postRunnable.run();
        }
    }

    @Override
    public @NotNull InputStream getInputStream() {
        return new ByteArrayInputStream(EMPTY_CONTENT);
    }

    @Override
    public @NotNull String getPresentableName() {
        return "happyu";
    }

}
