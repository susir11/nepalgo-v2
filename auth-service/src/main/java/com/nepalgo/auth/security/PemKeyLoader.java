package com.nepalgo.auth.security;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

// Reads the PEM files GenKeys.java produced and turns them back into Java
// key objects. This is the inverse of GenKeys' toPem() — strip the
// BEGIN/END headers, decode the base64 body, hand the raw bytes to the
// standard RSA KeyFactory.
public final class PemKeyLoader {

    private PemKeyLoader() {}

    public static RSAPrivateKey loadPrivateKey(String path) throws Exception {
        byte[] der = stripPemHeaders(Files.readString(Path.of(path)));
        KeyFactory factory = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    public static RSAPublicKey loadPublicKey(String path) throws Exception {
        byte[] der = stripPemHeaders(Files.readString(Path.of(path)));
        KeyFactory factory = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(der));
    }

    private static byte[] stripPemHeaders(String pem) {
        String cleaned = pem
                .replaceAll("-----BEGIN (.*)-----", "")
                .replaceAll("-----END (.*)-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(cleaned);
    }
}