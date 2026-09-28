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

import com.zhengshuyun.lava.core.lang.ValidationUtils;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * 由一个 {@link LavaScheduler} 拥有的任务生命周期句柄。
 */
public final class ScheduledTask {

    private final LavaScheduler.TaskControl control;

    ScheduledTask(LavaScheduler.TaskControl control) {
        this.control = control;
    }

    /**
     * 返回任务标识。
     *
     * @return 任务标识
     */
    public String id() {
        return control.id();
    }

    /**
     * 暂停后续触发，不会打断已经开始的执行。
     */
    public void pause() {
        control.pause();
    }

    /**
     * 恢复已暂停任务的后续触发。
     */
    public void resume() {
        control.resume();
    }

    /**
     * 原地替换触发器，并按新触发器重新计算下一次执行。
     *
     * <p>与先取消再重新注册不同，任务标识和运行状态保持不变：旧触发器下仍在进行的执行不受影响，
     * 且继续参与并发策略判断，{@link ConcurrencyPolicy#SKIP_IF_RUNNING} 下不会与新触发器的执行重叠。
     * 已暂停的任务保持暂停，恢复后按新触发器执行。
     *
     * @param trigger 新的触发器
     */
    public void reschedule(Trigger trigger) {
        control.reschedule(ValidationUtils.requireNonNull(trigger, "trigger must not be null"));
    }

    /**
     * 判断任务是否已暂停。
     *
     * @return 已暂停时为 true
     */
    public boolean isPaused() {
        return control.isPaused();
    }

    /**
     * 取消后续和排队中的任务，但不打断正在执行的任务。
     *
     * @return 成功取消时为 true
     */
    public boolean cancel() {
        return control.cancel(false);
    }

    /**
     * 取消此任务，并可选择打断该调度器创建的执行。
     *
     * @param mayInterruptIfRunning 为 true 时请求打断正在执行的任务
     * @return 成功取消时为 true
     */
    public boolean cancel(boolean mayInterruptIfRunning) {
        return control.cancel(mayInterruptIfRunning);
    }

    /**
     * 判断任务是否仍由调度器管理。
     *
     * @return 仍存在时为 true
     */
    public boolean exists() {
        return control.exists();
    }

    /**
     * 将一次立即执行交给相同的有界并发策略。
     */
    public void triggerNow() {
        control.triggerNow();
    }

    /**
     * 返回下一次计划执行时间。
     *
     * @return 下一次执行时间；没有下一次执行时为 null
     */
    public @Nullable Instant nextExecution() {
        return control.nextExecution();
    }

    /**
     * 返回最近一次计划执行时间。
     *
     * @return 最近一次执行时间；尚未执行时为 null
     */
    public @Nullable Instant previousExecution() {
        return control.previousExecution();
    }
}
