package com.github.kr328.clash.core.util

import com.github.kr328.clash.core.model.Traffic
import me.saket.bytesize.binaryBytes

fun Traffic.trafficUpload(): String = scaledToApproxBytes(uploadScaled).binaryBytes.toString()

fun Traffic.trafficDownload(): String = scaledToApproxBytes(downloadScaled).binaryBytes.toString()

fun Traffic.trafficTotal(): String =
  centiBytesToApproxBytes(
      normalizeScaledToCentiBytes(uploadScaled) + normalizeScaledToCentiBytes(downloadScaled),
    )
    .binaryBytes
    .toString()

private fun normalizeScaledToCentiBytes(scaled: Long): Long {
  return if (scaled <= 1024L) scaled * 100 else scaled
}

private fun scaledToApproxBytes(scaled: Long): Long = if (scaled <= 1024L) scaled else scaled / 100

private fun centiBytesToApproxBytes(centiBytes: Long): Long = centiBytes / 100
