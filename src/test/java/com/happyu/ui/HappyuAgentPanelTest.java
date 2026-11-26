package com.happyu.ui;

import com.intellij.testFramework.LightPlatformTestCase;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.text.DefaultEditorKit;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class HappyuAgentPanelTest extends LightPlatformTestCase {
    public void testLocalSendAndHideAndMultilineInput() {
        AtomicInteger hidden = new AtomicInteger();
        HappyuAgentPanel panel = new HappyuAgentPanel(hidden::incrementAndGet);
        JTextArea prompt = find(panel, JTextArea.class, null);
        JButton send = find(panel, JButton.class, "Send locally");
        assertFalse(send.isEnabled());
        prompt.setText("请检查项目中的线程处理");
        assertTrue(send.isEnabled());
        assertEquals(DefaultEditorKit.insertBreakAction, prompt.getInputMap().get(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)));
        Object enter = prompt.getInputMap().get(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
        prompt.getActionMap().get(enter).actionPerformed(new ActionEvent(prompt, 0, "enter"));
        assertEquals("", prompt.getText());
        assertFalse(send.isEnabled());
        find(panel, JButton.class, "Hide Agent Panel").doClick();
        assertEquals(1, hidden.get());
    }

    public void testComposerRendersInLightAndDarkThemesAndNarrowWindows() throws Exception {
        Map<String, Object> old = new HashMap<>();
        for (String key : new String[]{"Panel.background", "TextField.background", "Label.foreground"}) {
            old.put(key, UIManager.get(key));
        }
        try {
            Path output = Path.of("build/tmp/agent-panel-preview");
            Files.createDirectories(output);
            for (boolean dark : new boolean[]{false, true}) {
                UIManager.put("Panel.background", new Color(dark ? 0x252526 : 0xF2F3F5));
                UIManager.put("TextField.background", new Color(dark ? 0x1E1E1E : 0xFFFFFF));
                UIManager.put("Label.foreground", new Color(dark ? 0xD4D4D4 : 0x24272B));
                HappyuAgentPanel panel = new HappyuAgentPanel(() -> {
                });
                for (int width : new int[]{538, 280}) {
                    panel.setSize(width, 168);
                    layout(panel);
                    JTextArea prompt = find(panel, JTextArea.class, null);
                    JButton send = find(panel, JButton.class, "Send locally");
                    assertTrue(prompt.getWidth() > 0);
                    assertTrue(send.getVisibleRect().width > 0);
                    BufferedImage image = new BufferedImage(width, 168, BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = image.createGraphics();
                    panel.printAll(graphics);
                    graphics.dispose();
                    ImageIO.write(image, "png", output.resolve((dark ? "dark" : "light") + "-" + width + ".png").toFile());
                }
            }
        } finally {
            old.forEach(UIManager::put);
        }
    }

    private static void layout(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) if (child instanceof Container container) layout(container);
    }

    private static <T extends JComponent> T find(Container parent, Class<T> type, String name) {
        for (Component child : parent.getComponents()) {
            if (type.isInstance(child) && (name == null || name.equals(
                    ((JComponent) child).getAccessibleContext().getAccessibleName()))) return type.cast(child);
            if (child instanceof Container container) {
                T result = find(container, type, name);
                if (result != null) return result;
            }
        }
        return null;
    }
}
