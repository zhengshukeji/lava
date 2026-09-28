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

import java.security.Key;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * 无状态密码学能力的统一入口。
 *
 * <p>方法名称以算法或编码格式开头，调用方可以通过 IDE 补全发现可用能力。具体参数校验、
 * JCA 调用和异常转换由对应的专用工具负责；调用方也可以直接使用这些公开工具获取更聚焦的 API。</p>
 *
 * <p>带策略和随机源状态的密码哈希能力继续由 {@link PasswordHasher} 提供。</p>
 */
public final class CryptoUtils {

    /**
     * 工具类不允许实例化。
     */
    private CryptoUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 计算 HMAC-SHA-256，并返回原始的 32 字节结果。
     *
     * @param key  HMAC 密钥
     * @param data 待认证数据
     * @return 新创建的 32 字节 HMAC 结果
     * @throws IllegalArgumentException 密钥或数据为 null，或者密钥为空时抛出
     * @throws CryptoException          JCA Provider 无法完成计算时抛出
     */
    public static byte[] hmacSha256(byte[] key, byte[] data) {
        return HmacUtils.sha256(key, data);
    }

    /**
     * 计算 HMAC-SHA-256，并编码为小写十六进制字符串。
     *
     * @param key  HMAC 密钥
     * @param data 待认证数据
     * @return 长度为 64 的小写十六进制结果
     * @throws IllegalArgumentException 密钥或数据为 null，或者密钥为空时抛出
     * @throws CryptoException          JCA Provider 无法完成计算时抛出
     */
    public static String hmacSha256Hex(byte[] key, byte[] data) {
        return HmacUtils.sha256Hex(key, data);
    }

    /**
     * 将字符串按 UTF-8 编码后计算 HMAC-SHA-256，并返回小写十六进制字符串。
     *
     * @param key  HMAC 密钥文本
     * @param data 待认证文本
     * @return 长度为 64 的小写十六进制结果
     * @throws IllegalArgumentException 密钥或数据为 null，或者密钥为空时抛出
     * @throws CryptoException          JCA Provider 无法完成计算时抛出
     */
    public static String hmacSha256Hex(String key, String data) {
        return HmacUtils.sha256Hex(key, data);
    }

    /**
     * 使用 RSA 私钥生成 SHA-256 PKCS#1 v1.5 签名。
     *
     * @param privateKey RSA 私钥，可来自软件 Provider 或 HSM
     * @param data       待签名数据
     * @return 新创建的原始签名字节
     * @throws IllegalArgumentException 参数为空或私钥算法不是 RSA 时抛出
     * @throws CryptoException          JCA Provider 无法完成签名时抛出
     */
    public static byte[] rsaSha256Sign(PrivateKey privateKey, byte[] data) {
        return RsaSignatureUtils.sha256(privateKey, data);
    }

    /**
     * 使用 RSA 公钥验证 SHA-256 PKCS#1 v1.5 签名。
     *
     * @param publicKey RSA 公钥
     * @param data      已签名数据
     * @param signature 原始签名字节
     * @return 签名内容匹配时返回 true
     * @throws IllegalArgumentException 参数为空或公钥算法不是 RSA 时抛出
     * @throws CryptoException          签名格式无法处理或 JCA Provider 无法完成验签时抛出
     */
    public static boolean rsaSha256Verify(PublicKey publicKey, byte[] data, byte[] signature) {
        return RsaSignatureUtils.verifySha256(publicKey, data, signature);
    }

    /**
     * 使用 AES-GCM 加密，并在结果尾部附加 128 位认证标签。
     *
     * @param key            16、24 或 32 字节 AES 密钥
     * @param nonce          非空且对当前密钥唯一的随机串
     * @param associatedData 附加认证数据，可以为空数组
     * @param plaintext      明文，可以为空数组
     * @return 新创建的密文与认证标签
     * @throws IllegalArgumentException 参数无效时抛出
     * @throws CryptoException          JCA Provider 无法完成加密时抛出
     */
    public static byte[] aesGcmEncrypt(
            byte[] key, byte[] nonce, byte[] associatedData, byte[] plaintext) {
        return AesGcmUtils.encrypt(key, nonce, associatedData, plaintext);
    }

