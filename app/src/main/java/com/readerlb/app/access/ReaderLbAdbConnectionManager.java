// SPDX-License-Identifier: GPL-3.0-or-later
package com.readerlb.app.access;

import android.content.Context;
import android.os.Build;
import android.sun.misc.BASE64Encoder;
import android.sun.security.provider.X509Factory;
import android.sun.security.x509.AlgorithmId;
import android.sun.security.x509.CertificateAlgorithmId;
import android.sun.security.x509.CertificateExtensions;
import android.sun.security.x509.CertificateIssuerName;
import android.sun.security.x509.CertificateSerialNumber;
import android.sun.security.x509.CertificateSubjectName;
import android.sun.security.x509.CertificateValidity;
import android.sun.security.x509.CertificateVersion;
import android.sun.security.x509.CertificateX509Key;
import android.sun.security.x509.KeyIdentifier;
import android.sun.security.x509.PrivateKeyUsageExtension;
import android.sun.security.x509.SubjectKeyIdentifierExtension;
import android.sun.security.x509.X500Name;
import android.sun.security.x509.X509CertImpl;
import android.sun.security.x509.X509CertInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;

/**
 * Persistent ADB identity used by ReaderLB's built-in Wireless Debugging flow.
 *
 * The implementation follows libadb-android's reference sample, but keeps the
 * key material under ReaderLB's private app storage and uses a long-lived
 * certificate so the user does not need to pair again merely because a local
 * certificate expired.
 */
public final class ReaderLbAdbConnectionManager extends AbsAdbConnectionManager {
    private static ReaderLbAdbConnectionManager instance;

    private final PrivateKey privateKey;
    private final Certificate certificate;

    public static synchronized ReaderLbAdbConnectionManager getInstance(
            @NonNull Context context
    ) throws Exception {
        if (instance == null) {
            instance = new ReaderLbAdbConnectionManager(
                    context.getApplicationContext()
            );
        }
        return instance;
    }

    private ReaderLbAdbConnectionManager(@NonNull Context context)
            throws Exception {
        setApi(Build.VERSION.SDK_INT);
        setTimeout(15, TimeUnit.SECONDS);
        setThrowOnUnauthorised(true);

        File identityDir = new File(context.getFilesDir(), "adb_identity");
        if (!identityDir.isDirectory() && !identityDir.mkdirs()) {
            throw new IOException("Unable to create ADB identity directory");
        }

        PrivateKey loadedPrivate = readPrivateKey(identityDir);
        Certificate loadedCertificate = readCertificate(identityDir);

        if (loadedPrivate != null && loadedCertificate != null) {
            privateKey = loadedPrivate;
            certificate = loadedCertificate;
            return;
        }

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(
                2048,
                SecureRandom.getInstance("SHA1PRNG")
        );
        KeyPair keyPair = generator.generateKeyPair();
        privateKey = keyPair.getPrivate();
        certificate = createCertificate(keyPair.getPublic(), privateKey);

        writePrivateKey(identityDir, privateKey);
        writeCertificate(identityDir, certificate);
    }

    @NonNull
    @Override
    protected PrivateKey getPrivateKey() {
        return privateKey;
    }

    @NonNull
    @Override
    protected Certificate getCertificate() {
        return certificate;
    }

    @NonNull
    @Override
    protected String getDeviceName() {
        return "ReaderLB";
    }

    private static Certificate createCertificate(
            PublicKey publicKey,
            PrivateKey privateKey
    ) throws Exception {
        final String algorithm = "SHA512withRSA";
        final X500Name subject = new X500Name("CN=ReaderLB");
        final Date notBefore = new Date(
                System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5)
        );
        final Date notAfter = new Date(
                System.currentTimeMillis() + TimeUnit.DAYS.toMillis(3650)
        );

        CertificateExtensions extensions = new CertificateExtensions();
        extensions.set(
                "SubjectKeyIdentifier",
                new SubjectKeyIdentifierExtension(
                        new KeyIdentifier(publicKey).getIdentifier()
                )
        );
        extensions.set(
                "PrivateKeyUsage",
                new PrivateKeyUsageExtension(notBefore, notAfter)
        );

        X509CertInfo info = new X509CertInfo();
        info.set("version", new CertificateVersion(2));
        info.set(
                "serialNumber",
                new CertificateSerialNumber(
                        new Random().nextInt() & Integer.MAX_VALUE
                )
        );
        info.set(
                "algorithmID",
                new CertificateAlgorithmId(
                        AlgorithmId.get(algorithm)
                )
        );
        info.set("subject", new CertificateSubjectName(subject));
        info.set("key", new CertificateX509Key(publicKey));
        info.set(
                "validity",
                new CertificateValidity(notBefore, notAfter)
        );
        info.set("issuer", new CertificateIssuerName(subject));
        info.set("extensions", extensions);

        X509CertImpl cert = new X509CertImpl(info);
        cert.sign(privateKey, algorithm);
        return cert;
    }

    @Nullable
    private static PrivateKey readPrivateKey(File directory)
            throws IOException, NoSuchAlgorithmException,
            InvalidKeySpecException {
        File file = new File(directory, "private.key");
        if (!file.isFile()) return null;

        byte[] bytes = new byte[(int) file.length()];
        try (InputStream input = new FileInputStream(file)) {
            int offset = 0;
            while (offset < bytes.length) {
                int read = input.read(bytes, offset, bytes.length - offset);
                if (read < 0) break;
                offset += read;
            }
            if (offset != bytes.length) {
                throw new IOException("Incomplete ADB private key");
            }
        }

        EncodedKeySpec spec = new PKCS8EncodedKeySpec(bytes);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    @Nullable
    private static Certificate readCertificate(File directory)
            throws IOException, CertificateException {
        File file = new File(directory, "certificate.pem");
        if (!file.isFile()) return null;
        try (InputStream input = new FileInputStream(file)) {
            return CertificateFactory
                    .getInstance("X.509")
                    .generateCertificate(input);
        }
    }

    private static void writePrivateKey(
            File directory,
            PrivateKey key
    ) throws IOException {
        try (OutputStream output = new FileOutputStream(
                new File(directory, "private.key")
        )) {
            output.write(key.getEncoded());
        }
    }

    private static void writeCertificate(
            File directory,
            Certificate certificate
    ) throws IOException, CertificateEncodingException {
        BASE64Encoder encoder = new BASE64Encoder();
        try (OutputStream output = new FileOutputStream(
                new File(directory, "certificate.pem")
        )) {
            output.write(
                    X509Factory.BEGIN_CERT.getBytes(
                            StandardCharsets.UTF_8
                    )
            );
            output.write('\n');
            encoder.encode(certificate.getEncoded(), output);
            output.write('\n');
            output.write(
                    X509Factory.END_CERT.getBytes(
                            StandardCharsets.UTF_8
                    )
            );
        }
    }
}
