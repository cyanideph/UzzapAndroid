package com.kolipri.uzzap.ui

import android.widget.EditText

/** Compatibility property for Kotlin source using selectAllOnFocus as a property. */
var EditText.selectAllOnFocus: Boolean
    get() = false
    set(value) { setSelectAllOnFocus(value) }
