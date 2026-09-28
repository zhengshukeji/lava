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
 * 传输和本地 HTTP 失败的稳定分类。
 */
public enum HttpFailureKind {
    /** 域名解析失败（UnknownHostException）。 */
    DNS,
    /** TCP 连接建立失败或传输中被对端断开（SocketException）。 */
    CONNECTION,
    /** TLS 握手或证书校验失败（SSLException）。 */
    TLS,
    /** 连接、读取、写出或整次调用超时（InterruptedIOException）。 */
    TIMEOUT,
    /** 请求在完成前被调用方或客户端关闭取消。 */
    CANCELLED,
    /** HTTP 协议违例，如连接行为异常或响应流被截断。 */
    PROTOCOL,
    /** 无法归入更具体类别的其他 I/O 失败。 */
    IO,
    /** 缓冲响应体超过配置的字节数上限。 */
    RESPONSE_TOO_LARGE
}
