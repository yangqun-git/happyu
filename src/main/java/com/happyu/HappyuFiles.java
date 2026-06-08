package com.happyu;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;

final class HappyuFiles {
    private static final Key<HappyuVirtualFile> FILE_KEY = Key.create("happyu.browser.file");

    private HappyuFiles() {
    }

    static HappyuVirtualFile getOrCreate(Project project) {
        HappyuVirtualFile file = project.getUserData(FILE_KEY);
        if (file == null || !file.isValid()) {
            file = new HappyuVirtualFile();
            project.putUserData(FILE_KEY, file);
        }
        return file;
    }
}
