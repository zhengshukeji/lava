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
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 不可变且线程安全的 Argon2id 密码哈希器，输出标准 PHC 字符串
 * （如 {@code $argon2id$v=19$m=65536,t=3,p=1$<盐>$<哈希>}）。
 *
 * <pre>{@code
 * PasswordHasher hasher = new PasswordHasher();
 * String stored = hasher.hash(password);
 * boolean ok = hasher.verify(password, stored);
 * if (ok && hasher.needsRehash(stored)) {
 *     stored = hasher.hash(password);
 * }
 * }</pre>
 */
public final class PasswordHasher {

    private static final int VERSION = Argon2Parameters.ARGON2_VERSION_13;
    private static final Pattern PHC = Pattern.compile(
            "\\$argon2id\\$v=(\\d{1,3})\\$m=(\\d{1,10}),t=(\\d{1,10}),p=(\\d{1,10})"
                    + "\\$([A-Za-z0-9+/]+)\\$([A-Za-z0-9+/]+)");
    /**
     * 验证时允许的参数上限，防止被篡改或损坏的哈希让验证分配数 GB 内存；远高于任何合理配置。
     */
    private static final long MAX_MEMORY_KIB = 4L * 1024 * 1024;
    private static final long MAX_ITERATIONS = 1_000;
    private static final long MAX_PARALLELISM = 255;
    private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();

