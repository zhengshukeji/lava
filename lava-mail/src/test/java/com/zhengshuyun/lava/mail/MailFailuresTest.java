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
package com.zhengshuyun.lava.mail;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.FolderClosedException;
import jakarta.mail.MessagingException;
import jakarta.mail.StoreClosedException;
import jakarta.mail.internet.AddressException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLHandshakeException;
import java.io.Serial;
import java.net.*;

import static org.junit.jupiter.api.Assertions.*;

class MailFailuresTest {
    @Test
    void classifiesSpecificFailuresBeforeGenericMessagingFailure() {
        MessagingException authentication = new MessagingException("outer protocol message");
        authentication.setNextException(new AuthenticationFailedException("password-secret"));
        assertKind(MailFailureKind.AUTHENTICATION, authentication);

        assertKind(MailFailureKind.TLS,
                new MessagingException("wrapper", new SSLHandshakeException("tls-secret")));
        assertKind(MailFailureKind.TIMEOUT, new SocketTimeoutException("timeout"));
        assertKind(MailFailureKind.CONNECTION, new ConnectException("connection"));
        assertKind(MailFailureKind.CONNECTION, new NoRouteToHostException("route"));
        assertKind(MailFailureKind.CONNECTION, new UnknownHostException("dns"));
        assertKind(MailFailureKind.CONNECTION, new SocketException("reset"));
        assertKind(MailFailureKind.CONNECTION, new FolderClosedException(null, "disconnected"));
        assertKind(MailFailureKind.CONNECTION, new StoreClosedException(null, "disconnected"));
        assertKind(MailFailureKind.PARSING, new AddressException("bad address"));
        assertKind(MailFailureKind.PROTOCOL, new MessagingException("protocol"));
        assertKind(MailFailureKind.PARSING, new IllegalStateException("unexpected"));
    }

    @Test
    void handlesCyclicCauseChainsAndKeepsOriginalCause() {
        CyclingFailure first = new CyclingFailure("first-secret");
        CyclingFailure second = new CyclingFailure("second-secret");
        first.next = second;
        second.next = first;

        MailException wrapped = MailFailures.wrap("test operation", first);

        assertEquals(MailFailureKind.PARSING, wrapped.kind());
        assertFalse(wrapped.getMessage().contains("first-secret"));
        assertSame(first, wrapped.getCause());
    }

    @Test
    void preservesAlreadyStructuredMailFailure() {
        MailException original = new MailException(MailFailureKind.SIZE_LIMIT, "bounded");
        assertSame(original, MailFailures.wrap("ignored", original));
    }

    private static void assertKind(MailFailureKind expected, Throwable failure) {
        MailException wrapped = MailFailures.wrap("mail operation", failure);
        assertEquals(expected, wrapped.kind());
    }

    private static final class CyclingFailure extends Exception {
        @Serial
        private static final long serialVersionUID = 1L;

        private @Nullable Throwable next;

        private CyclingFailure(String message) {
            super(message);
        }

        @Override
        public synchronized @Nullable Throwable getCause() {
            return next;
        }
    }
}
