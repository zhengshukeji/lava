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

package com.zhengshuyun.lava.jiandaoyun.file;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 文件上传凭证。一个凭证只能上传一个文件，有效期 1 小时。
 *
 * @param url 上传地址
 * @param token 上传凭证
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UploadToken(String url, String token) {
    /**
     * 返回不含凭证内容的描述，避免日志泄露上传凭证。
     *
     * @return 诊断文本
     */
    @Override
    public String toString() {
        return "UploadToken[url=" + url + ", token=***]";
    }
}
