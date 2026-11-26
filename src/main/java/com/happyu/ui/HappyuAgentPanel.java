package com.happyu.ui;

import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultEditorKit;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * A local-only composer: no browser, model client, background tasks or persisted messages.
 */
public final class HappyuAgentPanel extends JPanel {
    private final JPanel card;
    private final JTextArea prompt;
    private final JBLabel status = new JBLabel();
    private final JButton send = iconButton("send.svg", "Send locally");
    private final JButton mode = textButton("全自动", "bolt.svg");
    private final JButton model = textButton("GPT-6.1-Sol high", "agent.svg");
    private Color outline;
    private Color muted;
    private int panelHeight = JBUI.scale(168);

    public HappyuAgentPanel(Runnable hide) {
        super(new BorderLayout());
        setBorder(JBUI.Borders.empty(0, 8, 8, 8));
        getAccessibleContext().setAccessibleName("Agent conversation panel");
        JPanel grip = new JPanel() {
            @Override
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                graphics.setColor(outline);
                graphics.fillRoundRect((getWidth() - JBUI.scale(40)) / 2, JBUI.scale(3),
                        JBUI.scale(40), JBUI.scale(3), JBUI.scale(3), JBUI.scale(3));
            }
        };
        grip.setPreferredSize(JBUI.size(0, 10));
        grip.setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
        grip.setToolTipText("拖动调整对话面板高度");
        MouseAdapter resize = new MouseAdapter() {
            private int startY;
            private int startHeight;

            @Override
            public void mousePressed(MouseEvent event) {
                startY = event.getYOnScreen();
                startHeight = getHeight();
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                int maximum = getParent() == null ? JBUI.scale(400)
                        : Math.min(JBUI.scale(400), getParent().getHeight() - JBUI.scale(120));
                int minimum = JBUI.scale(140);
                maximum = Math.max(minimum, maximum);
                panelHeight = Math.max(minimum, Math.min(maximum, startHeight + startY - event.getYOnScreen()));
                revalidate();
            }
        };
        grip.addMouseListener(resize);
        grip.addMouseMotionListener(resize);
        add(grip, BorderLayout.NORTH);
        card = new JPanel(new BorderLayout(0, JBUI.scale(4))) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(getBackground());
                g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, JBUI.scale(12), JBUI.scale(12));
                g.dispose();
            }

            @Override
            protected void paintBorder(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(outline);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, JBUI.scale(12), JBUI.scale(12));
                g.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(JBUI.Borders.empty(6, 10, 6, 6));
        add(card, BorderLayout.CENTER);

        JPanel header = row();
        JButton attach = iconButton("attach.svg", "引用文件");
        attach.addActionListener(event -> insertReference());
        header.add(attach);
        header.add(new JBLabel("40%", IconLoader.getIcon("/icons/context.svg", getClass()), JBLabel.LEFT));
        JBLabel context = new JBLabel(" 文件上下文 ");
        context.setBorder(BorderFactory.createDashedBorder(UIUtil.getContextHelpForeground()));
        header.add(context);
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(header, BorderLayout.CENTER);
        JButton collapse = iconButton("chevron-down.svg", "Hide Agent Panel");
        collapse.addActionListener(event -> hide.run());
        top.add(collapse, BorderLayout.EAST);
        card.add(top, BorderLayout.NORTH);

        prompt = new JTextArea(3, 0) {
            @Override
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                if (!getText().isEmpty()) return;
                graphics.setFont(getFont());
                graphics.setColor(muted);
                FontMetrics metrics = graphics.getFontMetrics();
                String hint = "@引用文件，#唤起智能体，输入提示词，Enter 发送";
                int x = getInsets().left;
                int y = getInsets().top + metrics.getAscent();
                if (metrics.stringWidth(hint) <= getWidth() - x - getInsets().right) {
                    graphics.drawString(hint, x, y);
                } else {
                    graphics.drawString("@引用文件，#唤起智能体", x, y);
                    graphics.drawString("输入提示词，Enter 发送", x, y + metrics.getHeight());
                }
            }
        };
        prompt.setLineWrap(true);
        prompt.setWrapStyleWord(true);
        prompt.setFont(UIUtil.getLabelFont());
        prompt.setBorder(JBUI.Borders.empty(3, 0));
        prompt.setOpaque(false);
        prompt.getAccessibleContext().setAccessibleName("Agent prompt");
        JBScrollPane scroll = new JBScrollPane(prompt);
        scroll.setBorder(JBUI.Borders.empty());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        card.add(scroll, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(JBUI.scale(6), 0));
        footer.setOpaque(false);
        JPanel left = row();
        JButton options = iconButton("options.svg", "会话选项");
        options.addActionListener(event -> {
            JPopupMenu menu = new JPopupMenu();
            JMenuItem clear = new JMenuItem("清空本地对话");
            clear.addActionListener(action -> {
                prompt.setText("");
                status.setVisible(false);
            });
            menu.add(clear);
            menu.show(options, 0, -menu.getPreferredSize().height);
        });
        left.add(options);
        left.add(mode);
        footer.add(left, BorderLayout.WEST);
        footer.add(model, BorderLayout.CENTER);
        footer.add(send, BorderLayout.EAST);
        choices(mode, "全自动", "只读", "询问");
        choices(model, "GPT-6.1-Sol high", "GPT-6.1-Sol", "Auto");
        JPanel bottom = new JPanel(new BorderLayout(0, JBUI.scale(3)));
        bottom.setOpaque(false);
        status.setVisible(false);
        bottom.add(status, BorderLayout.NORTH);
        bottom.add(footer, BorderLayout.SOUTH);
        card.add(bottom, BorderLayout.SOUTH);
        send.setEnabled(false);
        send.setToolTipText("发送（本地演示，不连接模型）");
        send.addActionListener(event -> submit());
        prompt.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                update();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                update();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                update();
            }

            private void update() {
                send.setEnabled(!prompt.getText().isBlank());
            }
        });
        prompt.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "happyu.agent.send");
        prompt.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK),
                DefaultEditorKit.insertBreakAction);
        prompt.getActionMap().put("happyu.agent.send", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                submit();
            }
        });
        applyTheme();
    }

    private void insertReference() {
        prompt.insert("@", prompt.getCaretPosition());
        prompt.requestFocusInWindow();
    }

    private void submit() {
        if (prompt.getText().isBlank()) return;
        prompt.setText("");
        status.setText("已收到，正在分析项目结构…");
        status.setVisible(true);
        revalidate();
        prompt.requestFocusInWindow();
    }

    private static JPanel row() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0));
        row.setOpaque(false);
        return row;
    }

    private static JButton iconButton(String icon, String name) {
        JButton button = textButton("", icon);
        button.setToolTipText(name);
        button.getAccessibleContext().setAccessibleName(name);
        button.setPreferredSize(JBUI.size(22, 22));
        return button;
    }

    private static JButton textButton(String text, String icon) {
        JButton button = new JButton(text, IconLoader.getIcon("/icons/" + icon, HappyuAgentPanel.class));
        button.setBorder(JBUI.Borders.empty(2));
        button.setContentAreaFilled(false);
        button.setFont(UIUtil.getLabelFont().deriveFont(11f));
        button.setHorizontalAlignment(JButton.LEFT);
        return button;
    }

    private static void choices(JButton button, String... values) {
        button.addActionListener(event -> {
            JPopupMenu menu = new JPopupMenu();
            for (String value : values) {
                JMenuItem item = new JMenuItem(value);
                item.addActionListener(action -> button.setText(value + " ⌄"));
                menu.add(item);
            }
            menu.show(button, 0, -menu.getPreferredSize().height);
        });
        button.setText(button.getText() + " ⌄");
    }

    public void applyTheme() {
        Color background = UIUtil.getPanelBackground();
        Color foreground = UIUtil.getLabelForeground();
        boolean dark = ColorUtil.isDark(background);
        outline = dark ? new Color(0x414347) : new Color(0xC8CDD2);
        muted = UIUtil.getContextHelpForeground();
        colors(this, background, muted);
        card.setBackground(UIUtil.getTextFieldBackground());
        prompt.setForeground(foreground);
        prompt.setCaretColor(foreground);
        mode.setForeground(dark ? new Color(0xE5A323) : new Color(0xA36400));
        repaint();
    }

    private static void colors(JComponent component, Color background, Color foreground) {
        component.setBackground(background);
        component.setForeground(foreground);
        for (Component child : component.getComponents()) {
            if (child instanceof JComponent swing) colors(swing, background, foreground);
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(0, panelHeight);
    }
}
