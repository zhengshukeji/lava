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

package com.zhengshuyun.lava.schedule;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class TriggerTest {

    @Test
    void oneShotAndFixedRateUseInstantAndDuration() {
        Instant first = Instant.parse("2026-08-17T00:00:00Z");
        Trigger oneShot = Trigger.at(first);
        Trigger fixed = Trigger.fixedRate(first, Duration.ofMinutes(5));

        assertEquals(first, oneShot.firstFireTime(first.minusSeconds(1)));
        assertNull(oneShot.nextExecutionAfter(first));
        assertEquals(first, oneShot.nextExecutionAfter(first.minusNanos(1)));
        assertEquals(first.plus(Duration.ofMinutes(5)), fixed.nextExecutionAfter(first));
    }

    @Test
    void relativeTriggersCalculateFromTheSuppliedClockInstant() {
        Instant now = Instant.parse("2026-08-17T00:00:00Z");

        assertEquals(now.plusSeconds(4), Trigger.after(Duration.ofSeconds(4)).firstFireTime(now));
        assertEquals(now.plusSeconds(2),
                Trigger.fixedRate(Duration.ofSeconds(2), Duration.ofSeconds(3)).firstFireTime(now));
    }

    @Test
    void cronDefaultsToUtcAndHonorsExplicitZone() {
        Instant after = Instant.parse("2026-08-17T00:00:00Z");

        assertEquals(
                Instant.parse("2026-08-17T02:00:00Z"),
                Trigger.cron("0 0 2 * * ?").nextExecutionAfter(after));
        assertEquals(
                Instant.parse("2026-08-17T18:00:00Z"),
                Trigger.cron("0 0 2 * * ?", ZoneId.of("Asia/Shanghai"))
                        .nextExecutionAfter(after));
    }

    @Test
    void cronUsesZoneRulesAcrossDstGap() {
        Trigger berlin = Trigger.cron("0 30 2 * * ?", ZoneId.of("Europe/Berlin"));

        assertEquals(
                Instant.parse("2026-03-30T00:30:00Z"),
                berlin.nextExecutionAfter(Instant.parse("2026-03-28T02:00:00Z")));
    }

    @Test
    void fixedDelayStartsAfterInitialDelayAndLeavesNextToCompletion() {
        Instant now = Instant.parse("2026-08-17T00:00:00Z");
        Trigger delayed = Trigger.fixedDelay(Duration.ofSeconds(2), Duration.ofSeconds(5));

        assertEquals(now.plusSeconds(2), delayed.firstFireTime(now));
        assertEquals(now.plusSeconds(5), Trigger.fixedDelay(Duration.ofSeconds(5)).firstFireTime(now));
        assertNull(delayed.nextExecutionAfter(now));
        assertEquals(now.plusSeconds(5), delayed.nextFixedDelayFireTime(now));
    }

    @Test
    void misfirePolicyDefaultsToFireOnceNowAndCopiesOnChange() {
        Trigger original = Trigger.cron("0 0 2 * * ?");
        Trigger skipping = original.withMisfirePolicy(MisfirePolicy.SKIP);

        assertEquals(MisfirePolicy.FIRE_ONCE_NOW, original.misfirePolicy());
        assertEquals(MisfirePolicy.SKIP, skipping.misfirePolicy());
        Instant after = Instant.parse("2026-08-17T00:00:00Z");
        assertEquals(original.nextExecutionAfter(after), skipping.nextExecutionAfter(after));
    }

    @Test
    void invalidTriggerValuesFailImmediately() {
        assertThrows(IllegalArgumentException.class, () -> Trigger.cron("bad cron"));
        assertThrows(IllegalArgumentException.class, () -> Trigger.cron(" "));
        assertThrows(IllegalArgumentException.class, () -> Trigger.after(Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class, () -> Trigger.fixedRate(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> Trigger.at(null));
        assertThrows(IllegalArgumentException.class, () -> Trigger.fixedDelay(Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> Trigger.fixedDelay(Duration.ofSeconds(-1), Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> Trigger.after(Duration.ZERO).withMisfirePolicy(null));
    }

    @Test
    void relativeTriggerOverflowIsReportedAsScheduleFailure() {
        assertThrows(
                ScheduleException.class,
                () -> Trigger.after(Duration.ofSeconds(Long.MAX_VALUE))
                        .firstFireTime(Instant.EPOCH));
    }
}