    private final PasswordHashPolicy policy;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 使用 {@link PasswordHashPolicy#DEFAULT} 创建哈希器。
     */
    public PasswordHasher() {
        this(PasswordHashPolicy.DEFAULT);
    }

    /**
     * 使用指定策略创建哈希器。
     *
     * @param policy 生成新哈希时使用的参数
     */
    public PasswordHasher(PasswordHashPolicy policy) {
        this.policy = ValidationUtils.requireNonNull(policy, "policy must not be null");
    }

    /**
     * 返回生成新哈希时使用的参数。
     *
     * @return 哈希策略
     */
    public PasswordHashPolicy policy() {
        return policy;
    }

    /**
     * 使用随机盐计算密码哈希。
     *
     * @param password 密码；调用方可在返回后自行清零
     * @return PHC 字符串
     * @throws IllegalArgumentException 密码包含不成对的 UTF-16 代理字符
     */
    public String hash(char[] password) {
        ValidationUtils.requireNonNull(password, "password must not be null");
        byte[] salt = new byte[policy.saltLengthBytes()];
        secureRandom.nextBytes(salt);
        byte[] hash = computeHash(password, salt, policy.memoryKiB(), policy.iterations(),
                policy.parallelism(), policy.hashLengthBytes());
        return "$argon2id$v=" + VERSION
                + "$m=" + policy.memoryKiB() + ",t=" + policy.iterations() + ",p=" + policy.parallelism()
                + "$" + BASE64_ENCODER.encodeToString(salt)
                + "$" + BASE64_ENCODER.encodeToString(hash);
    }

    /**
     * 使用随机盐计算密码哈希。
     *
     * @param password 密码
     * @return PHC 字符串
     */
    public String hash(String password) {
        ValidationUtils.requireNonNull(password, "password must not be null");
        return hash(password.toCharArray());
    }

    /**
     * 使用哈希中记录的参数重新计算并以常量时间比较；参数与当前策略不同的旧哈希同样可以验证。
     *
     * @param password    待验证的密码
     * @param encodedHash PHC 字符串
     * @return 密码匹配时返回 true
     * @throws CryptoException 哈希不是合法的 Argon2id PHC 字符串或参数超出安全上限
     */
    public boolean verify(char[] password, String encodedHash) {
        ValidationUtils.requireNonNull(password, "password must not be null");
        ParsedHash parsed = parse(encodedHash);
        byte[] computed = computeHash(password, parsed.salt(), parsed.memoryKiB(),
                parsed.iterations(), parsed.parallelism(), parsed.hash().length);
        return MessageDigest.isEqual(parsed.hash(), computed);
    }

    /**
     * 使用哈希中记录的参数重新计算并以常量时间比较。
     *
     * @param password    待验证的密码
     * @param encodedHash PHC 字符串
     * @return 密码匹配时返回 true
     * @throws CryptoException 哈希不是合法的 Argon2id PHC 字符串或参数超出安全上限
     */
    public boolean verify(String password, String encodedHash) {
        ValidationUtils.requireNonNull(password, "password must not be null");
        return verify(password.toCharArray(), encodedHash);
    }

    /**
     * 判断已存储的哈希是否使用了与当前策略不同的参数，应在验证成功后重新哈希。
     *
     * @param encodedHash PHC 字符串
     * @return 参数与当前策略不一致时返回 true
     * @throws CryptoException 哈希不是合法的 Argon2id PHC 字符串或参数超出安全上限
     */
    public boolean needsRehash(String encodedHash) {
        ParsedHash parsed = parse(encodedHash);
        return parsed.memoryKiB() != policy.memoryKiB()
                || parsed.iterations() != policy.iterations()
                || parsed.parallelism() != policy.parallelism()
                || parsed.salt().length != policy.saltLengthBytes()
                || parsed.hash().length != policy.hashLengthBytes();
    }

    private static ParsedHash parse(String encodedHash) {
        ValidationUtils.requireNonNull(encodedHash, "encodedHash must not be null");
        Matcher matcher = PHC.matcher(encodedHash);
        if (!matcher.matches()) {
            throw new CryptoException("Invalid Argon2id PHC string");
        }
        if (Integer.parseInt(matcher.group(1)) != VERSION) {
            throw new CryptoException("Unsupported Argon2id version: " + matcher.group(1));
        }
        long memory = Long.parseLong(matcher.group(2));
        long iterations = Long.parseLong(matcher.group(3));
        long parallelism = Long.parseLong(matcher.group(4));
        if (memory > MAX_MEMORY_KIB || iterations > MAX_ITERATIONS || parallelism > MAX_PARALLELISM) {
            throw new CryptoException("Argon2id parameters exceed the verification limit");
        }
        PasswordHashPolicy parameters;
        byte[] salt;
        byte[] hash;
        try {
            // 复用策略的下限校验，拒绝 Argon2 规范不允许的参数
            parameters = new PasswordHashPolicy((int) memory, (int) iterations, (int) parallelism,
                    8, 4);
            salt = BASE64_DECODER.decode(matcher.group(5));
            hash = BASE64_DECODER.decode(matcher.group(6));
        } catch (IllegalArgumentException exception) {
            throw new CryptoException("Invalid Argon2id PHC string", exception);
        }
        if (salt.length < 8 || hash.length < 4) {
            throw new CryptoException("Argon2id salt or hash is too short");
        }
        return new ParsedHash(parameters.memoryKiB(), parameters.iterations(), parameters.parallelism(),
                salt, hash);
    }

    private static byte[] computeHash(char[] password, byte[] salt, int memoryKiB, int iterations,
                                      int parallelism, int hashLength) {
        byte[] passwordBytes = encodeUtf8(password);
        try {
            Argon2Parameters parameters = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                    .withVersion(VERSION)
                    .withMemoryAsKB(memoryKiB)
                    .withIterations(iterations)
                    .withParallelism(parallelism)
                    .withSalt(salt)
                    .build();
            byte[] result = new byte[hashLength];
            Argon2BytesGenerator generator = new Argon2BytesGenerator();
            generator.init(parameters);
            generator.generateBytes(passwordBytes, result);
            return result;
        } finally {
            // 提供 char[] 接口的意义在于调用方可清除明文，这里同样清除内部转换出的字节副本
            Arrays.fill(passwordBytes, (byte) 0);
        }
    }

    /**
     * 严格按 UTF-8 编码密码；不成对的代理字符会被拒绝，而不是替换为 '?' 导致不同密码碰撞。
     */
    private static byte[] encodeUtf8(char[] password) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(password));
            byte[] result = new byte[encoded.remaining()];
            encoded.get(result);
            if (encoded.hasArray()) {
                Arrays.fill(encoded.array(), (byte) 0);
            }
            return result;
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("password contains malformed UTF-16", exception);
        }
    }

    private record ParsedHash(int memoryKiB, int iterations, int parallelism, byte[] salt, byte[] hash) {
    }
}
