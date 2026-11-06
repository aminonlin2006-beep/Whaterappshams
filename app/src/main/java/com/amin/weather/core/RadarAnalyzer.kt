package com.amin.weather.core

import kotlin.math.*

enum class CellApproachState { INSIDE_NOW, APPROACHING, WILL_MISS, NOT_APPROACHING }

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
    val speedKmh: Double,
    val moveDirectionToDeg: Double,
    val state: CellApproachState,
    val etaMinLow: Int?,
    val etaMinHigh: Int?,
    val minDistanceKm: Double,
    val motionSource: String, // "radar_track" or "steering_wind"
    val xLocalKm: Double = 0.0,
    val yLocalKm: Double = 0.0
) {
    val distKm: Double get() = centerDistKm
    val directionFa: String get() = GeoUtils.dir16Fa(centerBearingDeg)
    val headingFa: String get() = GeoUtils.dir16Fa(moveDirectionToDeg)
    val etaLowMin: Int get() = etaMinLow ?: 0
    val etaHighMin: Int get() = etaMinHigh ?: 0
    val closestDistKm: Double get() = minDistanceKm
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
    val statusText: String = ""
)

object RadarAnalyzer {

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

    fun analyzeNowcast(
        frames: List<RadarFrame>?,
        steeringWind: SteeringWind? = null
    ): RadarNowcastResult {
        if (frames.isNullOrEmpty()) {
            return RadarNowcastResult(
                available = false, frameAgeMin = 999, coverageOk = false,
                maxDbzInTarget = -1, wetFractionInTarget = 0.0, rainRateInTargetMmH = 0.0,
                cells = emptyList(), firstArrival = null,
                statusText = "داده‌های راداری در دسترس نیست"
            )
        }

        val lastFrame = frames.last()
        val nowSec = System.currentTimeMillis() / 1000
        val frameAgeMin = ((nowSec - lastFrame.timeEpochSec) / 60).toInt().coerceAtLeast(0)

        val tileXDouble = GeoUtils.lonToTileX(RegionConfig.CENTER.lon, 7)
        val tileYDouble = GeoUtils.latToTileY(RegionConfig.CENTER.lat, 7)
        val minTileX = floor(tileXDouble).toInt() - 1
        val minTileY = floor(tileYDouble).toInt()

        val grid = lastFrame.dbzGrid

        var maxDbzTarget = -1
        var wetPixelCountTarget = 0
        var totalPixelCountTarget = 0
        var sumRainRateTarget = 0.0

        // Analyze 20km target area
        for (py in 0 until 512) {
            val lat = GeoUtils.tileYToLat(minTileY + (py + 0.5) / 256.0, 7)
            for (px in 0 until 512) {
                val lon = GeoUtils.tileXToLon(minTileX + (px + 0.5) / 256.0, 7)
                val dist = GeoUtils.haversineKm(RegionConfig.CENTER.lat, RegionConfig.CENTER.lon, lat, lon)

                if (dist <= Tuning.TARGET_RADIUS_KM) {
                    totalPixelCountTarget++
                    val dbz = grid[py * 512 + px]
                    if (dbz >= Tuning.DBZ_RAIN) {
                        wetPixelCountTarget++
                        if (dbz > maxDbzTarget) maxDbzTarget = dbz
                        sumRainRateTarget += dbzToRainRate(dbz)
                    }
                }
            }
        }

        val wetFractionTarget = if (totalPixelCountTarget > 0) wetPixelCountTarget.toDouble() / totalPixelCountTarget else 0.0
        val meanRainRateTarget = if (wetPixelCountTarget > 0) sumRainRateTarget / wetPixelCountTarget else 0.0

        // Steering wind defaults
        val steeringSpeedKmh = steeringWind?.speedKmh?.firstOrNull() ?: 25.0
        val steeringDirToDeg = steeringWind?.dirToDeg?.firstOrNull() ?: 60.0

        // Cell Identification on 8-connected components
        val visited = BooleanArray(512 * 512)
        val rawCells = ArrayList<List<Pair<Int, Int>>>()

        for (py in 0 until 512) {
            for (px in 0 until 512) {
                val idx = py * 512 + px
                if (!visited[idx] && grid[idx] >= Tuning.DBZ_RAIN) {
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
                                    if (!visited[nIdx] && grid[nIdx] >= Tuning.DBZ_RAIN) {
                                        visited[nIdx] = true
                                        queue.add(Pair(nx, ny))
                                    }
                                }
                            }
                        }
                    }

