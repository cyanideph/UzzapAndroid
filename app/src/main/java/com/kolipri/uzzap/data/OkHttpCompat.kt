package com.kolipri.uzzap.data

import okhttp3.MediaType
import okhttp3.RequestBody

/** OkHttp 3/4 compatibility helpers used by the compact Supabase clients. */
fun String.toMediaType(): MediaType = MediaType.parse(this)!!
fun String.toRequestBody(mediaType: MediaType): RequestBody = RequestBody.create(mediaType, this)
