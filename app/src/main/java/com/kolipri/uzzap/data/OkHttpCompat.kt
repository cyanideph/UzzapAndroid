package com.kolipri.uzzap.data

import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.toMediaType as okhttpToMediaType

/** OkHttp compatibility helpers for the app's existing call sites. */
fun String.toMediaType(): MediaType = this.okhttpToMediaType()
fun String.toRequestBody(mediaType: MediaType): RequestBody = RequestBody.create(mediaType, this)
