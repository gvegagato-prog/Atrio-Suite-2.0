package com.gonzalo.gamerate

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Las mismas capturas en la OPPO Pad Mini: 8,8", 2520 × 1680 px (3:2) a 344 ppi, que Android ve como
 * 1260 × 840 dp (densidad 2,0). En horizontal y en vertical.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1260dp-h840dp-land-xhdpi")
class CapturasOppoHorizontalTest : Capturas6Test() {
    override val prefijo: String get() = "oppo_h_"
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w840dp-h1260dp-port-xhdpi")
class CapturasOppoVerticalTest : Capturas6Test() {
    override val prefijo: String get() = "oppo_v_"
}
