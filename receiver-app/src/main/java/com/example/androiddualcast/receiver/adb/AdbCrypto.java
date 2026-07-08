package com.example.androiddualcast.receiver.adb;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

final class AdbCrypto {
    private static final String PREFS = "adb_crypto";
    private static final int ANDROID_PUBKEY_MODULUS_SIZE = 256;
    private static final int ANDROID_PUBKEY_MODULUS_WORDS = ANDROID_PUBKEY_MODULUS_SIZE / 4;

    private final KeyPair keyPair;

    private AdbCrypto(KeyPair keyPair) {
        this.keyPair = keyPair;
    }

    static AdbCrypto loadOrCreate(Context context) throws GeneralSecurityException {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String privateKey = prefs.getString("private", null);
        String publicKey = prefs.getString("public", null);
        if (privateKey != null && publicKey != null) {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            PrivateKey priv = factory.generatePrivate(new PKCS8EncodedKeySpec(
                    Base64.decode(privateKey, Base64.NO_WRAP)));
            PublicKey pub = factory.generatePublic(new X509EncodedKeySpec(
                    Base64.decode(publicKey, Base64.NO_WRAP)));
            return new AdbCrypto(new KeyPair(pub, priv));
        }

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        prefs.edit()
                .putString("private", Base64.encodeToString(pair.getPrivate().getEncoded(), Base64.NO_WRAP))
                .putString("public", Base64.encodeToString(pair.getPublic().getEncoded(), Base64.NO_WRAP))
                .apply();
        return new AdbCrypto(pair);
    }

    byte[] sign(byte[] token) throws GeneralSecurityException {
        Signature signature = Signature.getInstance("SHA1withRSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(token);
        return signature.sign();
    }

    byte[] adbPublicKeyPayload() {
        RSAPublicKey rsa = (RSAPublicKey) keyPair.getPublic();
        byte[] binaryKey = androidPubkey(rsa);
        String key = Base64.encodeToString(binaryKey, Base64.NO_WRAP) + " android-dual-cast@receiver\0";
        return key.getBytes();
    }

    private static byte[] androidPubkey(RSAPublicKey key) {
        BigInteger modulus = key.getModulus();
        BigInteger rr = BigInteger.ONE.shiftLeft(ANDROID_PUBKEY_MODULUS_SIZE * 8 * 2).mod(modulus);
        ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + ANDROID_PUBKEY_MODULUS_SIZE
                + ANDROID_PUBKEY_MODULUS_SIZE + 4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(ANDROID_PUBKEY_MODULUS_WORDS);
        buffer.putInt(n0inv(modulus));
        putLittleEndianFixed(buffer, modulus, ANDROID_PUBKEY_MODULUS_SIZE);
        putLittleEndianFixed(buffer, rr, ANDROID_PUBKEY_MODULUS_SIZE);
        buffer.putInt(key.getPublicExponent().intValue());
        return buffer.array();
    }

    private static int n0inv(BigInteger modulus) {
        BigInteger base = BigInteger.ONE.shiftLeft(32);
        BigInteger word = modulus.mod(base);
        return word.modInverse(base).negate().intValue();
    }

    private static void putLittleEndianFixed(ByteBuffer buffer, BigInteger value, int size) {
        byte[] source = value.toByteArray();
        byte[] bigEndian = new byte[size];
        int copyLength = Math.min(source.length, size);
        System.arraycopy(source, source.length - copyLength, bigEndian, size - copyLength, copyLength);
        for (int i = size - 1; i >= 0; i--) {
            buffer.put(bigEndian[i]);
        }
    }
}

