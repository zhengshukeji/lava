/*
 * Copyright 2026 整数科技 (zhengshuyun.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zhengshuyun.lava.crypto;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.ECParameterSpec;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PemKeyUtilsTest {

    @Test
    void everySupportedCurveRoundTripsAndSignsWithoutProviderMutation() throws Exception {
        List<String> providersBefore = providerNames();
        for (EcKeyUtils.Curve curve : EcKeyUtils.Curve.values()) {
            KeyPair pair = CryptoUtils.ecGenerateKeyPair(curve);
            String privatePem = CryptoUtils.pemEncode(pair.getPrivate());
            String publicPem = CryptoUtils.pemEncode(pair.getPublic());
            ECPrivateKey restoredPrivate = CryptoUtils.pemReadEcPrivateKey(privatePem);
            var restoredPublic = CryptoUtils.pemReadEcPublicKey(publicPem);

            Signature signer = Signature.getInstance(signatureAlgorithm(curve));
            signer.initSign(restoredPrivate);
            signer.update(new byte[]{1, 2, 3, 4});
            byte[] signature = signer.sign();
            Signature verifier = Signature.getInstance(signatureAlgorithm(curve));
            verifier.initVerify(restoredPublic);
            verifier.update(new byte[]{1, 2, 3, 4});

            assertTrue(verifier.verify(signature));
            assertEquals(((ECPrivateKey) pair.getPrivate()).getS(), restoredPrivate.getS());
        }
        assertEquals(providersBefore, providerNames());
    }

    @Test
    void pemOutputIsCanonicalAndContainsNoExtraMaterial() {
        KeyPair pair = CryptoUtils.ecGenerateKeyPair();
        String pem = CryptoUtils.pemEncode(pair.getPrivate());
        String[] lines = pem.split("\\n");

        assertEquals("-----BEGIN PRIVATE KEY-----", lines[0]);
        assertEquals("-----END PRIVATE KEY-----", lines[lines.length - 1]);
        for (int index = 1; index < lines.length - 2; index++) {
            assertEquals(64, lines[index].length());
        }
        assertTrue(lines[lines.length - 2].length() <= 64);
    }

    @Test
    void strictParserRejectsWrongDuplicateAndEmbeddedBoundaries() {
        KeyPair pair = CryptoUtils.ecGenerateKeyPair();
        String privatePem = CryptoUtils.pemEncode(pair.getPrivate());
        String publicPem = CryptoUtils.pemEncode(pair.getPublic());

        assertThrows(CryptoException.class, () -> CryptoUtils.pemReadEcPrivateKey(publicPem));
        assertThrows(CryptoException.class, () -> CryptoUtils.pemReadEcPublicKey(privatePem));
        assertThrows(CryptoException.class,
                () -> CryptoUtils.pemReadEcPrivateKey(privatePem + privatePem));
        assertThrows(CryptoException.class,
                () -> CryptoUtils.pemReadEcPrivateKey(privatePem.replace(
                        "-----END PRIVATE KEY-----",
                        "-----BEGIN PUBLIC KEY-----\n-----END PRIVATE KEY-----")));
        assertThrows(CryptoException.class,
                () -> CryptoUtils.pemReadEcPrivateKey(
                        "-----BEGIN PRIVATE KEY-----\n!!!!\n-----END PRIVATE KEY-----"));
        assertThrows(CryptoException.class,
                () -> CryptoUtils.pemReadEcPrivateKey(
                        "x".repeat(65_537)));
    }

    @Test
    void rsaKeysRoundTripButAreNotAcceptedAsEcKeys() throws Exception {
        KeyPair rsa = KeyPairGenerator.getInstance("RSA").generateKeyPair();

        String privatePem = CryptoUtils.pemEncode(rsa.getPrivate());
        String publicPem = CryptoUtils.pemEncode(rsa.getPublic());
        assertEquals(rsa.getPrivate(), CryptoUtils.pemReadRsaPrivateKey(privatePem));
        assertEquals(rsa.getPublic(), CryptoUtils.pemReadRsaPublicKey(publicPem));

        assertThrows(CryptoException.class, () -> CryptoUtils.pemReadEcPrivateKey(privatePem));
    }

    @Test
    void rawBase64DerIsAcceptedWithoutPemBoundaries() throws Exception {
        KeyPair rsa = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String rawPrivate = java.util.Base64.getEncoder().encodeToString(rsa.getPrivate().getEncoded());
        String rawPublic = java.util.Base64.getEncoder().encodeToString(rsa.getPublic().getEncoded());

        assertEquals(rsa.getPrivate(), CryptoUtils.pemReadRsaPrivateKey(rawPrivate));
        assertEquals(rsa.getPublic(), CryptoUtils.pemReadRsaPublicKey("  " + rawPublic + "\n"));
        assertThrows(CryptoException.class, () -> CryptoUtils.pemReadRsaPrivateKey(rawPublic));
    }

    @Test
    void rsaPemCanBeReadByExplicitRsaEntrypoints() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        String privatePem = toPrivatePem(pair.getPrivate().getEncoded());
        String publicPem = toPublicPem(pair.getPublic().getEncoded());

        RSAPrivateKey privateKey = CryptoUtils.pemReadRsaPrivateKey(privatePem);
        var publicKey = CryptoUtils.pemReadRsaPublicKey(publicPem);
        byte[] data = {1, 2, 3};
        byte[] signature = CryptoUtils.rsaSha256Sign(privateKey, data);

        assertTrue(CryptoUtils.rsaSha256Verify(publicKey, data, signature));
        assertThrows(CryptoException.class, () -> CryptoUtils.pemReadRsaPrivateKey(publicPem));
    }

    @Test
    void nonExportableHsmStyleKeyHasExplicitFailure() {
        CryptoException missingEncoding = assertThrows(
                CryptoException.class,
                () -> CryptoUtils.pemEncode(new NonExportableEcPrivateKey("PKCS#8")));
        CryptoException missingFormat = assertThrows(
                CryptoException.class,
                () -> CryptoUtils.pemEncode(new NonExportableEcPrivateKey(null)));
        CryptoException inaccessibleFormat = assertThrows(
                CryptoException.class,
                () -> CryptoUtils.pemEncode(new NonExportableEcPrivateKey(null, true)));

        assertFalse(missingEncoding.getMessage().contains("null"));
        assertFalse(missingFormat.getMessage().contains("null"));
        assertFalse(inaccessibleFormat.getMessage().contains("null"));
    }

    @Test
    void generatedKeysUseOnlyTheRequestedCurves() {
        assertEquals(256, ((java.security.interfaces.ECPublicKey) CryptoUtils
                .ecGenerateKeyPair(EcKeyUtils.Curve.P256)
                .getPublic()).getParams().getCurve().getField().getFieldSize());
        assertEquals(384, ((java.security.interfaces.ECPublicKey) CryptoUtils
                .ecGenerateKeyPair(EcKeyUtils.Curve.P384)
                .getPublic()).getParams().getCurve().getField().getFieldSize());
        assertEquals(521, ((java.security.interfaces.ECPublicKey) CryptoUtils
                .ecGenerateKeyPair(EcKeyUtils.Curve.P521)
                .getPublic()).getParams().getCurve().getField().getFieldSize());
        assertEquals("secp256r1", EcKeyUtils.Curve.P256.standardName());
    }

    private static String signatureAlgorithm(EcKeyUtils.Curve curve) {
        return switch (curve) {
            case P256 -> "SHA256withECDSA";
            case P384 -> "SHA384withECDSA";
            case P521 -> "SHA512withECDSA";
        };
    }

    private static List<String> providerNames() {
        return Arrays.stream(Security.getProviders()).map(Provider::getName).toList();
    }

    private static String toPrivatePem(byte[] encoded) {
        String base64 = java.util.Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(encoded);
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n";
    }

    private static String toPublicPem(byte[] encoded) {
        String base64 = java.util.Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(encoded);
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----\n";
    }

    private static final class NonExportableEcPrivateKey implements ECPrivateKey {

        private final @Nullable String format;
        private final boolean throwOnFormat;

        NonExportableEcPrivateKey(@Nullable String format) {
            this(format, false);
        }

        NonExportableEcPrivateKey(@Nullable String format, boolean throwOnFormat) {
            this.format = format;
            this.throwOnFormat = throwOnFormat;
        }

        @Override
        public String getAlgorithm() {
            return "EC";
        }

        @Override
        public @Nullable String getFormat() {
            if (throwOnFormat) {
                throw new UnsupportedOperationException("provider does not expose format");
            }
            return format;
        }

        @Override
        public byte @Nullable [] getEncoded() {
            return null;
        }

        @Override
        public BigInteger getS() {
            return BigInteger.ONE;
        }

        @Override
        public @Nullable ECParameterSpec getParams() {
            return null;
        }
    }
}
