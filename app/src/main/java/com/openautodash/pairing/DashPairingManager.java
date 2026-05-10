package com.openautodash.pairing;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

public class DashPairingManager {
    private static final String PREFS = "dash_pairing";
    private static final String KEY_DASH_ID = "dash_id";
    private static final String KEY_PHONES = "paired_phones";
    private static final String KEY_ACTIVE_PAIRING = "active_pairing";

    private final SharedPreferences prefs;
    private ActivePairing activePairing;

    public DashPairingManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized String createPairingQrPayload() throws Exception {
        String dashId = getDashId();
        String pairingId = CryptoUtils.randomId();
        String dashNonce = CryptoUtils.b64(CryptoUtils.sha256((pairingId + System.currentTimeMillis()).getBytes()));
        KeyPair pairKey = CryptoUtils.generateEcKeyPair();
        long expiresAt = System.currentTimeMillis() + 120_000L;
        activePairing = new ActivePairing(pairingId, dashNonce, pairKey, expiresAt);
        persistActivePairing(activePairing);

        JSONObject payload = new JSONObject();
        payload.put("v", 1);
        payload.put("dashId", dashId);
        payload.put("dashName", "OpenAutoDash");
        payload.put("pairingId", pairingId);
        payload.put("dashNonce", dashNonce);
        payload.put("dashPublicKey", CryptoUtils.encodePublicKey(pairKey.getPublic()));
        payload.put("expiresAt", expiresAt);
        return payload.toString();
    }

