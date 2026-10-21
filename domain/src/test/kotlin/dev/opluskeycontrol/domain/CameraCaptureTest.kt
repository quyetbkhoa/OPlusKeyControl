package dev.opluskeycontrol.domain

import org.junit.Assert.*
import org.junit.Test

class CameraCaptureTest {
    @Test fun secureOpenThenExactlyOneShutterThenKeepOpen() {
        val c = CameraCapture(); val opened = c.start(0, true) as CameraEffect.OpenStock
        assertTrue(opened.secure)
        assertEquals(CameraEffect.None, c.start(10, false))
        assertEquals(CameraEffect.ShutterOnce(opened.session), c.ready(opened.session, 200))
        assertEquals(CameraEffect.None, c.ready(opened.session, 250))
        c.photoSaved(opened.session, 400); assertEquals(CameraStage.COMPLETE, c.stage)
    }
    @Test fun launchIsNotCaptureSuccess() {
        val c = CameraCapture(); c.start(0, false); assertEquals(CameraStage.OPENING, c.stage)
        c.timeout(8000); assertEquals(CameraStage.FAILED, c.stage)
    }
    @Test fun lateReadinessNeverFiresShutter() {
        val c = CameraCapture(); val s = c.start(0, false) as CameraEffect.OpenStock
        assertEquals(CameraEffect.None, c.ready(s.session, 8000)); assertEquals(CameraStage.FAILED, c.stage)
    }
    @Test fun cancelledSessionCannotShutter() {
        val c = CameraCapture(); val old = c.start(0, false) as CameraEffect.OpenStock
        c.cancel(); c.start(100, false); assertEquals(CameraEffect.None, c.ready(old.session, 200))
    }
    @Test fun photoMustBeAcknowledgedForCurrentSession() {
        val c = CameraCapture(); val s = c.start(0, false) as CameraEffect.OpenStock
        c.photoSaved(s.session, 10); assertEquals(CameraStage.OPENING, c.stage)
        c.ready(s.session, 100); c.photoSaved(s.session + 1, 200); assertEquals(CameraStage.WAITING_FOR_PHOTO, c.stage)
    }
    @Test fun noBlindShutterRetryAfterAcknowledgementTimeout() {
        val c = CameraCapture(); val s = c.start(0, false) as CameraEffect.OpenStock
        c.ready(s.session, 100); c.timeout(8000); assertEquals(CameraEffect.None, c.ready(s.session, 8001)); assertEquals(CameraStage.FAILED, c.stage)
    }
    @Test fun recordingStopSurvivesScheduleBoundary() {
        val continuation = RecordingContinuation(); val trigger = Trigger(Button.PLUS, Gesture.DOUBLE)
        continuation.started(trigger); assertTrue(continuation.shouldStop(trigger))
        assertFalse(continuation.shouldStop(Trigger(Button.VOLUME_UP, Gesture.SINGLE)))
        continuation.stopped(); assertFalse(continuation.shouldStop(trigger))
    }
}
