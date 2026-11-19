package com.nepalgo.catalog.image;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;

import static org.assertj.core.api.Assertions.*;

/** SSRF guard + file-type sniffing for "add photo by URL" / upload. No network needed. */
class ImageSafetyTest {

    private static boolean pub(String ip) throws Exception {
        return SafeImageFetcher.isPublic(InetAddress.getByName(ip));
    }

    @Test
    void internalAddressesAreRefused() throws Exception {
        for (String ip : new String[]{"127.0.0.1", "10.1.2.3", "172.16.0.5", "192.168.88.187", "169.254.169.254",
                "0.0.0.0", "100.64.0.1", "224.0.0.1", "255.255.255.255", "::1", "fd00::1", "fe80::1"}) {
            assertThat(pub(ip)).as(ip).isFalse();
        }
    }

    @Test
    void publicAddressesAreAllowed() throws Exception {
        assertThat(pub("8.8.8.8")).isTrue();
        assertThat(pub("151.101.1.140")).isTrue();
        assertThat(pub("2a00:1450:4001:80b::200e")).isTrue();
    }

    @Test
    void onlyPlainHttpUrlsOnStandardPorts() {
        assertThat(SafeImageFetcher.parse("https://example.com/a.jpg")).isEqualTo(URI.create("https://example.com/a.jpg"));
        for (String bad : new String[]{"file:///etc/passwd", "ftp://example.com/a.jpg", "gopher://x/", "javascript:alert(1)",
                "https://user:pw@example.com/a.jpg", "http://example.com:8081/actuator", "not a url", "", "https:///nohost"}) {
            assertThatThrownBy(() -> SafeImageFetcher.parse(bad)).as(bad).isInstanceOf(InvalidImageException.class);
        }
    }

    @Test
    void hostnameResolvingToLoopbackIsRefused() {
        assertThatThrownBy(() -> SafeImageFetcher.checkPublic(URI.create("http://localhost/x.jpg")))
                .isInstanceOf(InvalidImageException.class).hasMessageContaining("not allowed");
    }

    @Test
    void sniffsRealFileTypeNotTheName() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F'};
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        byte[] html = "<html><body>".getBytes();
        byte[] gif = "GIF89a..".getBytes();
        assertThat(ImageUploadService.isJpegOrPng(jpeg)).isTrue();
        assertThat(ImageUploadService.isJpegOrPng(png)).isTrue();
        assertThat(ImageUploadService.isJpegOrPng(html)).isFalse();
        assertThat(ImageUploadService.isJpegOrPng(gif)).isFalse();
        assertThat(ImageUploadService.isJpegOrPng(new byte[3])).isFalse();
    }
}
