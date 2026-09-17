package com.ironmanpath.sync;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/** Panel lateral: escribir el codigo, vincular, sincronizar ahora, desvincular. */
class IronmanPathSyncPanel extends PluginPanel
{
	private final IronmanPathSyncPlugin plugin;
	private final JLabel status = new JLabel();
	private final JLabel result = new JLabel();
	private final JTextField codeField = new JTextField();
	private final JButton linkButton = new JButton("Link with this code");
	private final JButton syncButton = new JButton("Sync now");
	private final JButton unlinkButton = new JButton("Unlink");
	private final JPanel codeBox = new JPanel();

	IronmanPathSyncPanel(IronmanPathSyncPlugin plugin)
	{
		super();
		this.plugin = plugin;
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Ironman Path Sync");
		title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
		title.setForeground(ColorScheme.BRAND_ORANGE);
		body.add(title);
		body.add(Box.createVerticalStrut(6));

		JLabel help = html("Get a code at <b>ironmanpath.app</b> &gt; Set builder &gt; My bank &gt; Link with RuneLite. Paste it here and press Link. Then open your bank once.");
		body.add(help);
		body.add(Box.createVerticalStrut(8));

		codeBox.setLayout(new BoxLayout(codeBox, BoxLayout.Y_AXIS));
		codeBox.setBackground(ColorScheme.DARK_GRAY_COLOR);
		codeField.setFont(codeField.getFont().deriveFont(Font.BOLD, 18f));
		codeField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
		codeField.setToolTipText("6-letter link code from the website");
		codeBox.add(codeField);
		codeBox.add(Box.createVerticalStrut(4));
		linkButton.setAlignmentX(LEFT_ALIGNMENT);
		linkButton.addActionListener(e -> plugin.linkWithCode(codeField.getText()));
		codeBox.add(linkButton);
		body.add(codeBox);
		body.add(Box.createVerticalStrut(8));

		status.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		body.add(status);
		body.add(Box.createVerticalStrut(8));

		JPanel buttons = new JPanel(new GridLayout(0, 1, 0, 4));
		buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		syncButton.setToolTipText("Send levels, inventory, worn items and the last bank the client saw (open your bank once per session).");
		syncButton.addActionListener(e -> plugin.syncNow());
		unlinkButton.addActionListener(e -> plugin.unlink());
		buttons.add(syncButton);
		buttons.add(unlinkButton);
		body.add(buttons);
		body.add(Box.createVerticalStrut(8));

		result.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		body.add(result);

		add(body, BorderLayout.NORTH);
		refresh();
	}

	private static JLabel html(String text)
	{
		JLabel l = new JLabel("<html><body style='width:180px'>" + text + "</body></html>");
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		return l;
	}

	/** Actualiza el panel segun el estado (vinculado o no). Se puede llamar desde cualquier hilo. */
	void refresh()
	{
		SwingUtilities.invokeLater(() ->
		{
			boolean linked = plugin.isLinked();
			codeBox.setVisible(!linked);
			unlinkButton.setVisible(linked);
			syncButton.setEnabled(linked || plugin.hasCode());
			if (linked)
			{
				status.setText("<html><body style='width:180px'><b style='color:#4caf50'>Linked</b> as <b>" + plugin.linkedRsn() + "</b>.<br>Bank is sent every time you open it.</body></html>");
			}
			else
			{
				status.setText("<html><body style='width:180px'>Not linked yet.</body></html>");
			}
			revalidate();
			repaint();
		});
	}

	void setResult(String text, boolean ok)
	{
		SwingUtilities.invokeLater(() ->
		{
			result.setText("<html><body style='width:180px;color:" + (ok ? "#4caf50" : "#ff5555") + "'>" + text + "</body></html>");
			revalidate();
			repaint();
		});
	}
}