    public synchronized boolean finalizePairingFromPhoneHello(String phoneHelloJson) {
        try {
            if (activePairing == null) {
                activePairing = loadActivePairing();
            }
            if (activePairing == null || System.currentTimeMillis() > activePairing.expiresAt) return false;
            JSONObject hello = new JSONObject(phoneHelloJson);
            if (!activePairing.pairingId.equals(hello.optString("pairingId"))) return false;

            String phoneId = hello.optString("phoneId");
            String phoneName = hello.optString("displayName", "Phone");
            String phonePublicKey = hello.optString("phonePublicKey");
            if (phoneId.isEmpty() || phonePublicKey.isEmpty()) return false;

            byte[] shared = CryptoUtils.sharedSecret(activePairing.keyPair, phonePublicKey);
            byte[] root = CryptoUtils.hkdf(shared, activePairing.pairingId, "oad-pair-root", 32);

            upsertPairedPhone(phoneId, phoneName, CryptoUtils.b64(root));
            activePairing = null;
            clearActivePairing();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public synchronized List<String> listPairedPhones() {
        List<String> list = new ArrayList<>();
        for (PairedPhone phone : listPairedPhoneRecords()) {
            list.add(phone.displayName + " (" + phone.phoneId + ")");
        }
        return list;
    }

    public synchronized List<PairedPhone> listPairedPhoneRecords() {
        List<PairedPhone> list = new ArrayList<>();
        try {
            JSONArray phones = new JSONArray(prefs.getString(KEY_PHONES, "[]"));
            for (int i = 0; i < phones.length(); i++) {
                JSONObject item = phones.getJSONObject(i);
                list.add(new PairedPhone(
                        item.optString("phoneId", "unknown"),
                        item.optString("displayName", "Phone"),
                        item.optLong("pairedAt", 0)
                ));
            }
        } catch (Exception ignored) {}
        return list;
    }

    public synchronized void clearPhone(String phoneId) {
        try {
            JSONArray phones = new JSONArray(prefs.getString(KEY_PHONES, "[]"));
            JSONArray out = new JSONArray();
            for (int i = 0; i < phones.length(); i++) {
                JSONObject item = phones.getJSONObject(i);
                if (!phoneId.equals(item.optString("phoneId"))) out.put(item);
            }
            prefs.edit().putString(KEY_PHONES, out.toString()).apply();
        } catch (Exception ignored) {}
    }

    public synchronized boolean verifyAuthResponse(String phoneId, String challenge, String mac) {
        if (phoneId == null || phoneId.isEmpty() || challenge == null || challenge.isEmpty() || mac == null || mac.isEmpty()) {
            return false;
        }
        try {
            JSONArray phones = new JSONArray(prefs.getString(KEY_PHONES, "[]"));
            for (int i = 0; i < phones.length(); i++) {
                JSONObject item = phones.getJSONObject(i);
                if (!phoneId.equals(item.optString("phoneId"))) continue;
                String rootSecret = item.optString("rootSecret");
                if (rootSecret.isEmpty()) return false;
                String expected = CryptoUtils.hmacSha256B64(rootSecret, "AUTH:" + challenge);
                return CryptoUtils.constantTimeEquals(expected, mac);
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void upsertPairedPhone(String phoneId, String displayName, String rootSecret) throws Exception {
        JSONArray phones = new JSONArray(prefs.getString(KEY_PHONES, "[]"));
        JSONArray out = new JSONArray();
        boolean replaced = false;
        for (int i = 0; i < phones.length(); i++) {
            JSONObject item = phones.getJSONObject(i);
            if (phoneId.equals(item.optString("phoneId"))) {
                item.put("displayName", displayName);
                item.put("rootSecret", rootSecret);
                item.put("pairedAt", System.currentTimeMillis());
                out.put(item);
                replaced = true;
            } else {
                out.put(item);
            }
        }
        if (!replaced) {
            JSONObject item = new JSONObject();
            item.put("phoneId", phoneId);
            item.put("displayName", displayName);
            item.put("rootSecret", rootSecret);
            item.put("pairedAt", System.currentTimeMillis());
            out.put(item);
        }
        prefs.edit().putString(KEY_PHONES, out.toString()).apply();
    }

    private String getDashId() {
        String id = prefs.getString(KEY_DASH_ID, null);
        if (id == null) {
            id = CryptoUtils.randomId();
            prefs.edit().putString(KEY_DASH_ID, id).apply();
        }
        return id;
    }

    private void persistActivePairing(ActivePairing pairing) throws Exception {
        JSONObject obj = new JSONObject();
        obj.put("pairingId", pairing.pairingId);
        obj.put("dashNonce", pairing.dashNonce);
        obj.put("privateKey", CryptoUtils.encodePrivateKey(pairing.keyPair.getPrivate()));
        obj.put("publicKey", CryptoUtils.encodePublicKey(pairing.keyPair.getPublic()));
        obj.put("expiresAt", pairing.expiresAt);
        prefs.edit().putString(KEY_ACTIVE_PAIRING, obj.toString()).apply();
    }

    private ActivePairing loadActivePairing() {
        try {
            String json = prefs.getString(KEY_ACTIVE_PAIRING, null);
            if (json == null || json.isEmpty()) return null;
            JSONObject obj = new JSONObject(json);
            String pairingId = obj.optString("pairingId");
            String dashNonce = obj.optString("dashNonce");
            String privateKey = obj.optString("privateKey");
            String publicKey = obj.optString("publicKey");
            long expiresAt = obj.optLong("expiresAt", 0L);
            if (pairingId.isEmpty() || dashNonce.isEmpty() || privateKey.isEmpty() || publicKey.isEmpty()) return null;
            if (System.currentTimeMillis() > expiresAt) {
                clearActivePairing();
                return null;
            }
            KeyPair keyPair = new KeyPair(CryptoUtils.decodePublicKey(publicKey), CryptoUtils.decodePrivateKey(privateKey));
            return new ActivePairing(pairingId, dashNonce, keyPair, expiresAt);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void clearActivePairing() {
        prefs.edit().remove(KEY_ACTIVE_PAIRING).apply();
    }

    public static class PairedPhone {
        public final String phoneId;
        public final String displayName;
        public final long pairedAt;

        public PairedPhone(String phoneId, String displayName, long pairedAt) {
            this.phoneId = phoneId;
            this.displayName = displayName;
            this.pairedAt = pairedAt;
        }
    }

    private static class ActivePairing {
        final String pairingId;
        final String dashNonce;
        final KeyPair keyPair;
        final long expiresAt;

        ActivePairing(String pairingId, String dashNonce, KeyPair keyPair, long expiresAt) {
            this.pairingId = pairingId;
            this.dashNonce = dashNonce;
            this.keyPair = keyPair;
            this.expiresAt = expiresAt;
        }
    }
}
