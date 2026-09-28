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
 * 同一任务上一次执行尚未结束时，新触发的处理方式。
 *
 * <p>全局并发上限由调度器的执行器约束（见 {@link LavaScheduler.Builder#executionBounds(int, int)}），
 * 超出时产生 {@link TaskEventStatus#REJECTED} 事件。
 */
public enum ConcurrencyPolicy {

    /**
     * 跳过本次触发并产生 {@link TaskEventStatus#SKIPPED} 事件；同一任务永不重叠执行。默认策略。
     */
    SKIP_IF_RUNNING,

    /**
     * 允许同一任务的多次执行并行。
     */
    PARALLEL
}
