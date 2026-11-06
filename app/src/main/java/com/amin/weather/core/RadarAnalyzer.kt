package com.amin.weather.core

import kotlin.math.*

enum class CellApproachState { INSIDE_NOW, APPROACHING, WILL_MISS, NOT_APPROACHING, UNKNOWN_MOTION }

data class RadarCellInfo(
    val id: String,
    val pixelCount: Int,
    val areaSqKm: Double,
    val maxDbz: Int,
    val centerLat: Double,
    val centerLon: Double,
    val centerDistKm: Double,
    val centerBearingDeg: Double,
    val radiusKm: Double,
    val speedKmh: Double?,
    val moveDirectionToDeg: Double?,
    val state: CellApproachState,
    val etaMinLow: Int?,
    val etaMinHigh: Int?,
    val minDistanceKm: Double?,
    val motionSource: String, // "radar_track", "steering_wind", or "unknown"
    val xLocalKm: Double = 0.0,
    val yLocalKm: Double = 0.0
) {
    val distKm: Double get() = centerDistKm
    val directionFa: String get() = GeoUtils.dir16Fa(centerBearingDeg)
    val headingFa: String get() = moveDirectionToDeg?.let { GeoUtils.dir16Fa(it) } ?: "نامعلوم"
    val closestDistKm: Double? get() = minDistanceKm
}

data class RadarNowcastResult(
    val available: Boolean,
    val frameAgeMin: Int,
    val coverageOk: Boolean,
    val maxDbzInTarget: Int,
    val wetFractionInTarget: Double,
    val rainRateInTargetMmH: Double,
    val cells: List<RadarCellInfo> = emptyList(),
    val firstArrival: RadarCellInfo? = null,
    val statusText: String = "",
    val maxDbzNearPoint: Int = -1,
    val upwindStrongEcho: Boolean = false,
    val coverageVerified: Boolean = true,
    val clutterPixelsRemoved: Int = 0
)

data class RawCell(
    val xKm: Double,
    val yKm: Double,
    val pixels: Int,
    val maxDbz: Int,
    val radiusKm: Double
)

object RadarAnalyzer {

    private val tileXDouble by lazy { GeoUtils.lonToTileX(RegionConfig.CENTER.lon, 7) }
    private val tileYDouble by lazy { GeoUtils.latToTileY(RegionConfig.CENTER.lat, 7) }
    private val minTileX by lazy { floor(tileXDouble).toInt() - 1 }
    private val minTileY by lazy { floor(tileYDouble).toInt() }

    private val precomputedDistKm: DoubleArray by lazy {
        val arr = DoubleArray(512 * 512)
        for (py in 0 until 512) {
            val lat = GeoUtils.tileYToLat(minTileY + (py + 0.5) / 256.0, 7)
            for (px in 0 until 512) {
                val lon = GeoUtils.tileXToLon(minTileX + (px + 0.5) / 256.0, 7)
                arr[py * 512 + px] = GeoUtils.haversineKm(RegionConfig.CENTER.lat, RegionConfig.CENTER.lon, lat, lon)
            }
        }
        arr
    }

    private val precomputedBearingDeg: DoubleArray by lazy {
        val arr = DoubleArray(512 * 512)
        for (py in 0 until 512) {
            val lat = GeoUtils.tileYToLat(minTileY + (py + 0.5) / 256.0, 7)
            for (px in 0 until 512) {
                val lon = GeoUtils.tileXToLon(minTileX + (px + 0.5) / 256.0, 7)
                val (lx, ly) = GeoUtils.toLocalKm(RegionConfig.CENTER.lat, RegionConfig.CENTER.lon, lat, lon)
                arr[py * 512 + px] = (Math.toDegrees(atan2(lx, ly)) + 360.0) % 360.0
            }
        }
        arr
    }

    fun dbzToRainRate(dbz: Int): Double {
        if (dbz < Tuning.DBZ_LIGHT) return 0.0
        val cappedDbz = minOf(dbz, Tuning.DBZ_CAP_FOR_RAINRATE)
        val z = 10.0.pow(cappedDbz / 10.0)
        return if (cappedDbz < Tuning.DBZ_STRONG) {
            (z / 200.0).pow(1.0 / 1.6)
        } else {
            (z / 300.0).pow(1.0 / 1.4)
        }
    }