    /**
     * 验证 128 位认证标签并使用 AES-GCM 解密。
     *
     * @param key            16、24 或 32 字节 AES 密钥
     * @param nonce          加密时使用的随机串
     * @param associatedData 加密时使用的附加认证数据
     * @param ciphertext     密文与认证标签
     * @return 新创建的明文
     * @throws IllegalArgumentException 参数无效时抛出
     * @throws CryptoException          认证标签无效或 JCA Provider 无法完成解密时抛出
     */
    public static byte[] aesGcmDecrypt(
            byte[] key, byte[] nonce, byte[] associatedData, byte[] ciphertext) {
        return AesGcmUtils.decrypt(key, nonce, associatedData, ciphertext);
    }

    /**
     * 使用新的安全随机源生成 P-256 EC 密钥对。
     *
     * @return P-256 EC 密钥对
     * @throws CryptoException JDK Provider 不支持所需 EC 操作时抛出
     */
    public static KeyPair ecGenerateKeyPair() {
        return EcKeyUtils.generate();
    }

    /**
     * 使用新的安全随机源生成指定曲线的 EC 密钥对。
     *
     * @param curve EC 曲线
     * @return 指定曲线的 EC 密钥对
     * @throws IllegalArgumentException curve 为 null 时抛出
     * @throws CryptoException          JDK Provider 不支持所需 EC 操作时抛出
     */
    public static KeyPair ecGenerateKeyPair(EcKeyUtils.Curve curve) {
        return EcKeyUtils.generate(curve);
    }

    /**
     * 使用指定随机源生成指定曲线的 EC 密钥对。
     *
     * @param curve        EC 曲线
     * @param secureRandom 用于生成密钥材料的安全随机源
     * @return 指定曲线的 EC 密钥对
     * @throws IllegalArgumentException 参数为 null 时抛出
     * @throws CryptoException          JDK Provider 不支持所需 EC 操作时抛出
     */
    public static KeyPair ecGenerateKeyPair(EcKeyUtils.Curve curve, SecureRandom secureRandom) {
        return EcKeyUtils.generate(curve, secureRandom);
    }

    /**
     * 将私钥编码为 PKCS#8 PEM，或将公钥编码为 X.509 PEM。
     *
     * @param key 待编码的密钥
     * @return 含头尾边界和换行的 PEM 文本
     * @throws IllegalArgumentException key 为 null 时抛出
     * @throws CryptoException          密钥不可导出或编码格式不受支持时抛出
     */
    public static String pemEncode(Key key) {
        return PemKeyUtils.toPem(key);
    }

    /**
     * 读取 PKCS#8 EC 私钥，接受 PEM 或裸 Base64 DER。
     *
     * @param pem PEM 或裸 Base64 DER 文本
     * @return EC 私钥
     * @throws IllegalArgumentException pem 为 null 时抛出
     * @throws CryptoException          内容不是有效的对应类型密钥时抛出
     */
    public static ECPrivateKey pemReadEcPrivateKey(String pem) {
        return PemKeyUtils.readEcPrivateKey(pem);
    }

    /**
     * 读取 X.509 SubjectPublicKeyInfo EC 公钥，接受 PEM 或裸 Base64 DER。
     *
     * @param pem PEM 或裸 Base64 DER 文本
     * @return EC 公钥
     * @throws IllegalArgumentException pem 为 null 时抛出
     * @throws CryptoException          内容不是有效的对应类型密钥时抛出
     */
    public static ECPublicKey pemReadEcPublicKey(String pem) {
        return PemKeyUtils.readEcPublicKey(pem);
    }

    /**
     * 读取 PKCS#8 RSA 私钥，接受 PEM 或裸 Base64 DER。
     *
     * @param pem PEM 或裸 Base64 DER 文本
     * @return RSA 私钥
     * @throws IllegalArgumentException pem 为 null 时抛出
     * @throws CryptoException          内容不是有效的对应类型密钥时抛出
     */
    public static RSAPrivateKey pemReadRsaPrivateKey(String pem) {
        return PemKeyUtils.readRsaPrivateKey(pem);
    }

    /**
     * 读取 X.509 SubjectPublicKeyInfo RSA 公钥，接受 PEM 或裸 Base64 DER。
     *
     * @param pem PEM 或裸 Base64 DER 文本
     * @return RSA 公钥
     * @throws IllegalArgumentException pem 为 null 时抛出
     * @throws CryptoException          内容不是有效的对应类型密钥时抛出
     */
    public static RSAPublicKey pemReadRsaPublicKey(String pem) {
        return PemKeyUtils.readRsaPublicKey(pem);
    }
}
