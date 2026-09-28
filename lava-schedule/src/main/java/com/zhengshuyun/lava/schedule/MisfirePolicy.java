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

/**
 * 计划时刻已被错过（暂停后恢复、协调线程停顿、时钟前跳或首次时刻已过去）时的处理方式。
 *
 * <p>无论哪种策略，错过的多次触发都不会逐次补偿；触发器随后从当前时刻之后的下一个计划时刻继续推进。
 * 与 Quartz CronTrigger 的 {@code FIRE_ONCE_NOW} / {@code DO_NOTHING} 两种 misfire 指令对应。
 */
public enum MisfirePolicy {

    /**
     * 错过的多次触发合并为一次立即执行。默认策略。
     */
    FIRE_ONCE_NOW,

    /**
     * 丢弃错过的触发，只产生一次 {@link TaskEventStatus#SKIPPED} 事件；一次性触发器因此不再执行。
     */
    SKIP
}
