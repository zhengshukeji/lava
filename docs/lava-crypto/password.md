# Argon2id 密码哈希

## 基本使用

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

`char[]` 是主入口。Lava 借用但不修改调用方数组，调用方应在使用完成后清零。`String` 重载只是便利入口，原始不可变字符串无法从内存中主动清除。

空密码和全空白密码可以被算法处理；最小长度、复杂度、泄露密码检查和登录限速属于业务认证策略。

## 默认生成参数

| 参数 | 默认值 |
| --- | ---: |
| memory | 65536 KiB（64 MiB） |
| iterations | 3 |
| parallelism | 1 |
| salt | 16 字节 |
| hash | 32 字节 |

## 自定义策略

```java
PasswordHasher hasher = new PasswordHasher(
        new PasswordHashPolicy(65_536, 3, 1, 16, 32)
);
```

参数依次为内存（KiB）、迭代次数、并行通道数、盐字节数、哈希字节数，低于 Argon2 规范下限时抛出 `IllegalArgumentException`。

验证时以 PHC 字符串中记录的参数计算，因此调整策略后旧哈希仍可验证；另有固定的安全上限（内存 4 GiB、迭代 1000 次、并行 255），防止被篡改的哈希触发超大内存分配。

## 验证语义

- 普通密码不匹配返回 `false`；
- 畸形、不支持或超过安全上限的 PHC 抛出 `CryptoException`；
- `needsRehash(...)` 只接受合法的 PHC；
- PHC 参数与当前生成策略不一致时返回需要升级。

不要记录密码、完整 PHC 或派生中间值。PHC 虽不包含明文密码，仍属于敏感认证数据。
