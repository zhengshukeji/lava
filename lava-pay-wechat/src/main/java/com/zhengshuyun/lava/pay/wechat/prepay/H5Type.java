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

package com.zhengshuyun.lava.pay.wechat.prepay;

/**
 * H5 下单场景类型常量，取值区分大小写。
 */
public final class H5Type {
    /** 手机浏览器网页。 */
    public static final String WAP = "Wap";
    /** iOS 应用内打开的网页。 */
    public static final String IOS = "iOS";
    /** Android 应用内打开的网页。 */
    public static final String ANDROID = "Android";

    /** 禁止实例化场景类型常量容器。 */
    private H5Type() {
        throw new UnsupportedOperationException("Constants class");
    }
}
