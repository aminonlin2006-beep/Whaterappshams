package com.amin.weather.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File
import kotlin.math.floor

data class RadarFrame(
    val timeEpochSec: Long,
    val dbzGrid: IntArray // 512x512 grid, -1 for no rain
)

data class RadarFetch(
    val frames: List<RadarFrame>,
    val lastFrameAgeMin: Int,
    val coverageVerified: Boolean
)

object RadarClient {
    private const val MAPS_URL = "https://api.rainviewer.com/public/weather-maps.json"

    fun fetchRadarFrames(cacheDir: File? = null): SourceResult<RadarFetch> {
        cleanTileCache(cacheDir)

        val resp = Http.get(MAPS_URL)
        if (!resp.isSuccess || resp.bodyString.isNullOrBlank()) {
            return SourceResult("rainviewer/radar", SourceStatus.FAILED, null, resp.error ?: "Failed to fetch RainViewer maps")
        }

        return try {
            val json = JSONObject(resp.bodyString!!)
            val host = json.optString("host")
            val radarObj = json.optJSONObject("radar")
            val pastArr = radarObj?.optJSONArray("past")

            if (host.isBlank() || pastArr == null || pastArr.length() == 0) {
                return SourceResult("rainviewer/radar", SourceStatus.UNAVAILABLE, null, "RainViewer radar data unavailable")
            }

            val nowSec = System.currentTimeMillis() / 1000
            val lastFrameObj = pastArr.getJSONObject(pastArr.length() - 1)
            val lastFrameTime = lastFrameObj.getLong("time")
            val frameAgeMin = ((nowSec - lastFrameTime) / 60).toInt().coerceAtLeast(0)

            // Tile coordinates
            val tileXDouble = GeoUtils.lonToTileX(RegionConfig.CENTER.lon, 7)
            val tileYDouble = GeoUtils.latToTileY(RegionConfig.CENTER.lat, 7)
            val centerTileX = floor(tileXDouble).toInt()
            val centerTileY = floor(tileYDouble).toInt()
            val minTileX = centerTileX - 1
            val minTileY = centerTileY

            // Check coverage tile
            val (coverageOk, coverageVerified) = checkCoverage(cacheDir, host, centerTileX, centerTileY, tileXDouble, tileYDouble)

            if (coverageVerified && !coverageOk) {
                return SourceResult("rainviewer/radar", SourceStatus.UNAVAILABLE, null, "منطقه زیر پوشش رادار نیست")
            }

            // Fetch last Tuning.RADAR_FRAMES_USED (3) frames
            val framesToFetchCount = minOf(Tuning.RADAR_FRAMES_USED, pastArr.length())
            val frames = ArrayList<RadarFrame>()
            var latestFrameIncomplete = false

            for (i in (pastArr.length() - framesToFetchCount) until pastArr.length()) {
                val fObj = pastArr.getJSONObject(i)
                val time = fObj.getLong("time")
                val path = fObj.getString("path")

                val mosaicDbz = IntArray(512 * 512) { -1 }
                var frameSuccess = true

                // 2x2 grid of tiles
                for (txOffset in 0..1) {
                    for (tyOffset in 0..1) {
                        val tx = minTileX + txOffset
                        val ty = minTileY + tyOffset
                        val bmp = getTileBitmap(cacheDir, host, path, tx, ty)
                        if (bmp != null) {
                            val pixels = IntArray(256 * 256)
                            bmp.getPixels(pixels, 0, 256, 0, 0, 256, 256)
                            val mosaicXOffset = txOffset * 256
                            val mosaicYOffset = tyOffset * 256

                            for (py in 0 until 256) {
                                val my = mosaicYOffset + py
                                for (px in 0 until 256) {
                                    val mx = mosaicXOffset + px
                                    val argb = pixels[py * 256 + px]
                                    mosaicDbz[my * 512 + mx] = UniversalBlue.dbz(argb)
                                }
                            }
                        } else {
                            frameSuccess = false
                        }
                    }
                }

                if (frameSuccess) {
                    frames.add(RadarFrame(time, mosaicDbz))
                } else {
                    if (i == pastArr.length() - 1) {
                        latestFrameIncomplete = true
                    }
                }
            }

            val usedAgeMin = if (frames.isNotEmpty()) ((nowSec - frames.last().timeEpochSec) / 60).toInt().coerceAtLeast(0) else frameAgeMin
            val fetchResult = RadarFetch(frames, usedAgeMin, coverageVerified)
            val status = if (latestFrameIncomplete || usedAgeMin > Tuning.RADAR_MAX_FRAME_AGE_MIN) SourceStatus.PARTIAL else SourceStatus.OK
            val msg = when {
                latestFrameIncomplete -> "کاشی رادار ناقص است"
                usedAgeMin > Tuning.RADAR_MAX_FRAME_AGE_MIN -> "رادار به‌روز نیست (عمر فریم: $usedAgeMin دقیقه)"
                else -> null
            }

            if (frames.isEmpty()) {
                SourceResult("rainviewer/radar", SourceStatus.UNAVAILABLE, null, "Failed to load radar tile images")
            } else {
                SourceResult("rainviewer/radar", status, fetchResult, msg)
            }
        } catch (e: Exception) {
            SourceResult("rainviewer/radar", SourceStatus.FAILED, null, e.message)
        }
    }

