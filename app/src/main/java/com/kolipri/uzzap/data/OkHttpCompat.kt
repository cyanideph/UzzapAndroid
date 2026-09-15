package com.kolipri.uzzap.data

import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaType as okhttpToMediaType
import okhttp3.RequestBody.Companion.toRequestBody as okhttpToRequestBody

/** OkHttp 4.12 compatibility helpers for the app's existing call sites. */
fun String.toMediaType(): MediaType = this.okhttpToMediaType()
fun String.toRequestBody(mediaType: MediaType): RequestBody = this.okhttpToRequestBody(mediaType)
