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

package com.zhengshuyun.lava.http;

/**
 * 常用媒体类型常量
 *
 * @author Toint
 * @since 2026/1/8
 */
public final class HttpMediaTypes {

    private HttpMediaTypes() {
    }

    /** 任意二进制流。 */
    public static final String APPLICATION_OCTET_STREAM = "application/octet-stream";
    /** JSON 文档。 */
    public static final String APPLICATION_JSON = "application/json";
    /** XML 文档。 */
    public static final String APPLICATION_XML = "application/xml";
    /** URL 编码的表单提交。 */
    public static final String APPLICATION_FORM_URLENCODED = "application/x-www-form-urlencoded";
    /** multipart 表单提交。 */
    public static final String MULTIPART_FORM_DATA = "multipart/form-data";
    /** 纯文本。 */
    public static final String TEXT_PLAIN = "text/plain";
}