    fun detectCells(grid: IntArray): List<RawCell> {
        val visited = BooleanArray(512 * 512)
        val rawCells = ArrayList<RawCell>()

        for (py in 0 until 512) {
            for (px in 0 until 512) {
                val idx = py * 512 + px
                if (!visited[idx] && grid[idx] >= Tuning.DBZ_RAIN && precomputedDistKm[idx] <= Tuning.RADAR_WATCH_RADIUS_KM) {
                    val cellPixels = ArrayList<Pair<Int, Int>>()
                    val queue = ArrayDeque<Pair<Int, Int>>()
                    queue.add(Pair(px, py))
                    visited[idx] = true

                    while (queue.isNotEmpty()) {
                        val (cx, cy) = queue.removeFirst()
                        cellPixels.add(Pair(cx, cy))

                        for (dx in -1..1) {
                            for (dy in -1..1) {
                                if (dx == 0 && dy == 0) continue
                                val nx = cx + dx
                                val ny = cy + dy
                                if (nx in 0..511 && ny in 0..511) {
                                    val nIdx = ny * 512 + nx
                                    if (!visited[nIdx] && grid[nIdx] >= Tuning.DBZ_RAIN && precomputedDistKm[nIdx] <= Tuning.RADAR_WATCH_RADIUS_KM) {
                                        visited[nIdx] = true
                                        queue.add(Pair(nx, ny))
                                    }
                                }
                            }
                        }
                    }

                    if (cellPixels.size >= Tuning.MIN_CELL_PIXELS) {
                        var sumZ = 0.0
                        var sumX = 0.0
                        var sumY = 0.0
                        var maxDbzCell = -1

                        for ((cpx, cpy) in cellPixels) {
                            val dbz = grid[cpy * 512 + cpx]
                            if (dbz > maxDbzCell) maxDbzCell = dbz
                            val z = 10.0.pow(dbz / 10.0)
                            sumZ += z
                            val (lx, ly) = pixelToLocalKm(cpx, cpy)
                            sumX += lx * z
                            sumY += ly * z
                        }

                        val comX = sumX / sumZ
                        val comY = sumY / sumZ
                        val areaSqKm = cellPixels.size * 1.03 * 1.03
                        val radiusKm = sqrt(areaSqKm / PI)

                        rawCells.add(RawCell(comX, comY, cellPixels.size, maxDbzCell, radiusKm))
                    }
                }
            }
        }
        return rawCells
    }

    fun matchVelocity(c: RawCell, prev: List<RawCell>, dtMin: Double): Pair<Double, Double>? {
        if (dtMin < Tuning.TRACK_FRAME_GAP_MIN_MIN || dtMin > Tuning.TRACK_FRAME_GAP_MAX_MIN) return null
        val maxMove = Tuning.TRACK_MATCH_MAX_KM * dtMin / 10.0
        val best = prev.minByOrNull { hypot(it.xKm - c.xKm, it.yKm - c.yKm) } ?: return null
        val d = hypot(best.xKm - c.xKm, best.yKm - c.yKm)
        if (d > maxMove) return null
        val dtH = dtMin / 60.0
        val vx = (c.xKm - best.xKm) / dtH
        val vy = (c.yKm - best.yKm) / dtH
        val spd = hypot(vx, vy)
        return if (spd in Tuning.CELL_SPEED_MIN_KMH..Tuning.CELL_SPEED_MAX_KMH) Pair(vx, vy) else null
    }

