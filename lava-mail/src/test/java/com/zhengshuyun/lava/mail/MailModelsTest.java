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

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class MailModelsTest {
    @Test
    void credentialsPreserveOpaqueSecretsAndRejectInvalidScopes() {
        PasswordCredential password = new PasswordCredential("  user@example.com  ", " password ");
        assertEquals("user@example.com", password.username());
        assertEquals(" password ", password.password());

        OAuth2RefreshTokenCredential oauth = new OAuth2RefreshTokenCredential(
                " user@example.com ", " client ", " refresh ",
                URI.create("https://login.example.com/token?tenant=one"),
                List.of("mail.read", "mail.send"), " secret ");
        assertEquals("user@example.com", oauth.username());
        assertEquals(" client ", oauth.clientId());
        assertEquals(" refresh ", oauth.refreshToken());
        assertEquals(" secret ", oauth.clientSecret());
        assertEquals(List.of("mail.read", "mail.send"), oauth.scopes());

        assertThrows(IllegalArgumentException.class, () -> new OAuth2RefreshTokenCredential(
                "user", "client", "refresh", URI.create("https://login.example.com/token"),
                List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> new OAuth2RefreshTokenCredential(
                "user", "client", "refresh", URI.create("mailto:test@example.com"),
                List.of("mail"), null));
        assertThrows(IllegalArgumentException.class, () -> new OAuth2RefreshTokenCredential(
                "user", "client", "refresh", URI.create("https://login.example.com/token"),
                List.of("mail\tread"), null));
    }

    @Test
    void addressAndAttachmentModelsAreDefensiveAndRejectControlCharacters() {
        MailAddress address = new MailAddress("sender@example.com", "Sender");
        assertEquals("Sender", address.displayName());
        assertThrows(IllegalArgumentException.class,
                () -> new MailAddress("sender@example.com", "Bad\tName"));

        byte[] bytes = "payload".getBytes(UTF_8);
        MailAttachment attachment = new MailAttachment("note.txt", "text/plain", bytes);
        bytes[0] = 'X';
        byte[] returned = attachment.content();
        returned[0] = 'Y';
        assertArrayEquals("payload".getBytes(UTF_8), attachment.content());
        assertEquals(7, attachment.size());
        assertEquals(
                attachment,
                new MailAttachment("note.txt", "text/plain", "payload".getBytes(UTF_8)));
        assertEquals(
                attachment.hashCode(),
                new MailAttachment("note.txt", "text/plain", "payload".getBytes(UTF_8)).hashCode());
        assertNotEquals(
                attachment,
                new MailAttachment("note.txt", "text/plain", "different".getBytes(UTF_8)));
        assertThrows(IllegalArgumentException.class,
                () -> new MailAttachment("bad\0name", "text/plain", new byte[0]));
        assertThrows(IllegalArgumentException.class,
                () -> new MailAttachment("ok", "text/plain\r\nX: y", new byte[0]));

        MailAttachmentInfo info = new MailAttachmentInfo(
                0, "note.txt", "text/plain", -1, "part@example.com");
        assertEquals(-1, info.reportedSizeBytes());
        assertThrows(IllegalArgumentException.class,
                () -> new MailAttachmentInfo(-1, "note", "text/plain", 0, null));
        assertThrows(IllegalArgumentException.class,
                () -> new MailAttachmentInfo(0, "note", "text/plain", -2, null));
        assertThrows(IllegalArgumentException.class,
                () -> new MailAttachmentInfo(0, "note", "text/plain", 1, "bad\ncontent-id"));
    }

    @Test
    void sendRequestCopiesCollectionsAndRequiresSafeContent() {
        List<MailAddress> recipients = new ArrayList<>();
        recipients.add(new MailAddress("receiver@example.com"));
        MailSendRequest request = MailSendRequest.text(
                new MailAddress("sender@example.com"), recipients, "subject", "body");
        recipients.clear();
        assertEquals(1, request.to().size());
        assertEquals("body", request.textBody());
        assertNull(request.htmlBody());

        assertThrows(IllegalArgumentException.class, () -> new MailSendRequest(
                request.from(), List.of(), List.of(), List.of(), List.of(), "subject",
                "body", null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new MailSendRequest(
                request.from(), request.to(), List.of(), List.of(), List.of(), "bad\tsubject",
                "body", null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new MailSendRequest(
                request.from(), request.to(), List.of(), List.of(), List.of(), "subject",
                null, null, List.of()));
    }

    @Test
    void cursorQueryAndPageKeepStableUidIdentity() {
        MailCursor cursor = new MailCursor(" INBOX ", 42, 100);
        assertEquals("INBOX", cursor.folder());
        MailQuery first = new MailQuery(
                "INBOX", 25, null, true,
                Instant.parse("2026-08-16T00:00:00Z"),
                Instant.parse("2026-08-18T00:00:00Z"), "sender", "subject");
        MailQuery next = first.nextPage(cursor);
        assertEquals(cursor, next.cursor());
        assertTrue(next.unreadOnly());
        assertThrows(IllegalArgumentException.class, () -> first.nextPage(null));

        MailMessageId id = new MailMessageId("INBOX", 42, 99);
        MailMessageSummary summary = new MailMessageSummary(
                id, null, List.of(), List.of(), List.of(), "subject",
                null, null, true, List.of());
        MailPage<MailMessageSummary> page = new MailPage<>(List.of(summary), cursor);
        assertEquals(id, page.items().getFirst().id());
        assertEquals(cursor, page.nextCursor());

        assertThrows(IllegalArgumentException.class, () -> new MailCursor("INBOX", 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new MailCursor("INBOX", 1, MailMessageId.MAX_IMAP_UID + 1));
        assertThrows(IllegalArgumentException.class, () -> new MailMessageId("INBOX", 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> MailQuery.firstPage(0));
        assertThrows(IllegalArgumentException.class,
                () -> MailQuery.firstPage(1_001));
        assertThrows(IllegalArgumentException.class, () -> new MailQuery(
                null, 10, null, false, Instant.EPOCH, Instant.EPOCH,
                null, null));
        assertThrows(IllegalArgumentException.class, () -> new MailQuery(
                "bad\nfolder", 10, null, false, null, null, null, null));
    }

    @Test
    void optionsLimitsAndResultsValidateTheirContracts() {
        MailLimits limits = new MailLimits(10, 20, 30, 2);
        Clock clock = Clock.fixed(Instant.parse("2026-08-17T00:00:00Z"), ZoneOffset.UTC);
        MailClientOptions options = new MailClientOptions(limits, clock, Duration.ZERO);
        assertEquals(limits, options.limits());
        assertThrows(IllegalArgumentException.class,
                () -> new MailClientOptions(limits, clock, Duration.ofNanos(-1)));
        assertThrows(IllegalArgumentException.class, () -> new MailLimits(0, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new MailLimits(1, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new MailLimits(1, 1, 0, 1));

        MailSendResult result = new MailSendResult(null, clock.instant());
        assertNull(result.messageId());
        assertEquals(clock.instant(), result.sentAt());
        MailMessage message = new MailMessage(
                new MailMessageSummary(
                        new MailMessageId("INBOX", 1, 1), null,
                        List.of(), List.of(), List.of(), "", null, null, false, List.of()),
                null, null);
        assertNotNull(message.summary());
        assertFalse(message.summary().unread());
    }

    @Test
    void exceptionKeepsKindAndCause() {
        IllegalStateException cause = new IllegalStateException("boom");
        MailException failure = new MailException(
                MailFailureKind.AUTHENTICATION, "authentication failed", cause);

        assertEquals(MailFailureKind.AUTHENTICATION, failure.kind());
        assertSame(cause, failure.getCause());
        assertEquals("authentication failed", failure.getMessage());
    }
}
