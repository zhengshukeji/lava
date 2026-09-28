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

import okhttp3.sse.EventSource;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 可取消的 SSE 会话句柄。
 *
 * <p>会话从“连接中”出发，最终进入取消、远端关闭、失败三种终态之一；终态由 CAS 唯一确定，
 * {@link SseListener#onTerminal} 恰好调用一次。监听器回调串行执行，任一回调抛出异常都会让会话以
 * {@link SseTermination#FAILED} 结束。
 */
public final class SseSession implements AutoCloseable {

    /**
     * 会话内部状态；终态只能进入一次。
     */
    private enum State {
        CONNECTING,
        OPEN,
        CANCELLED,
        REMOTE_CLOSED,
        FAILED
    }

    private final SseListener listener;
    /**
     * 终态回调结束后通知客户端注销本会话。
     */
    private final Consumer<SseSession> onTerminated;
    private final AtomicReference<State> state = new AtomicReference<>(State.CONNECTING);
    /**
     * 底层事件源；OkHttp 异步回调可能早于 {@code newEventSource} 返回，因此延迟绑定。
     */
    private final AtomicReference<@Nullable EventSource> eventSource = new AtomicReference<>();
    private final AtomicReference<@Nullable SseTerminal> terminal = new AtomicReference<>();
    /**
     * 串行化监听器回调，保证终态回调不会与事件回调交叠。
     */
    private final Object callbackLock = new Object();

    SseSession(SseListener listener, Consumer<SseSession> onTerminated) {
        this.listener = listener;
        this.onTerminated = onTerminated;
    }

    void bind(EventSource source) {
        eventSource.compareAndSet(null, source);
        // 绑定前已被取消或失败时，补做一次底层取消
        State current = state.get();
        if (current == State.CANCELLED || current == State.FAILED) {
            source.cancel();
        }
    }

    void opened(int statusCode, HttpHeaders headers) {
        if (!state.compareAndSet(State.CONNECTING, State.OPEN)) {
            return;
        }
        Throwable callbackFailure = null;
        synchronized (callbackLock) {
            if (state.get() != State.OPEN) {
                return;
            }
            try {
                listener.onOpen(this, statusCode, headers);
            } catch (Throwable throwable) {
                callbackFailure = throwable;
            }
        }
        if (callbackFailure != null) {
            fail(new SseFailure(HttpFailureKind.IO, callbackFailure, statusCode, headers, null));
        }
    }

    void event(SseEvent event) {
        Throwable callbackFailure = null;
        synchronized (callbackLock) {
            if (state.get() != State.OPEN) {
                return;
            }
            try {
                listener.onEvent(this, event);
            } catch (Throwable throwable) {
                callbackFailure = throwable;
            }
        }
        if (callbackFailure != null) {
            fail(new SseFailure(HttpFailureKind.IO, callbackFailure, null, null, null));
        }
    }

    void remoteClosed() {
        complete(State.REMOTE_CLOSED, new SseTerminal(SseTermination.REMOTE_CLOSED, null));
    }

    void fail(SseFailure failure) {
        complete(State.FAILED, new SseTerminal(SseTermination.FAILED, failure));
    }

    private void complete(State terminalState, SseTerminal result) {
        // 远端关闭、显式取消和失败可能并发到达，CAS 决定唯一的最终结果
        while (true) {
            State current = state.get();
            if (isTerminal(current)) {
                return;
            }
            if (state.compareAndSet(current, terminalState)) {
                break;
            }
        }
        terminal.set(result);

        if (terminalState != State.REMOTE_CLOSED) {
            EventSource source = eventSource.get();
            if (source != null) {
                // 主动终态必须停止底层读取，防止终态后继续派发事件
                source.cancel();
            }
        }
        try {
            synchronized (callbackLock) {
                listener.onTerminal(this, result);
            }
        } catch (Throwable ignored) {
            // 终态回调的异常不能再触发第二个终态
        } finally {
            onTerminated.accept(this);
        }
    }

    /**
     * 主动取消会话；已进入终态时无效果。
     */
    public void cancel() {
        complete(State.CANCELLED, new SseTerminal(SseTermination.CANCELLED, null));
    }

    /**
     * 判断会话是否已进入终态。
     *
     * @return 已终止时返回 true
     */
    public boolean isClosed() {
        return isTerminal(state.get());
    }

    /**
     * 判断会话的实际终态是否为取消。
     *
     * @return 已取消时返回 true
     */
    public boolean isCancelled() {
        return state.get() == State.CANCELLED;
    }

    /**
     * 返回已经确定的终态。
     *
     * @return 终态；仍在运行时为空
     */
    public Optional<SseTerminal> terminal() {
        return Optional.ofNullable(terminal.get());
    }

    /**
     * 等同于 {@link #cancel()}。
     */
    @Override
    public void close() {
        cancel();
    }

    private static boolean isTerminal(State state) {
        return state == State.CANCELLED || state == State.REMOTE_CLOSED || state == State.FAILED;
    }
}
