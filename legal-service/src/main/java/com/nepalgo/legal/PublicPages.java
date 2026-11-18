package com.nepalgo.legal;

import com.nepalgo.legal.content.SiteContent;
import com.nepalgo.legal.content.SiteContentService;
import com.nepalgo.legal.document.LegalDocument;
import com.nepalgo.legal.document.LegalDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.databind.JsonNode;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

// Public web pages the app stores require (no login, no JavaScript):
//   /privacy         - Privacy Policy URL for the App Store / Play listing
//   /terms           - Terms and Conditions
//   /delete-account  - Google Play's "web link to request account deletion"
// Always rendered from the CURRENT PUBLISHED version in the database, so the
// public page can never drift from what customers agree to in the app: publish
// v2 in the admin portal and the page changes with it. Like a bank's published
// fee schedule - one source of truth, shown wherever it's needed.
//
// Security: every piece of text is HTML-escaped (the text is admin-authored,
// but escaping costs nothing and rules out stored XSS), and the strict CSP
// header forbids scripts entirely.
@RestController
@RequiredArgsConstructor
public class PublicPages {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private final LegalDocumentService documents;
    private final SiteContentService content;

    @GetMapping(value = "/privacy", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> privacy() {
        return page(document(LegalDocument.Type.PRIVACY_POLICY));
    }

    @GetMapping(value = "/terms", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> terms() {
        return page(document(LegalDocument.Type.TERMS_AND_CONDITIONS));
    }

    @GetMapping(value = "/delete-account", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> deleteAccount() {
        String email = companyField("supportEmail");
        String body = """
                <h1>Delete your NepalGo account</h1>
                <h2>In the app (fastest)</h2>
                <ol>
                  <li>Open the NepalGo app and sign in.</li>
                  <li>Go to <strong>Profile</strong> and tap <strong>Delete account</strong>.</li>
                  <li>Read the summary, type <strong>DELETE</strong> and confirm.</li>
                </ol>
                <h2>Without the app</h2>
                <p>E-mail <strong>%s</strong> from the address you signed up with and ask us to delete your account.
                We confirm within 30 days.</p>
                <h2>What is deleted</h2>
                <ul>
                  <li>Your login and profile (name, e-mail).</li>
                  <li>Your name, e-mail and passport number on your bookings, and your special requests.</li>
                  <li>Your in-app messages and notification settings.</li>
                  <li>Trip requests that are not paid yet (they are withdrawn) and unused travel credits.</li>
                </ul>
                <h2>What we keep, and why</h2>
                <ul>
                  <li>Invoices and payment records of paid trips, as Nepal's tax law requires.</li>
                  <li>The record that you accepted our Terms for a confirmed booking (legal evidence).</li>
                </ul>
                <p>These records no longer contain your name, e-mail or passport number. Your NepalGo ID is retired
                and never given to anyone else. See the <a href="/privacy">Privacy Policy</a> for details.</p>
                """.formatted(esc(email));
        return html("Delete your NepalGo account", body);
    }

    // ------------------------------------------------------------------ rendering

    private record Doc(String title, String text, String version, String effective, String sha256) {}

    private Doc document(LegalDocument.Type type) {
        var d = documents.current(type);
        return new Doc(d.title(), d.fullText(), d.versionLabel(), DATE.format(d.effectiveFrom()), d.contentSha256());
    }

    private ResponseEntity<String> page(Doc d) {
        String meta = "<p class=\"meta\">Version " + esc(d.version()) + " · effective " + esc(d.effective()) + "</p>";
        String foot = "<p class=\"meta\">Fingerprint (SHA-256): <code>" + esc(d.sha256()) + "</code></p>";
        return html(d.title(), meta + render(d.text()) + foot);
    }

    // The documents are plain text: CAPITALISED or "1. SECTION" lines are headings,
    // "- " lines are bullets, blank lines separate paragraphs.
    static String render(String text) {
        StringBuilder out = new StringBuilder();
        boolean inList = false;
        for (String block : text.replace("\r", "").split("\n\\s*\n")) {
            for (String raw : block.split("\n")) {
                String line = raw.strip();
                if (line.isEmpty()) continue;
                boolean bullet = line.startsWith("- ") || line.startsWith("• ");
                if (!bullet && inList) { out.append("</ul>"); inList = false; }
                if (bullet) {
                    if (!inList) { out.append("<ul>"); inList = true; }
                    out.append("<li>").append(esc(line.substring(2))).append("</li>");
                } else if (INLINE_HEADING.matcher(line).matches()) {
                    // "2. HOW WE USE THIS INFORMATION To create..." -> heading + paragraph
                    var m = INLINE_HEADING.matcher(line);
                    m.matches();
                    out.append("<h2>").append(esc(m.group(1))).append("</h2><p>").append(esc(m.group(2))).append("</p>");
                } else if (isHeading(line)) {
                    String tag = out.isEmpty() ? "h1" : "h2";   // first heading is the page title
                    out.append('<').append(tag).append('>').append(esc(line)).append("</").append(tag).append('>');
                } else {
                    out.append("<p>").append(esc(line)).append("</p>");
                }
            }
            if (inList) { out.append("</ul>"); inList = false; }
        }
        return out.toString();
    }

    // Some stored texts put a numbered CAPITAL heading and its first sentence on one line.
    private static final java.util.regex.Pattern INLINE_HEADING =
            java.util.regex.Pattern.compile("^(\\d+\\.\\s+[A-Z][A-Z0-9 ,&'/()-]*?[A-Z)]\\.?)\\s+([A-Z][a-z].*)$");

    static boolean isHeading(String line) {
        String letters = line.replaceAll("[^A-Za-z]", "");
        return line.length() <= 90 && letters.length() >= 3 && letters.equals(letters.toUpperCase(Locale.ROOT));
    }

    private String companyField(String field) {
        try {
            JsonNode n = content.current(SiteContent.Key.COMPANY_PROFILE).payload().get(field);
            return n == null || n.isNull() ? "our support team" : n.asString();
        } catch (RuntimeException e) {
            return "our support team";
        }
    }

    private static String esc(String s) {
        // UTF-8 mode: escapes only < > & " ' (all that matters for safety), keeps — and é readable.
        return s == null ? "" : HtmlUtils.htmlEscape(s, "UTF-8");
    }

    private static ResponseEntity<String> html(String title, String body) {
        String doc = """
                <!doctype html>
                <html lang="en">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s</title>
                <style>
                  :root { --bg:#f7f5f0; --card:#ffffff; --text:#1d2421; --muted:#5d6762; --brand:#2f6b4f; --line:#e3e0d8; }
                  @media (prefers-color-scheme: dark) {
                    :root { --bg:#111614; --card:#1a201d; --text:#e9ece9; --muted:#9aa49f; --brand:#7cc29c; --line:#2a322e; }
                  }
                  * { box-sizing: border-box; }
                  body { margin:0; background:var(--bg); color:var(--text);
                         font: 16px/1.65 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
                  header { padding: 18px 16px; border-bottom: 1px solid var(--line); }
                  header a { color: var(--brand); font-weight: 700; text-decoration: none; font-size: 18px; }
                  main { max-width: 760px; margin: 24px auto; padding: 28px 20px; background: var(--card);
                         border: 1px solid var(--line); border-radius: 20px; }
                  h1 { font-size: 26px; line-height: 1.25; margin: 0 0 8px; }
                  h2 { font-size: 18px; margin: 28px 0 8px; color: var(--brand); }
                  p, li { overflow-wrap: anywhere; }
                  ul, ol { padding-left: 22px; }
                  a { color: var(--brand); }
                  .meta { color: var(--muted); font-size: 14px; }
                  code { font-size: 12px; }
                  footer { text-align: center; color: var(--muted); font-size: 14px; padding: 8px 16px 32px; }
                  @media (max-width: 600px) { main { margin: 0; border-radius: 0; border-left: 0; border-right: 0; } }
                </style>
                </head>
                <body>
                <header><a href="/privacy">NepalGo</a></header>
                <main>
                %s
                </main>
                <footer><a href="/privacy">Privacy Policy</a> · <a href="/terms">Terms</a> · <a href="/delete-account">Delete account</a></footer>
                </body>
                </html>
                """.formatted(esc(title), body);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; img-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'")
                .header("X-Content-Type-Options", "nosniff")
                .header("Referrer-Policy", "no-referrer")
                .contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8))
                .body(doc);
    }
}