    private fun checkCoverage(cacheDir: File?, host: String, centerTileX: Int, centerTileY: Int, tileXDouble: Double, tileYDouble: Double): Pair<Boolean, Boolean> {
        val covFile = if (cacheDir != null) File(File(cacheDir, "radar"), "coverage.json") else null
        val nowSec = System.currentTimeMillis() / 1000

        if (covFile != null && covFile.exists()) {
            try {
                val json = JSONObject(covFile.readText())
                val timeSec = json.getLong("timeSec")
                val covOk = json.getBoolean("coverageOk")
                if (nowSec - timeSec < 86400) { // 24h cache
                    return Pair(covOk, true)
                }
            } catch (_: Exception) {}
        }

        val coverageUrl = "$host/v2/coverage/0/256/7/$centerTileX/$centerTileY/0/0_0.png"
        val covResp = Http.get(coverageUrl)
        if (covResp.isSuccess && covResp.body != null) {
            val covBmp = decodePng(covResp.body!!)
            if (covBmp != null) {
                val px = ((tileXDouble - centerTileX) * 256).toInt().coerceIn(0, 255)
                val py = ((tileYDouble - centerTileY) * 256).toInt().coerceIn(0, 255)
                var blackCount = 0
                var totalCount = 0
                for (dx in -2..2) {
                    for (dy in -2..2) {
                        val x = (px + dx).coerceIn(0, 255)
                        val y = (py + dy).coerceIn(0, 255)
                        val color = covBmp.getPixel(x, y)
                        val alpha = (color ushr 24) and 0xFF
                        val r = (color ushr 16) and 0xFF
                        val g = (color ushr 8) and 0xFF
                        val b = color and 0xFF
                        totalCount++
                        if (alpha > 200 && r < 15 && g < 15 && b < 15) {
                            blackCount++
                        }
                    }
                }
                val covOk = (blackCount.toDouble() / totalCount) <= 0.6
                if (covFile != null) {
                    try {
                        covFile.parentFile?.mkdirs()
                        val json = JSONObject().apply {
                            put("timeSec", nowSec)
                            put("coverageOk", covOk)
                        }
                        covFile.writeText(json.toString())
                    } catch (_: Exception) {}
                }
                return Pair(covOk, true)
            }
        }

        return Pair(true, false) // Failed to verify -> unverified, but radar usable
    }

    private fun getTileBitmap(cacheDir: File?, host: String, path: String, tx: Int, ty: Int): Bitmap? {
        val fileName = "${path.trim('/').replace('/', '_')}_${tx}_${ty}.png"
        val tileFile = if (cacheDir != null) File(File(cacheDir, "radar"), fileName) else null

        if (tileFile != null && tileFile.exists()) {
            try {
                val bytes = tileFile.readBytes()
                val bmp = decodePng(bytes)
                if (bmp != null) return bmp
            } catch (_: Exception) {}
        }

        val tileUrl = "$host$path/256/7/$tx/$ty/2/0_0.png"
        val tileResp = Http.get(tileUrl)
        if (tileResp.isSuccess && tileResp.body != null) {
            val bytes = tileResp.body!!
            val bmp = decodePng(bytes)
            if (bmp != null) {
                if (tileFile != null) {
                    try {
                        tileFile.parentFile?.mkdirs()
                        tileFile.writeBytes(bytes)
                    } catch (_: Exception) {}
                }
                return bmp
            }
        }
        return null
    }

    private fun cleanTileCache(cacheDir: File?) {
        if (cacheDir == null) return
        try {
            val radarDir = File(cacheDir, "radar")
            if (radarDir.exists()) {
                val nowMs = System.currentTimeMillis()
                val maxAgeMs = Tuning.RADAR_TILE_CACHE_MAX_AGE_MIN * 60 * 1000L
                radarDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name != "coverage.json" && (nowMs - file.lastModified()) > maxAgeMs) {
                        file.delete()
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun decodePng(bytes: ByteArray): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply {
                inPremultiplied = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (_: Exception) {
            null
        }
    }
}
