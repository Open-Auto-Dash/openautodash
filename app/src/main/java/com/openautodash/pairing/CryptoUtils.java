package com.openautodash.pairing;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.UUID;

import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class CryptoUtils {
    private CryptoUtils() {}

    public static KeyPair generateEcKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        return generator.generateKeyPair();
    }

    public static String encodePublicKey(PublicKey key) {
        return b64(key.getEncoded());
    }

    public static PublicKey decodePublicKey(String encoded) throws Exception {
        byte[] bytes = b64d(encoded);
        return KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(bytes));
    }

    public static String encodePrivateKey(PrivateKey key) {
        return b64(key.getEncoded());
    }

    public static PrivateKey decodePrivateKey(String encoded) throws Exception {
        byte[] bytes = b64d(encoded);
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(bytes));
    }

    public static byte[] sharedSecret(KeyPair ours, String peerPublic) throws Exception {
        PublicKey peer = decodePublicKey(peerPublic);
        KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH");
        keyAgreement.init(ours.getPrivate());
        keyAgreement.doPhase(peer, true);
        return keyAgreement.generateSecret();
    }

    public static byte[] hkdf(byte[] ikm, String salt, String info, int len) throws Exception {
        byte[] saltBytes = salt == null ? new byte[32] : sha256(salt.getBytes(StandardCharsets.UTF_8));
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(saltBytes, "HmacSHA256"));
        byte[] prk = hmac.doFinal(ikm);

        byte[] okm = new byte[len];
        byte[] t = new byte[0];
        int pos = 0;
        int counter = 1;
        while (pos < len) {
            hmac.init(new SecretKeySpec(prk, "HmacSHA256"));
            hmac.update(t);
            hmac.update(info.getBytes(StandardCharsets.UTF_8));
            hmac.update((byte) counter);
            t = hmac.doFinal();
            int copy = Math.min(t.length, len - pos);
            System.arraycopy(t, 0, okm, pos, copy);
            pos += copy;
            counter++;
        }
        return okm;
    }

    public static byte[] sha256(byte[] input) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(input);
    }

    public static String hmacSha256B64(String secretB64, String message) throws Exception {
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(b64d(secretB64), "HmacSHA256"));
        return b64(hmac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    }

    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    public static String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String b64(byte[] data) {
        return Base64.encodeToString(data, Base64.NO_WRAP);
    }

    public static byte[] b64d(String data) {
        return Base64.decode(data, Base64.NO_WRAP);
    }
}
