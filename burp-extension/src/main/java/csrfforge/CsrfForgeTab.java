package csrfforge;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The "CSRF PoC Forge" suite tab. Holds a raw-request editor on the left and the
 * generated PoC on the right, with controls to choose the scheme, toggle
 * auto-submit, and copy or save the result. Requests sent from the right-click
 * menu populate the editor and generate immediately.
 */
public class CsrfForgeTab {

    private static final String SAMPLE_REQUEST =
            "POST /my-account/change-email HTTP/1.1\n"
                    + "Host: vulnerable.example.com\n"
                    + "Content-Type: application/x-www-form-urlencoded\n"
                    + "Cookie: session=REPLACE_ME\n"
                    + "\n"
                    + "email=attacker%40evil.com";

    private final MontoyaApi api;
    private final JPanel root;
    private final JTextArea requestArea;
    private final JTextArea outputArea;
    private final JComboBox<String> schemeBox;
    private final JCheckBox autoSubmitBox;
    private final JLabel status;

    public CsrfForgeTab(MontoyaApi api) {
        this.api = api;

        Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 12);

        requestArea = new JTextArea(SAMPLE_REQUEST);
        requestArea.setFont(mono);
        requestArea.setLineWrap(false);

        outputArea = new JTextArea();
        outputArea.setFont(mono);
        outputArea.setEditable(false);
        outputArea.setLineWrap(false);

        schemeBox = new JComboBox<>(new String[]{"https", "http"});
        autoSubmitBox = new JCheckBox("Auto-submit on load", true);
        status = new JLabel(" ");

        JButton generateButton = new JButton("Generate PoC");
        generateButton.addActionListener(e -> generate());

        JButton copyButton = new JButton("Copy");
        copyButton.addActionListener(e -> copyOutput());

        JButton saveButton = new JButton("Save as .html");
        saveButton.addActionListener(e -> saveOutput());

        JButton sampleButton = new JButton("Load sample");
        sampleButton.addActionListener(e -> {
            requestArea.setText(SAMPLE_REQUEST);
            schemeBox.setSelectedItem("https");
            setStatus("Loaded sample request.");
        });

        // Left: raw request editor.
        JPanel leftPanel = new JPanel(new BorderLayout(0, 4));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 4));
        leftPanel.add(new JLabel("Raw HTTP request"), BorderLayout.NORTH);
        leftPanel.add(new JScrollPane(requestArea), BorderLayout.CENTER);

        JPanel leftControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        leftControls.add(new JLabel("Scheme:"));
        leftControls.add(schemeBox);
        leftControls.add(autoSubmitBox);
        leftControls.add(sampleButton);
        leftControls.add(generateButton);
        leftPanel.add(leftControls, BorderLayout.SOUTH);

        // Right: generated PoC.
        JPanel rightPanel = new JPanel(new BorderLayout(0, 4));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 8));
        rightPanel.add(new JLabel("Generated CSRF PoC (HTML)"), BorderLayout.NORTH);
        rightPanel.add(new JScrollPane(outputArea), BorderLayout.CENTER);

        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        rightControls.add(copyButton);
        rightControls.add(saveButton);
        rightPanel.add(rightControls, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        split.setResizeWeight(0.5);

        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createEmptyBorder(2, 10, 4, 10));
        statusBar.add(status, BorderLayout.WEST);

        root = new JPanel(new BorderLayout());
        root.add(split, BorderLayout.CENTER);
        root.add(statusBar, BorderLayout.SOUTH);

        api.userInterface().applyThemeToComponent(root);
    }

    public Component getUiComponent() {
        return root;
    }

    /**
     * Populate the editor from a request chosen via the right-click menu, then
     * generate the PoC. Safe to call from any thread.
     */
    public void loadFromRequest(HttpRequest request) {
        SwingUtilities.invokeLater(() -> {
            requestArea.setText(request.toString());
            boolean secure = request.httpService() != null && request.httpService().secure();
            schemeBox.setSelectedItem(secure ? "https" : "http");
            generate();
        });
    }

    private void generate() {
        String scheme = (String) schemeBox.getSelectedItem();
        boolean autoSubmit = autoSubmitBox.isSelected();
        try {
            PocGenerator.ParsedRequest parsed =
                    PocGenerator.parseRaw(requestArea.getText(), scheme);
            String html = PocGenerator.buildHtml(
                    parsed.url, parsed.method, parsed.params, autoSubmit);
            outputArea.setText(html);
            outputArea.setCaretPosition(0);
            setStatus(parsed.method + " " + parsed.url
                    + "  (" + parsed.params.size() + " parameter(s))");
        } catch (IllegalArgumentException ex) {
            outputArea.setText("");
            setStatus("Parse error: " + ex.getMessage());
        }
    }

    private void copyOutput() {
        String html = outputArea.getText();
        if (html.isEmpty()) {
            setStatus("Nothing to copy - generate a PoC first.");
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(html), null);
        setStatus("Copied PoC to clipboard.");
    }

    private void saveOutput() {
        String html = outputArea.getText();
        if (html.isEmpty()) {
            setStatus("Nothing to save - generate a PoC first.");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save CSRF PoC");
        chooser.setSelectedFile(new File("poc.html"));
        chooser.setFileFilter(new FileNameExtensionFilter("HTML files (*.html)", "html", "htm"));
        if (chooser.showSaveDialog(root) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File file = chooser.getSelectedFile();
        String name = file.getName().toLowerCase();
        if (!name.endsWith(".html") && !name.endsWith(".htm")) {
            file = new File(file.getParentFile(), file.getName() + ".html");
        }

        try {
            Files.write(file.toPath(), html.getBytes(StandardCharsets.UTF_8));
            setStatus("Saved PoC to " + file.getAbsolutePath());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(root,
                    "Failed to save file:\n" + ex.getMessage(),
                    "CSRF PoC Forge", JOptionPane.ERROR_MESSAGE);
            setStatus("Save failed: " + ex.getMessage());
        }
    }

    private void setStatus(String message) {
        status.setText(message);
        api.logging().logToOutput(message);
    }
}
