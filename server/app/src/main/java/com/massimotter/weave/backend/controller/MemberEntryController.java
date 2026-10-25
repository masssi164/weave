package com.massimotter.weave.backend.controller;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.massimotter.weave.backend.service.PlatformContractService;
import io.swagger.v3.oas.annotations.Hidden;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/** Secret-free browser completion; activation never establishes a native session. */
@RestController
@Hidden
public final class MemberEntryController {
    private final PlatformContractService manifests;

    public MemberEntryController(PlatformContractService manifests) {
        this.manifests = manifests;
    }

    @GetMapping(value = {"/", "/join"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> entry(Locale locale) throws WriterException {
        // Use the canonical producer; request parameters and Host never select an authority.
        String origin = manifests.config().organizationOrigin();
        String link = "weave://join?organization_origin="
                + URLEncoder.encode(origin, StandardCharsets.UTF_8);
        boolean german = "de".equals(locale.getLanguage());
        String title = german ? "Mit Weave verbinden" : "Connect to Weave";
        String explanation = german
                ? "Öffnen Sie Weave und melden Sie sich im Systembrowser an. Die Einrichtung Ihres Kontos meldet Sie nicht automatisch in der App an."
                : "Open Weave and sign in through your system browser. Account setup does not automatically sign you into the app.";
        String open = german ? "Weave öffnen" : "Open Weave";
        String copy = german ? "Installationsadresse zum Kopieren" : "Installation address to copy";
        String qrLabel = german ? "QR-Code zum Öffnen von Weave" : "QR code to open Weave";
        String html = """
                <!doctype html><html lang="%s"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>%s</title><style>body{font:1.1rem system-ui;max-width:38rem;margin:3rem auto;padding:1rem}
                a,input{display:block;margin:1rem 0;padding:.8rem;font:inherit}input{width:90%%}
                svg{max-width:18rem;width:100%%}a:focus-visible,input:focus-visible{outline:3px solid #2456a6}</style>
                </head><body><main><h1>%s</h1><p>%s</p><a id="open-weave" href="%s">%s</a>
                <label for="organization-origin">%s</label>
                <input id="organization-origin" readonly value="%s">%s</main></body></html>
                """.formatted(german ? "de" : "en", title, title, explanation,
                        HtmlUtils.htmlEscape(link), open, copy, HtmlUtils.htmlEscape(origin), qr(link, qrLabel));
        return ResponseEntity.ok().contentType(new MediaType("text", "html", StandardCharsets.UTF_8)).cacheControl(CacheControl.noStore())
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'")
                .header("Referrer-Policy", "no-referrer").body(html);
    }

    private String qr(String link, String label) throws WriterException {
        BitMatrix matrix = new QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, 256, 256);
        StringBuilder path = new StringBuilder();
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                if (matrix.get(x, y)) {
                    path.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
                }
            }
        }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" role=\"img\" aria-label=\""
                + HtmlUtils.htmlEscape(label) + "\" viewBox=\"0 0 256 256\"><rect width=\"256\" height=\"256\" fill=\"white\"/>"
                + "<path fill=\"black\" d=\"" + path + "\"/></svg>";
    }
}
