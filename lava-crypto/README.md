# lava-crypto

`lava-crypto` 提供 HMAC-SHA-256、RSA-SHA256、AES-GCM、Argon2id 密码哈希、JDK EC 密钥生成以及严格的 EC/RSA PEM
读取。它使用 Bouncy Castle lightweight Argon2 API，但不会向 JVM 全局注册 Bouncy Castle Provider；标准 RSA、AES 与
EC 能力通过 JCA 获取。无状态密码学能力既可以从 `CryptoUtils` 统一进入，也可以按算法直接使用对应的
`AesGcmUtils`、`HmacUtils`、`RsaSignatureUtils`、`EcKeyUtils` 和 `PemKeyUtils`；带策略状态的密码哈希由
`PasswordHasher` 提供。

```xml
<dependency>
    <groupId>com.zhengshuyun</groupId>
    <artifactId>lava-crypto</artifactId>
</dependency>
```

## HMAC-SHA-256

```java
String digest = CryptoUtils.hmacSha256Hex(pepper, plaintext);
```

字符串入口固定使用 UTF-8，并返回 64 个小写十六进制字符。字节数组入口可获取原始的 32 字节结果。HMAC-SHA-256 通过 JCA
标准算法获取，不绑定具体 Provider，也不会修改 JVM 全局 Provider 列表。

## Argon2id

```java
PasswordHasher hasher = new PasswordHasher();
char[] password = readPassword();
try {
    String encoded = hasher.hash(password);
    boolean matches = hasher.verify(password, encoded);
    boolean upgrade = hasher.needsRehash(encoded);
} finally {
    Arrays.fill(password, '\0');
}
```

`char[]` 是主入口；Lava 借用且不修改调用方数组，调用方应在完成后清零。`String` overload 只是便利入口，无法清除原始不可变
String。空密码和全空白密码可以哈希；最小长度、复杂度、泄露密码检查等属于业务认证策略。

默认生成参数：

- memory 64 MiB（65536 KiB）
- 3 iterations
- parallelism 1
- 16-byte salt
- 32-byte hash

`PasswordHashPolicy` 将生成参数与验证资源上限分离。默认验证上限为 256 MiB、10 iterations、16 lanes、64-byte salt/hash 和
1024 个 PHC 字符，并在执行 Argon2 或攻击者可控的大分配前检查。

验证结果语义：

- 普通密码不匹配返回 `false`。
- 畸形、不支持或超过验证资源上限的 PHC 统一抛 `CryptoException`，异常消息会说明具体原因。
- `needsRehash` 只接受合法且在限制内的 PHC。

不要记录密码、完整 PHC 或派生中间值。PHC 包含 salt 和参数，虽不是明文密码，仍应按认证数据保护。

## RSA-SHA256 与 AES-GCM

```java
byte[] signature = CryptoUtils.rsaSha256Sign(privateKey, data);
boolean valid = CryptoUtils.rsaSha256Verify(publicKey, data, signature);

byte[] ciphertext = CryptoUtils.aesGcmEncrypt(key, nonce, associatedData, plaintext);
byte[] restored = CryptoUtils.aesGcmDecrypt(key, nonce, associatedData, ciphertext);
```

RSA 工具接受普通 JCA 或 HSM Provider 提供的密钥，不要求密钥可导出。AES-GCM 使用 128 位认证标签；调用方必须保证同一
密钥下 nonce 唯一，并负责在使用后清理密钥和明文数组。

## EC、RSA 与 PEM

```java
KeyPair pair = CryptoUtils.ecGenerateKeyPair(EcKeyUtils.Curve.P256);
String publicPem = CryptoUtils.pemEncode(pair.getPublic());
String privatePem = CryptoUtils.pemEncode(pair.getPrivate());

ECPublicKey publicKey = CryptoUtils.pemReadEcPublicKey(publicPem);
ECPrivateKey privateKey = CryptoUtils.pemReadEcPrivateKey(privatePem);
```

`CryptoUtils` 的 PEM 读取入口只接受：

- EC private key：PKCS#8 `PRIVATE KEY`
- EC public key：X.509 SubjectPublicKeyInfo `PUBLIC KEY`
- RSA private key：PKCS#8 `PRIVATE KEY`
- RSA public key：X.509 SubjectPublicKeyInfo `PUBLIC KEY`

解析器限制 PEM 字符数和 DER 字节数，要求唯一且匹配的 header/footer，并校验算法和格式。它不支持传统 `EC PRIVATE KEY`
、证书、多个拼接块或加密 PEM。

HSM/PKCS#11 等不可导出密钥的 `getFormat()`/`getEncoded()` 可能为空；此时 `pemEncode` 抛出带明确原因的
`CryptoException`。不要为了导出而降低 HSM 策略。`pemEncode` 产生的是未加密私钥文本，调用方必须限制其存储、日志和
传输范围。
