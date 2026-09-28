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

import com.zhengshuyun.lava.core.lang.ValidationUtils;

import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * PKCS#8 私钥与 X.509 公钥的 PEM 编解码工具。
 *
 * <p>读取方法同时接受带 {@code -----BEGIN ...-----} 头尾的 PEM 和不带头尾的裸 Base64 DER，
 * 后者是支付宝等国内平台控制台导出密钥的常见形式。EC 密钥固定使用 JDK 自带的 SunEC 解析，
 * 不依赖第三方 Provider。</p>
 */
public final class PemKeyUtils {

    private static final String PRIVATE_KEY = "PRIVATE KEY";
    private static final String PUBLIC_KEY = "PUBLIC KEY";
    private static final Base64.Encoder PEM_ENCODER =
            Base64.getMimeEncoder(64, new byte[]{'\n'});

    private PemKeyUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 将私钥编码为 PKCS#8 PEM，或将公钥编码为 X.509 PEM。
     *
     * @param key 私钥或公钥
     * @return 每行 64 字符、以换行结尾的 PEM 文本
     * @throws CryptoException 密钥不可导出（如 HSM 中的密钥），或编码格式不是 PKCS#8/X.509
     */
    public static String toPem(Key key) {
        ValidationUtils.requireNonNull(key, "key must not be null");
        String type = key instanceof PrivateKey ? PRIVATE_KEY
                : key instanceof PublicKey ? PUBLIC_KEY : null;
        if (type == null) {
            throw new CryptoException("Only private and public keys can be encoded as PEM");
        }
        String format;
        byte[] encoded;
        try {
            format = key.getFormat();
            encoded = key.getEncoded();
        } catch (RuntimeException exception) {
            throw new CryptoException("The key is not exportable", exception);
        }
        String expectedFormat = type.equals(PRIVATE_KEY) ? "PKCS#8" : "X.509";
        if (encoded == null || encoded.length == 0 || !expectedFormat.equalsIgnoreCase(format)) {
            throw new CryptoException("The key is not exportable as " + expectedFormat);
        }
        return "-----BEGIN " + type + "-----\n"
                + PEM_ENCODER.encodeToString(encoded)
                + "\n-----END " + type + "-----\n";
    }

    /**
     * 读取 PKCS#8 RSA 私钥。
     *
     * @param text PEM 或裸 Base64 DER
     * @return RSA 私钥
     * @throws CryptoException 内容不是有效的 PKCS#8 RSA 私钥
     */
    public static RSAPrivateKey readRsaPrivateKey(String text) {
        return generate(text, PRIVATE_KEY, RSAPrivateKey.class, "RSA");
    }

    /**
     * 读取 X.509 RSA 公钥。
     *
     * @param text PEM 或裸 Base64 DER
     * @return RSA 公钥
     * @throws CryptoException 内容不是有效的 X.509 RSA 公钥
     */
    public static RSAPublicKey readRsaPublicKey(String text) {
        return generate(text, PUBLIC_KEY, RSAPublicKey.class, "RSA");
    }

    /**
     * 读取 PKCS#8 EC 私钥。
     *
     * @param text PEM 或裸 Base64 DER
     * @return EC 私钥
     * @throws CryptoException 内容不是有效的 PKCS#8 EC 私钥
     */
    public static ECPrivateKey readEcPrivateKey(String text) {
        return generate(text, PRIVATE_KEY, ECPrivateKey.class, "EC");
    }

    /**
     * 读取 X.509 EC 公钥。
     *
     * @param text PEM 或裸 Base64 DER
     * @return EC 公钥
     * @throws CryptoException 内容不是有效的 X.509 EC 公钥
     */
    public static ECPublicKey readEcPublicKey(String text) {
        return generate(text, PUBLIC_KEY, ECPublicKey.class, "EC");
    }

    private static <K extends Key> K generate(
            String text, String type, Class<K> keyType, String algorithm) {
        byte[] der = decode(text, type);
        try {
            KeyFactory factory = algorithm.equals("EC")
                    ? KeyFactory.getInstance("EC", EcKeyUtils.SUN_EC_PROVIDER)
                    : KeyFactory.getInstance(algorithm);
            Key key = type.equals(PRIVATE_KEY)
                    ? factory.generatePrivate(new PKCS8EncodedKeySpec(der))
                    : factory.generatePublic(new X509EncodedKeySpec(der));
            if (!keyType.isInstance(key)) {
                throw new CryptoException("Content is not an " + algorithm + " " + type.toLowerCase());
            }
            return keyType.cast(key);
        } catch (GeneralSecurityException exception) {
            throw new CryptoException("Invalid " + algorithm + " " + type.toLowerCase(), exception);
        }
    }

    /**
     * 取出 DER 字节：带头尾时要求头尾类型与期望一致，其余内容按严格 Base64 解码。
     * 拼接的多个 PEM、夹杂的其他边界或非法字符都会使严格解码失败，无需逐项特判。
     */
    private static byte[] decode(String text, String type) {
        ValidationUtils.requireNonNull(text, "text must not be null");
        String body = text.strip();
        if (body.startsWith("-----BEGIN ")) {
            String header = "-----BEGIN " + type + "-----";
            String footer = "-----END " + type + "-----";
            if (!body.startsWith(header) || !body.endsWith(footer)
                    || body.length() < header.length() + footer.length()) {
                throw new CryptoException("PEM must contain a single " + type + " block");
            }
            body = body.substring(header.length(), body.length() - footer.length());
        }
        try {
            byte[] der = Base64.getDecoder().decode(body.replaceAll("\\s", ""));
            if (der.length == 0) {
                throw new CryptoException("Key content must not be empty");
            }
            return der;
        } catch (IllegalArgumentException exception) {
            throw new CryptoException("Key content is not valid Base64", exception);
        }
    }
}