    fun analyzeNowcast(
        fetch: RadarFetch?,
        steering: SteeringWind? = null,
        modelDryNow: Boolean? = null,
        nowSec: Long = System.currentTimeMillis() / 1000
    ): RadarNowcastResult {
        if (fetch == null || fetch.frames.isEmpty() || fetch.lastFrameAgeMin > Tuning.RADAR_MAX_FRAME_AGE_MIN) {
            val statusMsg = when {
                fetch == null || fetch.frames.isEmpty() -> "داده‌های راداری در دسترس نیست"
                else -> "رادار به‌روز نیست (عمر فریم: ${fetch.lastFrameAgeMin} دقیقه)"
            }
            return RadarNowcastResult(
                available = false,
                frameAgeMin = fetch?.lastFrameAgeMin ?: 999,
                coverageOk = fetch?.coverageVerified ?: false,
                maxDbzInTarget = -1,
                wetFractionInTarget = 0.0,
                rainRateInTargetMmH = 0.0,
                cells = emptyList(),
                firstArrival = null,
                statusText = statusMsg,
                coverageVerified = fetch?.coverageVerified ?: false
            )
        }

        val frames = fetch.frames
        val lastFrame = frames.last()
        val frameAgeMin = fetch.lastFrameAgeMin

        // Clutter filter
        val grid = lastFrame.dbzGrid.clone()
        var clutterPixelsRemoved = 0

        if (frames.size >= Tuning.CLUTTER_FRAMES && modelDryNow == true) {
            for (idx in 0 until (512 * 512)) {
                var allRain = true
                var minDbz = Int.MAX_VALUE
                var maxDbz = Int.MIN_VALUE

                for (f in frames) {
                    val dbz = f.dbzGrid[idx]
                    if (dbz < Tuning.DBZ_RAIN) {
                        allRain = false
                        break
                    }
                    if (dbz < minDbz) minDbz = dbz
                    if (dbz > maxDbz) maxDbz = dbz
                }

                if (allRain && (maxDbz - minDbz) <= Tuning.CLUTTER_MAX_DBZ_RANGE) {
                    grid[idx] = -1
                    clutterPixelsRemoved++
                }
            }
        }

        // Target stats
        var maxDbzTarget = -1
        var maxDbzNearPoint = -1
        var wetPixelCountTarget = 0
        var totalPixelCountTarget = 0
        var sumRainRateTarget = 0.0
        var upwindStrongEcho = false

        for (idx in 0 until (512 * 512)) {
            val dist = precomputedDistKm[idx]
            val dbz = grid[idx]

            if (dist <= Tuning.TARGET_RADIUS_KM) {
                totalPixelCountTarget++
                if (dbz >= Tuning.DBZ_RAIN) {
                    wetPixelCountTarget++
                    if (dbz > maxDbzTarget) maxDbzTarget = dbz
                    sumRainRateTarget += dbzToRainRate(dbz)
                }
            }

            if (dist <= Tuning.POINT_RADIUS_KM && dbz >= Tuning.DBZ_RAIN) {
                if (dbz > maxDbzNearPoint) maxDbzNearPoint = dbz
            }

            if (dbz >= Tuning.DBZ_STRONG) {
                val bearing = precomputedBearingDeg[idx]
                if (dist in Tuning.UPWIND_RING_MIN_KM..Tuning.RADAR_WATCH_RADIUS_KM &&
                    bearing in Tuning.UPWIND_BEARING_MIN..Tuning.UPWIND_BEARING_MAX) {
                    upwindStrongEcho = true
                }
            }
        }

        val wetFractionTarget = if (totalPixelCountTarget > 0) wetPixelCountTarget.toDouble() / totalPixelCountTarget else 0.0
        val meanRainRateTarget = if (wetPixelCountTarget > 0) sumRainRateTarget / wetPixelCountTarget else 0.0

        // Detect raw cells for all frames to perform tracking
        val rawCellFrames = frames.map { detectCells(if (it == lastFrame) grid else it.dbzGrid) }
        val curRawCells = rawCellFrames.last()

        val parsedCells = ArrayList<RadarCellInfo>()
        var cellCounter = 1

        for (c in curRawCells) {
            val centerLat = GeoUtils.destination(RegionConfig.CENTER.lat, RegionConfig.CENTER.lon, 0.0, c.yKm).first
            val centerLon = GeoUtils.destination(RegionConfig.CENTER.lat, RegionConfig.CENTER.lon, 90.0, c.xKm).second

            val centerDist = hypot(c.xKm, c.yKm)
            val centerBearing = (Math.toDegrees(atan2(c.xKm, c.yKm)) + 360.0) % 360.0

            val areaSqKm = c.pixels * 1.03 * 1.03

            // Attempt radar tracking
            var trackVel: Pair<Double, Double>? = null
            var motionSource = "radar_track"

            if (frames.size >= 2) {
                val prev1Cells = rawCellFrames[rawCellFrames.size - 2]
                val dt1Min = (lastFrame.timeEpochSec - frames[frames.size - 2].timeEpochSec) / 60.0
                val vel1 = matchVelocity(c, prev1Cells, dt1Min)

                if (frames.size >= 3 && vel1 != null) {
                    val prev2Cells = rawCellFrames[rawCellFrames.size - 3]
                    val prev1CellBest = prev1Cells.minByOrNull { hypot(it.xKm - c.xKm, it.yKm - c.yKm) }
                    if (prev1CellBest != null) {
                        val dt2Min = (frames[frames.size - 2].timeEpochSec - frames[frames.size - 3].timeEpochSec) / 60.0
                        val vel2 = matchVelocity(prev1CellBest, prev2Cells, dt2Min)
                        if (vel2 != null) {
                            trackVel = Pair((vel1.first + vel2.first) / 2.0, (vel1.second + vel2.second) / 2.0)
                        } else {
                            trackVel = vel1
                        }
                    } else {
                        trackVel = vel1
                    }
                } else {
                    trackVel = vel1
                }
            }

            var cellSpeed: Double? = null
            var cellDirTo: Double? = null

            if (trackVel != null) {
                cellSpeed = hypot(trackVel.first, trackVel.second)
                cellDirTo = (Math.toDegrees(atan2(trackVel.first, trackVel.second)) + 360.0) % 360.0
            } else {
                // Fallback to steering wind for current hour
                val sIdx = steering?.times?.indexOfLast { it <= nowSec } ?: -1
                if (sIdx >= 0 && steering != null && !steering.speedKmh[sIdx].isNaN() && !steering.dirToDeg[sIdx].isNaN()) {
                    cellSpeed = steering.speedKmh[sIdx]
                    cellDirTo = steering.dirToDeg[sIdx]
                    motionSource = "steering_wind"
                } else {
                    motionSource = "unknown"
                }
            }

            val tupleEta = if (cellSpeed != null && cellDirTo != null) {
                val vx = cellSpeed * sin(Math.toRadians(cellDirTo))
                val vy = cellSpeed * cos(Math.toRadians(cellDirTo))
                computeApproachEta(c.xKm, c.yKm, vx, vy, c.radiusKm)
            } else {
                TupleEta(CellApproachState.UNKNOWN_MOTION, null, null, null)
            }

            parsedCells.add(
                RadarCellInfo(
                    id = "C$cellCounter",
                    pixelCount = c.pixels,
                    areaSqKm = areaSqKm,
                    maxDbz = c.maxDbz,
                    centerLat = centerLat,
                    centerLon = centerLon,
                    centerDistKm = centerDist,
                    centerBearingDeg = centerBearing,
                    radiusKm = c.radiusKm,
                    speedKmh = cellSpeed,
                    moveDirectionToDeg = cellDirTo,
                    state = tupleEta.state,
                    etaMinLow = tupleEta.etaLow,
                    etaMinHigh = tupleEta.etaHigh,
                    minDistanceKm = tupleEta.dMinKm,
                    motionSource = motionSource,
                    xLocalKm = c.xKm,
                    yLocalKm = c.yKm
                )
            )
            cellCounter++
        }

        parsedCells.sortBy { it.centerDistKm }
        val topCells = parsedCells.take(5)

        val firstArrival = parsedCells
            .filter { it.state == CellApproachState.APPROACHING && it.maxDbz >= Tuning.DBZ_RAIN && it.etaMinLow != null }
            .minByOrNull { it.etaMinLow!! }

        val shownCells = if (firstArrival != null && firstArrival !in topCells) listOf(firstArrival) + topCells.take(4) else topCells

        val statusText = buildString {
            when {
                maxDbzTarget >= Tuning.DBZ_SEVERE -> append("رادار زنده: لکه بارشی شدیدی با قدرت $maxDbzTarget dBZ داخل دایره ۲۰km شمس‌آباد فعال است.")
                maxDbzTarget >= Tuning.DBZ_RAIN -> append("رادار زنده: لکه بارشی با قدرت $maxDbzTarget dBZ داخل دایره ۲۰km شمس‌آباد رصد شد.")
                firstArrival != null -> append("رادار زنده: توده بارشی ${firstArrival.maxDbz} dBZ در ${firstArrival.distKm.toInt()}km (${firstArrival.directionFa})، ورود محتمل بین ${firstArrival.etaMinLow} تا ${firstArrival.etaMinHigh} دقیقه دیگر.")
                shownCells.isNotEmpty() -> append("رادار زنده: ${shownCells.size} توده بارشی خارج از دایره ۲۰km شمس‌آباد رصد شد.")
                else -> append("رادار زنده: شعاع پایش ۲۰ کیلومتری شمس‌آباد پاک و بدون لکه بارشی است.")
            }
            if (clutterPixelsRemoved > 0) {
                append(" ($clutterPixelsRemoved پیکسل کلاتر حذف شد)")
            }
            if (!fetch.coverageVerified) {
                append(" (پوشش رادار تایید نشده)")
            }
        }

        return RadarNowcastResult(
            available = true,
            frameAgeMin = frameAgeMin,
            coverageOk = fetch.coverageVerified,
            maxDbzInTarget = maxDbzTarget,
            wetFractionInTarget = wetFractionTarget,
            rainRateInTargetMmH = meanRainRateTarget,
            cells = shownCells,
            firstArrival = firstArrival,
            statusText = statusText,
            maxDbzNearPoint = maxDbzNearPoint,
            upwindStrongEcho = upwindStrongEcho,
            coverageVerified = fetch.coverageVerified,
            clutterPixelsRemoved = clutterPixelsRemoved
        )
    }

