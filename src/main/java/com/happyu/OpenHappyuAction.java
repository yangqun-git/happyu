package com.happyu;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public final class OpenHappyuAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }

        FileEditorManager.getInstance(project).openFile(HappyuFiles.getOrCreate(project), true);
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
