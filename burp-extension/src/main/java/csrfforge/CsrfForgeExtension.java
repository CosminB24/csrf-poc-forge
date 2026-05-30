package csrfforge;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

/**
 * Entry point for the "CSRF PoC Forge" Burp Suite extension (Montoya API).
 *
 * <p>Registers:
 * <ul>
 *     <li>A "CSRF PoC Forge" suite tab for pasting/editing a raw request and
 *         viewing, copying or saving the generated PoC.</li>
 *     <li>A "Generate CSRF PoC" right-click context menu item available wherever
 *         a request is selected (Proxy, Repeater, Target, Logger, ...).</li>
 * </ul>
 */
public class CsrfForgeExtension implements BurpExtension {

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("CSRF PoC Forge");

        CsrfForgeTab tab = new CsrfForgeTab(api);
        api.userInterface().registerSuiteTab("CSRF PoC Forge", tab.getUiComponent());
        api.userInterface().registerContextMenuItemsProvider(new CsrfContextMenu(tab));

        api.logging().logToOutput(
                "CSRF PoC Forge loaded. Right-click a request and choose "
                        + "'Generate CSRF PoC', or use the 'CSRF PoC Forge' tab.");
    }
}
