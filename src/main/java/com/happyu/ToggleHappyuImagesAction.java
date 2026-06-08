package com.happyu;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public final class ToggleHappyuImagesAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }

        FileEditor editor = FileEditorManager.getInstance(project).getSelectedEditor();
        if (editor instanceof HappyuEditor happyuEditor) {
            happyuEditor.toggleImages();
            return;
        }

        FileEditor[] opened = FileEditorManager.getInstance(project).openFile(HappyuFiles.getOrCreate(project), true);
        for (FileEditor openedEditor : opened) {
            if (openedEditor instanceof HappyuEditor happyuEditor) {
                happyuEditor.toggleImages();
                return;
            }
        }
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabled(event.getProject() != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
