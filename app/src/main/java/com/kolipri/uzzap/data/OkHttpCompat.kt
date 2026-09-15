package com.kolipri.uzzap.data

import okhttp3.MediaType

/** Package-local compatibility helper for the compact Supabase clients. */
fun String.toMediaType(): MediaType = MediaType.parse(this)!!
