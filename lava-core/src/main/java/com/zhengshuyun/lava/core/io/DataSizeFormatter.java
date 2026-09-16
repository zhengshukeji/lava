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

package com.zhengshuyun.lava.core.io;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 把非负字节数格式化为无歧义的 IEC 或 SI 单位文本。
 */
public final class DataSizeFormatter {

    private static final String[] IEC_UNITS = {"B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB"};
    private static final String[] SI_UNITS = {"B", "kB", "MB", "GB", "TB", "PB", "EB"};

    private DataSizeFormatter() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 按 1024 的幂和 KiB、MiB 等 IEC 符号格式化。
     *
     * <pre>{@code
     * DataSizeFormatter.formatIec(1_536); // "1.5 KiB"
     * }</pre>
     *
     * @param bytes 非负字节数
     * @return 格式化后的 IEC 容量文本
     */
    public static String formatIec(long bytes) {
        return format(bytes, 1024, IEC_UNITS);
    }

    /**
     * 按 1000 的幂和 kB、MB 等 SI 符号格式化。
     *
     * <pre>{@code
     * DataSizeFormatter.formatSi(1_536); // "1.54 kB"
     * }</pre>
     *
     * @param bytes 非负字节数
     * @return 格式化后的 SI 容量文本
     */
    public static String formatSi(long bytes) {
        return format(bytes, 1000, SI_UNITS);
    }

    private static String format(long bytes, int base, String[] units) {
        if (bytes < 0) {
            throw new IllegalArgumentException("bytes must not be negative");
        }
        if (bytes < base) {
            return bytes + " B";
        }

        BigDecimal byteCount = BigDecimal.valueOf(bytes);
        BigDecimal divisor = BigDecimal.ONE;
        int unit = 0;
        while (unit < units.length - 1 && byteCount.compareTo(divisor.multiply(BigDecimal.valueOf(base))) >= 0) {
            divisor = divisor.multiply(BigDecimal.valueOf(base));
            unit++;
        }

        BigDecimal value = byteCount.divide(divisor, 2, RoundingMode.HALF_UP);
        if (value.compareTo(BigDecimal.valueOf(base)) >= 0 && unit < units.length - 1) {
            value = value.divide(BigDecimal.valueOf(base), 2, RoundingMode.HALF_UP);
            unit++;
        }
        return value.stripTrailingZeros().toPlainString() + " " + units[unit];
    }
}
