/*
 * Copyright 2026 The idemkit Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.nephets.idemkit.core.saga;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** SAGA 步骤执行的重试策略。描述最多尝试次数，以及每次失败后到下次尝试之间的等待时长。 */
public final class RetryPolicy {

    /** 退避方式。 */
    public enum Backoff {
        /** 不重试。 */
        NONE,
        /** 固定间隔。 */
        FIXED,
        /** 指数退避，单次等待随失败次数翻倍，封顶到最大等待。 */
        EXPONENTIAL
    }

    private final Backoff backoff;
    private final int maxAttempts;
    private final Duration baseDelay;
    private final Duration maxDelay;

    private RetryPolicy(Backoff backoff, int maxAttempts, Duration baseDelay, Duration maxDelay) {
        this.backoff = backoff;
        this.maxAttempts = maxAttempts;
        this.baseDelay = baseDelay;
        this.maxDelay = maxDelay;
    }

    /** 不重试：只尝试一次。 */
    public static RetryPolicy none() {
        return new RetryPolicy(Backoff.NONE, 1, Duration.ZERO, Duration.ZERO);
    }

    /** 固定间隔重试。 */
    public static RetryPolicy fixed(int maxAttempts, Duration delay) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts 至少为 1");
        }
        return new RetryPolicy(Backoff.FIXED, maxAttempts, delay, delay);
    }

    /** 指数退避重试。 */
    public static RetryPolicy exponential(int maxAttempts, Duration base, Duration max) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts 至少为 1");
        }
        Objects.requireNonNull(base, "base 不能为空");
        Objects.requireNonNull(max, "max 不能为空");
        return new RetryPolicy(Backoff.EXPONENTIAL, maxAttempts, base, max);
    }

    /** 第 {@code failedAttempts} 次失败之后、下一次尝试之前的等待时长。已是最后一次失败后返回零。 */
    public Duration delayFor(int failedAttempts) {
        if (failedAttempts >= maxAttempts - 1) {
            return Duration.ZERO;
        }
        switch (backoff) {
            case NONE:
                return Duration.ZERO;
            case FIXED:
                return baseDelay;
            case EXPONENTIAL:
                long millis = baseDelay.toMillis() << Math.min(failedAttempts, 30);
                long capped = Math.min(millis, maxDelay.toMillis());
                return Duration.ofMillis(capped);
            default:
                return Duration.ZERO;
        }
    }

    /** 最多尝试次数（含首次）。 */
    public int maxAttempts() {
        return maxAttempts;
    }

    public Backoff backoff() {
        return backoff;
    }

    /** 让当前线程按策略等待；被中断时恢复中断标记后返回。 */
    public void sleepFor(int failedAttempts) {
        Duration d = delayFor(failedAttempts);
        if (d.isZero() || d.isNegative()) {
            return;
        }
        try {
            TimeUnit.MILLISECONDS.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