    fun computeApproachEta(
        xKm: Double, yKm: Double,
        vxKmh: Double, vyKmh: Double,
        cellRadiusKm: Double
    ): TupleEta {
        val r = Tuning.TARGET_RADIUS_KM + cellRadiusKm
        val pv = xKm * vxKmh + yKm * vyKmh
        val vv = vxKmh * vxKmh + vyKmh * vyKmh
        val pp = xKm * xKm + yKm * yKm

        if (pp <= r * r) {
            return TupleEta(CellApproachState.INSIDE_NOW, 0, 0, 0.0)
        }

        if (vv < 1e-6 || pv >= 0) {
            val dMin = sqrt(max(0.0, pp - if (vv >= 1e-6) (pv * pv / vv) else 0.0))
            return TupleEta(CellApproachState.NOT_APPROACHING, null, null, dMin)
        }

        val disc = pv * pv - vv * (pp - r * r)
        if (disc < 0) {
            val dMin = sqrt(max(0.0, pp - (pv * pv / vv)))
            return TupleEta(CellApproachState.WILL_MISS, null, null, dMin)
        }

        val tEnterH = (-pv - sqrt(disc)) / vv
        val dMin = sqrt(max(0.0, pp - (pv * pv / vv)))
        val etaMin = (tEnterH * 60.0).toInt().coerceAtLeast(1)
        val etaLow = (etaMin * (1.0 - Tuning.ETA_UNCERTAINTY_FRAC)).toInt().coerceAtLeast(1)
        val etaHigh = (etaMin * (1.0 + Tuning.ETA_UNCERTAINTY_FRAC)).toInt().coerceAtLeast(etaLow)

        return TupleEta(CellApproachState.APPROACHING, etaLow, etaHigh, dMin)
    }

    data class TupleEta(
        val state: CellApproachState,
        val etaLow: Int?,
        val etaHigh: Int?,
        val dMinKm: Double?
    )

    private fun pixelToLocalKm(px: Int, py: Int): Pair<Double, Double> {
        val lon = GeoUtils.tileXToLon(minTileX + (px + 0.5) / 256.0, 7)
        val lat = GeoUtils.tileYToLat(minTileY + (py + 0.5) / 256.0, 7)
        return GeoUtils.toLocalKm(RegionConfig.CENTER.lat, RegionConfig.CENTER.lon, lat, lon)
    }
}
