package com.kolipri.uzzap.data

import okhttp3.MediaType
import okhttp3.RequestBody

/** OkHttp compatibility helpers for the app's existing call sites. */
fun String.toMediaType(): MediaType = MediaType.parse(this)!!
fun String.toRequestBody(mediaType: MediaType): RequestBody = RequestBody.create(mediaType, this)
