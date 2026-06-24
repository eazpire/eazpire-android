package com.eazpire.creator.ar.poster

import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import kotlin.math.sqrt

/** Screen-normalized aim point — slightly below center. */
const val POSTER_AR_HIT_TEST_X = 0.5f
const val POSTER_AR_HIT_TEST_Y = 0.55f

/** Minimum movement (m) before the preview anchor is recreated. */
private const val PREVIEW_POSE_UPDATE_THRESHOLD_M = 0.025f

fun findPosterWallHit(
    frame: Frame,
    screenWidthPx: Float,
    screenHeightPx: Float,
): HitResult? {
    val screenX = screenWidthPx * POSTER_AR_HIT_TEST_X
    val screenY = screenHeightPx * POSTER_AR_HIT_TEST_Y
    return findPosterWallHitAtScreenPoint(frame, screenX, screenY)
}

fun findPosterWallHitAtScreenPoint(
    frame: Frame,
    screenX: Float,
    screenY: Float,
): HitResult? {
    val camera = frame.camera
    if (camera.trackingState != TrackingState.TRACKING) return null

    val hits = frame.hitTest(screenX, screenY)
    hits.firstOrNull { it.isValidWallHit() }?.let { return it }
    return hits.firstOrNull { it.isValidFloorHit() }
}

fun createPosterFallbackAnchor(session: Session, frame: Frame): com.google.ar.core.Anchor? {
    val camera = frame.camera
    if (camera.trackingState != TrackingState.TRACKING) return null

    val cameraPose = camera.displayOrientedPose
    val offset = floatArrayOf(0f, 0f, -1.8f)
    val worldPose = cameraPose.compose(Pose(offset, floatArrayOf(0f, 0f, 0f, 1f)))
    return session.createAnchor(worldPose)
}

fun HitResult.isValidWallHit(): Boolean {
    val trackable = trackable
    if (trackable is Plane) {
        return trackable.type == Plane.Type.VERTICAL &&
            trackable.trackingState == TrackingState.TRACKING &&
            trackable.isPoseInPolygon(hitPose)
    }
    if (trackable is Point) {
        return trackable.trackingState == TrackingState.TRACKING
    }
    return false
}

fun HitResult.isValidFloorHit(): Boolean {
    val trackable = trackable
    if (trackable is Plane) {
        return trackable.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
            trackable.trackingState == TrackingState.TRACKING &&
            trackable.isPoseInPolygon(hitPose)
    }
    return false
}

fun shouldUpdatePosterPreviewAnchor(previousPose: Pose?, nextPose: Pose): Boolean {
    if (previousPose == null) return true
    val dx = previousPose.tx() - nextPose.tx()
    val dy = previousPose.ty() - nextPose.ty()
    val dz = previousPose.tz() - nextPose.tz()
    return sqrt(dx * dx + dy * dy + dz * dz) >= PREVIEW_POSE_UPDATE_THRESHOLD_M
}
