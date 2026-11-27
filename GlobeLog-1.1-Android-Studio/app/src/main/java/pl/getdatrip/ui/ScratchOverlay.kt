package pl.getdatrip.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay

private const val MAX_LOCALITY_RADIUS_METERS = 2_500.0
private const val MAX_RADIUS_SHARE_OF_NEIGHBOR_DISTANCE = 0.46
private const val RING_VERTICES = 48
private const val SCRATCH_ALPHA = 191

internal data class ScratchLocality(val center: GeoPoint, val outline: LocalityBoundary?)

internal class ScratchOverlay : Overlay() {
    private data class RevealedRing(
        val anchor: GeoPoint,
        val points: List<GeoPoint>,
        val north: Double = points.maxOf { it.latitude },
        val south: Double = points.minOf { it.latitude },
        val longitudeRadius: Double = points.maxOf { abs(longitudeDelta(it.longitude, anchor.longitude)) },
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0f
    }
    private val outsideAreas = Path()
    private val pixel = Point()
    private val visibleBounds = Rect()
    private var xCoordinates = FloatArray(RING_VERTICES)
    private var yCoordinates = FloatArray(RING_VERTICES)
    private var areas = emptyList<RevealedRing>()

    fun setScratchColor(color: Int): Boolean {
        val maskedColor = (color and 0x00FFFFFF) or (SCRATCH_ALPHA shl 24)
        if (paint.color == maskedColor) return false
        paint.color = maskedColor
        edgePaint.color = maskedColor
        return true
    }

    fun showRevealedAreas(locations: List<ScratchLocality>) {
        val tileSystem = MapView.getTileSystem()
        val ordered = locations.distinctBy { it.center }.sortedBy { it.center.latitude }
        areas = buildList {
            for ((index, locality) in ordered.withIndex()) {
                val center = locality.center
                val outline = locality.outline
                if (outline != null) {
                    for (ring in outline.rings) {
                        val points = ring.points.map { GeoPoint(tileSystem.cleanLatitude(it.latitude), it.longitude) }
                        add(RevealedRing(points.first(), points))
                    }
                    continue
                }

                var radius = MAX_LOCALITY_RADIUS_METERS
                // Search the latitude band that could intersect this small fallback area.
                val nearby = 0.055
                var start = index - 1
                while (start >= 0 && center.latitude - ordered[start].center.latitude <= nearby) start--
                var neighborIndex = start + 1
                while (neighborIndex < ordered.size && ordered[neighborIndex].center.latitude - center.latitude <= nearby) {
                    val neighbor = ordered[neighborIndex++].center
                    if (neighbor == center) continue
                    val distance = center.distanceToAsDouble(neighbor)
                    if (distance > 0) radius = minOf(radius, distance * MAX_RADIUS_SHARE_OF_NEIGHBOR_DISTANCE)
                }
                val boundary = List(RING_VERTICES) { vertex ->
                    val point = center.destinationPoint(radius, 360.0 * vertex / RING_VERTICES)
                    GeoPoint(tileSystem.cleanLatitude(point.latitude), point.longitude)
                }
                add(RevealedRing(center, boundary))
            }
        }
        val capacity = areas.maxOfOrNull { it.points.size } ?: 0
        if (capacity > xCoordinates.size) {
            xCoordinates = FloatArray(capacity)
            yCoordinates = FloatArray(capacity)
        }
    }

    override fun draw(canvas: Canvas, projection: Projection) {
        if (!canvas.getClipBounds(visibleBounds)) return
        outsideAreas.rewind()
        outsideAreas.fillType = Path.FillType.INVERSE_WINDING

        for (area in areas) appendVisibleCopies(area, projection)

        if (outsideAreas.isEmpty) {
            canvas.drawColor(paint.color)
        } else {
            canvas.drawPath(outsideAreas, paint)
            outsideAreas.fillType = Path.FillType.WINDING
            canvas.drawPath(outsideAreas, edgePaint)
        }
    }

    private fun appendVisibleCopies(area: RevealedRing, projection: Projection) {
        val north = projection.getLongPixelYFromLatitude(area.north, false)
        val south = projection.getLongPixelYFromLatitude(area.south, false)
        if (north > visibleBounds.bottom || south < visibleBounds.top) return

        val centerX = projection.toPixels(area.anchor, pixel).x.toDouble()
        val worldWidth = projection.worldMapSize
        val wraps = projection.isHorizontalWrapEnabled
        val nearestX = if (wraps) centerX + worldWidth * round((visibleBounds.centerX() - centerX) / worldWidth) else centerX
        val horizontalReach = worldWidth * area.longitudeRadius / 360.0 + 2.0
        if (nearestX + horizontalReach < visibleBounds.left || nearestX - horizontalReach > visibleBounds.right) return
        var left = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY

        for (index in area.points.indices) {
            projection.toPixels(area.points[index], pixel)
            val x = if (wraps) {
                // Keep the ring continuous where projected copies of the world meet.
                (pixel.x - worldWidth * round((pixel.x - centerX) / worldWidth)).toFloat()
            } else {
                pixel.x.toFloat()
            }
            xCoordinates[index] = x
            yCoordinates[index] = pixel.y.toFloat()
            left = minOf(left, x)
            right = maxOf(right, x)
        }

        val copies = if (wraps) {
            ceil((visibleBounds.left - right) / worldWidth).toInt()..
                floor((visibleBounds.right - left) / worldWidth).toInt()
        } else {
            0..0
        }

        for (copy in copies) {
            val shift = (worldWidth * copy).toFloat()
            outsideAreas.moveTo(xCoordinates[0] + shift, yCoordinates[0])
            for (index in 1 until area.points.size) {
                outsideAreas.lineTo(xCoordinates[index] + shift, yCoordinates[index])
            }
            outsideAreas.close()
        }
    }
}