                    if (cellPixels.size >= Tuning.MIN_CELL_PIXELS) {
                        rawCells.add(cellPixels)
                    }
                }
            }
        }

        // Process cells
        val parsedCells = ArrayList<RadarCellInfo>()
        var cellCounter = 1

        for (pixels in rawCells) {
            var sumZ = 0.0
            var sumX = 0.0
            var sumY = 0.0
            var maxDbzCell = -1

            for ((px, py) in pixels) {
                val dbz = grid[py * 512 + px]
                if (dbz > maxDbzCell) maxDbzCell = dbz
                val z = 10.0.pow(dbz / 10.0)
                sumZ += z
                val (lx, ly) = pixelToLocalKm(px, py, minTileX, minTileY)
                sumX += lx * z
                sumY += ly * z
            }

            val comX = sumX / sumZ
            val comY = sumY / sumZ

            val centerLat = GeoUtils.destination(RegionConfig.CENTER.lat, RegionConfig.CENTER.lon, 0.0, comY).first
            val centerLon = GeoUtils.destination(RegionConfig.CENTER.lat, RegionConfig.CENTER.lon, 90.0, comX).second

            val centerDist = hypot(comX, comY)
            val centerBearing = (Math.toDegrees(atan2(comX, comY)) + 360.0) % 360.0

            val areaSqKm = pixels.size * 1.03 * 1.03
            val radiusKm = sqrt(areaSqKm / PI)

            val cellSpeed = steeringSpeedKmh
            val cellDirTo = steeringDirToDeg
            val motionSource = "steering_wind"

            val (vx, vy) = Pair(
                cellSpeed * sin(Math.toRadians(cellDirTo)),
                cellSpeed * cos(Math.toRadians(cellDirTo))
            )

            val (state, etaLow, etaHigh, dMin) = computeApproachEta(
                comX, comY, vx, vy, radiusKm
            )

            parsedCells.add(
                RadarCellInfo(
                    id = "C$cellCounter",
                    pixelCount = pixels.size,
                    areaSqKm = areaSqKm,
                    maxDbz = maxDbzCell,
                    centerLat = centerLat,
                    centerLon = centerLon,
                    centerDistKm = centerDist,
                    centerBearingDeg = centerBearing,
                    radiusKm = radiusKm,
                    speedKmh = cellSpeed,
                    moveDirectionToDeg = cellDirTo,
                    state = state,
                    etaMinLow = etaLow,
                    etaMinHigh = etaHigh,
                    minDistanceKm = dMin,
                    motionSource = motionSource,
                    xLocalKm = comX,
                    yLocalKm = comY
                )
            )
            cellCounter++
        }

        parsedCells.sortBy { it.centerDistKm }
        val topCells = parsedCells.take(5)

        val firstArrival = topCells.firstOrNull { it.state == CellApproachState.APPROACHING && it.maxDbz >= Tuning.DBZ_RAIN }

        val statusText = when {
            maxDbzTarget >= Tuning.DBZ_SEVERE -> "رادار زنده: لکه بارشی شدیدی با قدرت $maxDbzTarget dBZ داخل دایره ۲۰km شمس‌آباد فعال است."
            maxDbzTarget >= Tuning.DBZ_RAIN -> "رادار زنده: لکه بارشی با قدرت $maxDbzTarget dBZ داخل دایره ۲۰km شمس‌آباد رصد شد."
            firstArrival != null -> "رادار زنده: توده بارشی ${firstArrival.maxDbz} dBZ در ${firstArrival.distKm.toInt()}km (${firstArrival.directionFa})، ورود محتمل بین ${firstArrival.etaLowMin} تا ${firstArrival.etaHighMin} دقیقه دیگر."
            topCells.isNotEmpty() -> "رادار زنده: ${topCells.size} توده بارشی خارج از دایره ۲۰km شمس‌آباد رصد شد."
            else -> "رادار زنده: شعاع پایش ۲۰ کیلومتری شمس‌آباد پاک و بدون لکه بارشی است."
        }

        return RadarNowcastResult(
            available = true,
            frameAgeMin = frameAgeMin,
            coverageOk = true,
            maxDbzInTarget = maxDbzTarget,
            wetFractionInTarget = wetFractionTarget,
            rainRateInTargetMmH = meanRainRateTarget,
            cells = topCells,
            firstArrival = firstArrival,
            statusText = statusText
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
        val dMinKm: Double
    )

    private fun pixelToLocalKm(px: Int, py: Int, minTileX: Int, minTileY: Int): Pair<Double, Double> {
        val lon = GeoUtils.tileXToLon(minTileX + (px + 0.5) / 256.0, 7)
        val lat = GeoUtils.tileYToLat(minTileY + (py + 0.5) / 256.0, 7)
        return GeoUtils.toLocalKm(RegionConfig.CENTER.lat, RegionConfig.CENTER.lon, lat, lon)
    }
}
