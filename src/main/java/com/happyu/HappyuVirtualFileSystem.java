package com.happyu;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

final class HappyuVirtualFileSystem extends VirtualFileSystem {
    static final HappyuVirtualFileSystem INSTANCE = new HappyuVirtualFileSystem();
    static final String PROTOCOL = "happyu";

    private HappyuVirtualFileSystem() {
    }

    @Override
    public @NotNull String getProtocol() {
        return PROTOCOL;
    }

    @Override
    public @Nullable VirtualFile findFileByPath(@NotNull String path) {
        return null;
    }

    @Override
    public void refresh(boolean asynchronous) {
    }

    @Override
    public @Nullable VirtualFile refreshAndFindFileByPath(@NotNull String path) {
        return null;
    }

    @Override
    public void addVirtualFileListener(@NotNull VirtualFileListener listener) {
    }

    @Override
    public void removeVirtualFileListener(@NotNull VirtualFileListener listener) {
    }

    @Override
    protected void deleteFile(Object requestor, @NotNull VirtualFile vFile) throws IOException {
        throw new IOException("happyu tab is read-only");
    }

    @Override
    protected void moveFile(Object requestor, @NotNull VirtualFile vFile, @NotNull VirtualFile newParent) throws IOException {
        throw new IOException("happyu tab cannot be moved");
    }

    @Override
    protected void renameFile(Object requestor, @NotNull VirtualFile vFile, @NotNull String newName) throws IOException {
        throw new IOException("happyu tab cannot be renamed");
    }

    @Override
    protected @NotNull VirtualFile createChildFile(Object requestor, @NotNull VirtualFile vDir, @NotNull String fileName) throws IOException {
        throw new IOException("happyu tab is read-only");
    }

    @Override
    protected @NotNull VirtualFile createChildDirectory(Object requestor, @NotNull VirtualFile vDir, @NotNull String dirName) throws IOException {
        throw new IOException("happyu tab is read-only");
    }

    @Override
    protected @NotNull VirtualFile copyFile(Object requestor, @NotNull VirtualFile virtualFile, @NotNull VirtualFile newParent, @NotNull String copyName) throws IOException {
        throw new IOException("happyu tab cannot be copied");
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }
}
