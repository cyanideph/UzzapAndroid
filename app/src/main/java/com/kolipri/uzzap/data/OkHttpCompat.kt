package com.kolipri.uzzap.data

import okhttp3.MediaType
import okhttp3.RequestBody

/** OkHttp 4.12 compatibility helpers for the app's existing call sites. */
fun String.toMediaType(): MediaType = okhttp3.MediaType.Companion.toMediaType(this)
fun String.toRequestBody(mediaType: MediaType): RequestBody = okhttp3.RequestBody.Companion.toRequestBody(this, mediaType)
