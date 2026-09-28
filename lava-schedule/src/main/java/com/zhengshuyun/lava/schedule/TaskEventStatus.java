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
 * 单次调度执行的终态结果。
 */
public enum TaskEventStatus {
    /**
     * 任务运行结束且未抛出异常。
     */
    SUCCESS,
    /**
     * 任务运行时抛出异常。
     */
    FAILURE,
    /**
     * occurrence 因上一次执行尚未结束（{@link ConcurrencyPolicy#SKIP_IF_RUNNING}）而未运行。
     */
    SKIPPED,
    /**
     * occurrence 因队列已满、执行器拒绝或补偿上限而未被接受。
     */
    REJECTED
}
