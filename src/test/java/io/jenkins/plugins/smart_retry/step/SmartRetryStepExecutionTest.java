package io.jenkins.plugins.smart_retry.step;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SmartRetryStepExecutionTest {

    @Test
    void restoresThreadInterruptFlagWhenInterruptedExceptionWasCaught() {
        Assertions.assertFalse(Thread.currentThread().isInterrupted());

        SmartRetryStepExecution.restoreInterruptIfNeeded(new InterruptedException("resume interrupted"));

        Assertions.assertTrue(Thread.currentThread().isInterrupted());
        Thread.interrupted();
    }

    @Test
    void leavesThreadInterruptFlagClearForNonInterruptingExceptions() {
        Assertions.assertFalse(Thread.currentThread().isInterrupted());

        SmartRetryStepExecution.restoreInterruptIfNeeded(new IllegalStateException("not interrupted"));

        Assertions.assertFalse(Thread.currentThread().isInterrupted());
    }
}
