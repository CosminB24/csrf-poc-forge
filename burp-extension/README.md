# CSRF PoC Forge — Burp Suite extension

A Burp Suite extension (Java, [Montoya API](https://portswigger.net/burp/documentation/desktop/extensions)) that turns any request into a self-submitting HTML CSRF Proof-of-Concept. Works in Burp Suite Professional **and** Community.

> For authorized security testing only.

## Build

Requires a JDK 17+. The Gradle wrapper downloads everything else.

```sh
./gradlew jar            # Windows: .\gradlew.bat jar
```

Output: `build/libs/csrf-forge.jar`.

## Install

**Extensions → Installed → Add → Extension type: Java →** select `build/libs/csrf-forge.jar`.

## Use

- Right-click a request (Proxy / Repeater / Target / Logger / …) → **Generate CSRF PoC**.
- Or open the **CSRF PoC Forge** tab, paste a raw request (or **Load sample**), choose the scheme, toggle **Auto-submit on load**, and **Generate PoC**.
- **Copy** or **Save as .html**, then open the file in a browser logged in to the target.

## Layout

| File | Role |
|------|------|
| `CsrfForgeExtension.java` | Entry point — registers the tab and context menu |
| `CsrfContextMenu.java` | "Generate CSRF PoC" right-click item |
| `CsrfForgeTab.java` | Suite tab UI (editor, options, copy/save) |
| `PocGenerator.java` | Burp-free parse + HTML build logic |
| `src/main/resources/META-INF/services/burp.api.montoya.BurpExtension` | ServiceLoader entry point Burp uses to discover the extension |
