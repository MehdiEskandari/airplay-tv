package io.github.mehdieskandari.airplaytv.service

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test

class ApprovalRequestTest {
    private fun request() = ApprovalRequest("device", "Phone", "iPhone", "AirPlay")

    @Test fun approvalFromAnotherThreadReleasesTheWaitingSender() {
        val request = request()
        val executor = Executors.newSingleThreadExecutor()
        try {
            val started = CountDownLatch(1)
            val result = executor.submit<Boolean> {
                started.countDown()
                request.awaitResult(5, TimeUnit.SECONDS)
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            request.answer(true)
            assertTrue(result.get(5, TimeUnit.SECONDS))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun denialReleasesTheSenderWithoutAllowingTheConnection() {
        val request = request()
        request.answer(false)
        assertFalse(request.awaitResult(0, TimeUnit.SECONDS))
    }

    @Test fun missingAnswerTimesOutWithoutAllowingTheConnection() {
        assertFalse(request().awaitResult(1, TimeUnit.MILLISECONDS))
    }

    @Test fun aSecondAnswerCannotOverrideTheFirstDecision() {
        val denied = request()
        denied.answer(false)
        denied.answer(true)
        assertFalse(denied.awaitResult(0, TimeUnit.SECONDS))
        val allowed = request()
        allowed.answer(true)
        allowed.answer(false)
        assertTrue(allowed.awaitResult(0, TimeUnit.SECONDS))
    }

    @Test fun interruptingTheWaitDeniesTheConnectionAndPreservesTheInterrupt() {
        val request = request()
        val started = CountDownLatch(1)
        val allowed = AtomicBoolean(true)
        val interrupted = AtomicBoolean(false)
        val thread = Thread {
            started.countDown()
            allowed.set(request.awaitResult(30, TimeUnit.SECONDS))
            interrupted.set(Thread.currentThread().isInterrupted)
        }
        thread.start()
        assertTrue(started.await(5, TimeUnit.SECONDS))
        thread.interrupt()
        thread.join(5_000)
        assertFalse(thread.isAlive)
        assertFalse(allowed.get())
        assertTrue(interrupted.get())
    }
}
