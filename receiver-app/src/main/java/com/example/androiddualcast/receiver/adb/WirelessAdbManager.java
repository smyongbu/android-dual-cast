package com.example.androiddualcast.receiver.adb;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.math.BigInteger;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;

/** 持久化同一 ADB 身份，并提供 Android 11+ TLS/SPAKE2 配对。 */
public final class WirelessAdbManager extends AbsAdbConnectionManager {
    private static final String PRIVATE_KEY_FILE = "wireless_adb_private.key";
    private static final String CERTIFICATE_FILE = "wireless_adb_certificate.der";
    private static WirelessAdbManager instance;

    private final PrivateKey privateKey;
    private final Certificate certificate;

    public static synchronized WirelessAdbManager getInstance(Context context)
            throws Exception {
        if (instance == null) {
            instance = new WirelessAdbManager(context.getApplicationContext());
        }
        return instance;
    }

    private WirelessAdbManager(Context context) throws Exception {
        setApi(30);
        setTimeout(10, TimeUnit.SECONDS);
        File keyFile = new File(context.getFilesDir(), PRIVATE_KEY_FILE);
        File certificateFile = new File(context.getFilesDir(), CERTIFICATE_FILE);

        if (keyFile.isFile() && certificateFile.isFile()) {
            privateKey = readPrivateKey(keyFile);
            certificate = readCertificate(certificateFile);
        } else {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048, SecureRandom.getInstance("SHA1PRNG"));
            KeyPair keyPair = generator.generateKeyPair();
            privateKey = keyPair.getPrivate();
            certificate = createCertificate(keyPair.getPublic(), privateKey);
            writeBytes(keyFile, privateKey.getEncoded());
            writeBytes(certificateFile, certificate.getEncoded());
        }
    }

    @Override
    protected PrivateKey getPrivateKey() {
        return privateKey;
    }

    @Override
    protected Certificate getCertificate() {
        return certificate;
    }

    public PrivateKey usbPrivateKey() { return privateKey; }
    public PublicKey usbPublicKey() { return certificate.getPublicKey(); }

    @Override
    protected String getDeviceName() {
        return "安卓双端投屏";
    }

    private static PrivateKey readPrivateKey(File file) throws Exception {
        return KeyFactory.getInstance("RSA").generatePrivate(
                new PKCS8EncodedKeySpec(readBytes(file)));
    }

    private static Certificate readCertificate(File file) throws Exception {
        try (InputStream input = new FileInputStream(file)) {
            return CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
    }

    private static Certificate createCertificate(PublicKey publicKey, PrivateKey privateKey)
            throws Exception {
        String algorithm = "SHA256withRSA";
        Date notBefore = new Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1));
        Date notAfter = new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(3650));
        X500Name name = new X500Name("CN=AndroidDualCast");
        BigInteger serial = new BigInteger(63, new SecureRandom());
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                name, serial, notBefore, notAfter, name, publicKey);
        ContentSigner signer = new JcaContentSignerBuilder(algorithm).build(privateKey);
        X509CertificateHolder holder = builder.build(signer);
        return new JcaX509CertificateConverter().getCertificate(holder);
    }

    private static byte[] readBytes(File file) throws Exception {
        byte[] bytes = new byte[(int) file.length()];
        try (InputStream input = new FileInputStream(file)) {
            int offset = 0;
            while (offset < bytes.length) {
                int read = input.read(bytes, offset, bytes.length - offset);
                if (read < 0) throw new java.io.IOException("密钥文件读取不完整");
                offset += read;
            }
        }
        return bytes;
    }

    private static void writeBytes(File file, byte[] bytes) throws Exception {
        try (OutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
        }
    }
}
