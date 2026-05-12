package com.mahavtaar.csvkeyboard.data.csv

import kotlinx.coroutines.flow.MutableSharedFlow

object SessionBus {
    val rowChanged = MutableSharedFlow<Int>(replay = 1, extraBufferCapacity = 1)
}
