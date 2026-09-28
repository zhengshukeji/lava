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

package com.zhengshuyun.lava.pay.wechat.h5;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.net.URI;

/**
 * H5 下单结果。
 *
 * @param h5Url 支付跳转链接，有效期 5 分钟；前端跳转到该地址拉起微信收银台，
 *              可追加 {@code redirect_url} 参数指定支付后返回页面
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record H5PrepayResponse(@JsonProperty("h5_url") URI h5Url) {
}
