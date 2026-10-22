package com.gonzalo.gamerate

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Las ediciones en la tableta (OPPO Pad Mini en horizontal). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1264dp-h840dp-xhdpi")
class EdicionesTabletaTest : EdicionesTest() {
    override val prefijo: String get() = "tab_"
}
