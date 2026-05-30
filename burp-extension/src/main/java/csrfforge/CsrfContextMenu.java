package csrfforge;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;

import javax.swing.JMenuItem;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Adds a "Generate CSRF PoC" item to Burp's right-click menu. It is shown
 * whenever exactly one request is in context (a message editor, or a selected
 * row in Proxy history / Target / Logger). Choosing it hands the request to the
 * {@link CsrfForgeTab}, which parses it and renders the PoC.
 */
public class CsrfContextMenu implements ContextMenuItemsProvider {

    private final CsrfForgeTab tab;

    public CsrfContextMenu(CsrfForgeTab tab) {
        this.tab = tab;
    }

    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        Optional<HttpRequest> request = selectedRequest(event);
        if (request.isEmpty()) {
            return null;
        }

        JMenuItem item = new JMenuItem("Generate CSRF PoC");
        item.addActionListener(e -> tab.loadFromRequest(request.get()));

        List<Component> items = new ArrayList<>();
        items.add(item);
        return items;
    }

    /** Resolve the request under the cursor, preferring an open message editor. */
    private Optional<HttpRequest> selectedRequest(ContextMenuEvent event) {
        if (event.messageEditorRequestResponse().isPresent()) {
            HttpRequest request = event.messageEditorRequestResponse().get()
                    .requestResponse().request();
            if (request != null) {
                return Optional.of(request);
            }
        }

        List<HttpRequestResponse> selected = event.selectedRequestResponses();
        if (selected != null && !selected.isEmpty()) {
            HttpRequest request = selected.get(0).request();
            if (request != null) {
                return Optional.of(request);
            }
        }

        return Optional.empty();
    }
}
