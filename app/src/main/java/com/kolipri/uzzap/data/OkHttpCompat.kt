package com.kolipri.uzzap.data

import okhttp3.MediaType
import okhttp3.RequestBody

/** OkHttp compatibility helpers. */
fun String.toMediaType(): MediaType = MediaType.Companion.toMediaType(this)
fun String.toRequestBody(mediaType: MediaType): RequestBody = RequestBody.create(mediaType, this)
