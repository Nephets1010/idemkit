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

import java.util.ArrayList;
import java.util.List;

/** 一次 SAGA 运行的结果。编排器在全部步骤成功时返回成功结果，失败时直接抛异常而非返回。 */
public final class SagaOutcome {

    private final boolean success;
    private final List<String> completedSteps;
    private final String failedStep;
    private final String error;

    private SagaOutcome(Builder b) {
        this.success = b.success;
        this.completedSteps = b.completedSteps;
        this.failedStep = b.failedStep;
        this.error = b.error;
    }

    public static SagaOutcome success(List<String> completedSteps) {
        return builder(true).completedSteps(completedSteps).build();
    }

    public boolean success() {
        return success;
    }

    public List<String> completedSteps() {
        return new ArrayList<>(completedSteps);
    }

    public String failedStep() {
        return failedStep;
    }

    public String error() {
        return error;
    }

    public static Builder builder(boolean success) {
        return new Builder(success);
    }

    /** 构造器。 */
    public static final class Builder {
        private final boolean success;
        private List<String> completedSteps = new ArrayList<>();
        private String failedStep;
        private String error;

        private Builder(boolean success) {
            this.success = success;
        }

        public Builder completedSteps(List<String> completedSteps) {
            this.completedSteps = new ArrayList<>(completedSteps);
            return this;
        }

        public Builder failedStep(String failedStep) {
            this.failedStep = failedStep;
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public SagaOutcome build() {
            return new SagaOutcome(this);
        }
    }

    @Override
    public String toString() {
        return "SagaOutcome{success="
                + success
                + ", completedSteps="
                + completedSteps
                + ", failedStep='"
                + failedStep
                + "'}";
    }
}
