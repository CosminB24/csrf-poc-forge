package csrfforge;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure CSRF Proof-of-Concept generation logic, with no dependency on the Burp
 * Montoya API so it can be unit-tested and reused independently.
 *
 * <p>This mirrors the behaviour of the {@code csrf_forge} Python CLI:
 * <ul>
 *     <li>The first line of a raw request is {@code METHOD PATH HTTP/VERSION}.</li>
 *     <li>Header lines up to the first blank line are parsed as {@code Name: value}.</li>
 *     <li>The body is parsed as {@code application/x-www-form-urlencoded} pairs.</li>
 *     <li>The target URL is built as {@code scheme://Host + path}.</li>
 * </ul>
 */
public final class PocGenerator {

    private PocGenerator() {
    }

    /** A single form parameter (decoded name/value pair). */
    public static final class Param {
        public final String name;
        public final String value;

        public Param(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }

    /** The interesting pieces of a parsed HTTP request. */
    public static final class ParsedRequest {
        public String method;
        public String path;
        public String host;
        public String contentType;
        public String url;
        public String body;
        public List<Param> params = new ArrayList<>();
    }

    /**
     * Parse a raw HTTP request (request line + headers + body) into the fields
     * needed to build a PoC. Used by the "paste a raw request" tab path.
     *
     * @throws IllegalArgumentException if the request is empty, the request line
     *                                  is malformed, or the Host header is missing.
     */
    public static ParsedRequest parseRaw(String rawRequest, String scheme) {
        if (rawRequest == null) {
            throw new IllegalArgumentException("request is empty");
        }

        String[] lines = rawRequest.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1);
        if (lines.length == 0 || lines[0].trim().isEmpty()) {
            throw new IllegalArgumentException("request is empty");
        }

        String requestLine = lines[0].trim();
        String[] parts = requestLine.split("\\s+");
        if (parts.length != 3) {
            throw new IllegalArgumentException("malformed request line: " + requestLine);
        }
        String method = parts[0];
        String path = parts[1];

        int blank = -1;
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isEmpty()) {
                blank = i;
                break;
            }
        }
        int headerEnd = (blank == -1) ? lines.length : blank;

        String host = null;
        String contentType = null;
        for (int i = 1; i < headerEnd; i++) {
            String line = lines[i];
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String name = line.substring(0, colon).trim().toLowerCase();
            String value = line.substring(colon + 1).trim();
            if (name.equals("host")) {
                host = value;
            } else if (name.equals("content-type")) {
                contentType = value;
            }
        }
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("missing Host header");
        }

        StringBuilder bodyBuilder = new StringBuilder();
        if (blank != -1) {
            for (int i = blank + 1; i < lines.length; i++) {
                if (bodyBuilder.length() > 0) {
                    bodyBuilder.append("\n");
                }
                bodyBuilder.append(lines[i]);
            }
        }
        String body = bodyBuilder.toString();

        ParsedRequest parsed = new ParsedRequest();
        parsed.method = method;
        parsed.path = path;
        parsed.host = host;
        parsed.contentType = contentType;
        parsed.url = scheme + "://" + host + path;
        parsed.body = body;
        parsed.params = parseFormParams(body);
        return parsed;
    }

    /**
     * Parse an {@code application/x-www-form-urlencoded} body into decoded
     * name/value pairs. Mirrors Python's {@code urllib.parse.parse_qsl}:
     * {@code +} becomes a space and {@code %xx} escapes are decoded.
     */
    public static List<Param> parseFormParams(String body) {
        List<Param> params = new ArrayList<>();
        if (body == null) {
            return params;
        }
        String trimmed = body.trim();
        if (trimmed.isEmpty()) {
            return params;
        }
        for (String pair : trimmed.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String rawName = eq >= 0 ? pair.substring(0, eq) : pair;
            String rawValue = eq >= 0 ? pair.substring(eq + 1) : "";
            params.add(new Param(urlDecode(rawName), urlDecode(rawValue)));
        }
        return params;
    }

    private static String urlDecode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            return value; // UTF-8 is always available
        } catch (IllegalArgumentException e) {
            return value; // malformed % escape: leave the raw text untouched
        }
    }

    /**
     * Build a self-contained HTML CSRF PoC page.
     *
     * @param url        form action (full target URL, including any query string)
     * @param method     HTTP method for the form
     * @param params     hidden form inputs
     * @param autoSubmit when true, the form auto-submits on page load
     */
    public static String buildHtml(String url, String method, List<Param> params, boolean autoSubmit) {
        StringBuilder inputs = new StringBuilder();
        for (Param param : params) {
            inputs.append("        <input type=\"hidden\" name=\"")
                    .append(escapeAttr(param.name))
                    .append("\" value=\"")
                    .append(escapeAttr(param.value))
                    .append("\">\n");
        }

        String script = autoSubmit
                ? "    <script>document.forms[0].submit();</script>\n"
                : "";

        return "<!DOCTYPE html>\n"
                + "<html lang=\"en\">\n"
                + "<head>\n"
                + "    <meta charset=\"UTF-8\">\n"
                + "    <title>CSRF PoC</title>\n"
                + "</head>\n"
                + "<body>\n"
                + "    <form action=\"" + escapeAttr(url) + "\" method=\"" + escapeAttr(method) + "\">\n"
                + inputs
                + "        <input type=\"submit\" value=\"Submit\">\n"
                + "    </form>\n"
                + script
                + "</body>\n"
                + "</html>\n";
    }

    /** Escape a string for safe inclusion inside an HTML attribute value. */
    private static String escapeAttr(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
