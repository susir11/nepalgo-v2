package com.nepalgo.catalog.image;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Downloads an image from a URL an admin pasted ("add photo by URL").
 *
 * The server fetching a user-supplied URL is the classic SSRF hole: someone could
 * point it at http://localhost:8081/actuator, the cloud metadata endpoint
 * (169.254.169.254) or an internal service and read the reply. Defences, like a
 * bank only wiring money to external accounts it has screened:
 *  - only http/https, no credentials in the URL, standard ports only
 *  - every hop's host must resolve to PUBLIC addresses only (no loopback, private,
 *    link-local/metadata, CGNAT, multicast, IPv6 ULA)
 *  - redirects are followed manually (max 3) so each hop is re-checked
 *  - hard limits: 5 s connect, 15 s total, 10 MB body
 * The bytes then go through the same magic-byte check + re-encode as an upload, so
 * what gets stored is always our own clean JPEG, never the remote file.
 * Residual risk: DNS rebinding between our check and the connect. Acceptable here
 * because only authenticated staff can call this endpoint.
 */
@Component
public class SafeImageFetcher {

    static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 3;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public byte[] fetch(String rawUrl) {
        URI uri = parse(rawUrl);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            checkPublic(uri);
            HttpResponse<InputStream> res;
            try {
                res = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                        .header("User-Agent", "NepalGo-ImageImport/1.0")
                        .header("Accept", "image/jpeg,image/png;q=0.9,*/*;q=0.1")
                        .GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InvalidImageException("Download was interrupted.");
            } catch (Exception e) {
                throw new InvalidImageException("Could not download that URL (" + e.getClass().getSimpleName() + ").");
            }
            int code = res.statusCode();
            if (code >= 300 && code < 400) {
                String loc = res.headers().firstValue("Location")
                        .orElseThrow(() -> new InvalidImageException("The URL redirects without a target."));
                closeQuietly(res.body());
                uri = parse(uri.resolve(loc).toString());
                continue;
            }
            if (code != 200) {
                closeQuietly(res.body());
                throw new InvalidImageException("The URL answered HTTP " + code + ", not an image.");
            }
            long declared = res.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (declared > MAX_BYTES) {
                closeQuietly(res.body());
                throw new InvalidImageException("Image is larger than 10 MB.");
            }
            return readLimited(res.body());
        }
        throw new InvalidImageException("Too many redirects.");
    }

    static URI parse(String raw) {
        URI uri;
        try {
            uri = URI.create(raw == null ? "" : raw.trim());
        } catch (IllegalArgumentException e) {
            throw new InvalidImageException("That is not a valid URL.");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new InvalidImageException("Only http:// and https:// URLs are allowed.");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) throw new InvalidImageException("The URL has no host.");
        if (uri.getUserInfo() != null) throw new InvalidImageException("URLs with a username/password are not allowed.");
        int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443) throw new InvalidImageException("Only standard ports (80/443) are allowed.");
        return uri;
    }

    static void checkPublic(URI uri) {
        InetAddress[] all;
        try {
            all = InetAddress.getAllByName(uri.getHost());
        } catch (UnknownHostException e) {
            throw new InvalidImageException("Host not found: " + uri.getHost());
        }
        for (InetAddress a : all) {
            if (!isPublic(a)) throw new InvalidImageException("That address is not allowed (internal network).");
        }
    }

    static boolean isPublic(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress()
                || a.isSiteLocalAddress() || a.isMulticastAddress()) return false;
        byte[] b = a.getAddress();
        if (b.length == 4) {
            int b0 = b[0] & 0xff, b1 = b[1] & 0xff;
            if (b0 == 0) return false;                                  // "this network"
            if (b0 == 100 && b1 >= 64 && b1 <= 127) return false;      // CGNAT 100.64/10
            if (b0 == 198 && (b1 == 18 || b1 == 19)) return false;     // benchmarking
            if (b0 >= 240) return false;                                // reserved / broadcast
        } else if (b.length == 16) {
            if ((b[0] & 0xfe) == 0xfc) return false;                    // IPv6 ULA fc00::/7
        }
        return true;
    }

    private static byte[] readLimited(InputStream in) {
        try (in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            int n, total = 0;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > MAX_BYTES) throw new InvalidImageException("Image is larger than 10 MB.");
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (InvalidImageException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidImageException("Download failed while reading the image.");
        }
    }

    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (Exception ignored) {
        }
    }
}
