package com.happyu.action;

import com.happyu.toolwindow.HappyuToolWindowFactory;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;

public final class SleepHappyuTabAction extends DumbAwareAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        HappyuToolWindowFactory.sleepTab(HappyuToolWindowFactory.findContextTab(event));
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        Content content = HappyuToolWindowFactory.findContextTab(event);
        event.getPresentation().setVisible(content != null);
        event.getPresentation().setEnabled(HappyuToolWindowFactory.canSleepTab(content));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
}
