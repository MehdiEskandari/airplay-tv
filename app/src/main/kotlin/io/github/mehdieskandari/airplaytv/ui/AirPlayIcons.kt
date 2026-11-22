package io.github.mehdieskandari.airplaytv.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** AirPlay video and Screen Mirroring silhouettes, as shown in Apple's connection guide. */
object AirPlayIcons {
    // AirPlay Video symbol geometry from Simple Icons (CC0), without redrawing it.
    val Video: ImageVector = ImageVector.Builder("AirPlay", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = PathParser().parsePathString("M 3.412 1.32 c -1.178 0 -1.584 .122 -2.031 .366 A 2.449 2.449 0 0 0 .365 2.7 C .122 3.148 0 3.553 0 4.771 v 9.137 c 0 1.178 .122 1.585 .365 2.031 .244 .447 .57 .772 1.016 1.016 .406 .244 .813 .365 2.031 .365 h 2.72 l 1.138 -1.34 H 3.006 c -.325 .041 -.69 -.001 -1.016 -.164 -.203 -.08 -.366 -.283 -.488 -.486 -.122 -.325 -.203 -.65 -.162 -1.016 V 4.406 c -.04 -.325 0 -.69 .162 -1.015 .081 -.203 .285 -.365 .488 -.487 .325 -.122 .65 -.204 1.016 -.164 h 17.867 c .325 -.04 .69 .002 1.016 .164 .203 .082 .364 .284 .486 .487 .122 .324 .203 .65 .162 1.015 v 9.95 c .04 .324 0 .69 -.162 1.015 -.081 .203 -.283 .365 -.486 .486 -.325 .122 -.65 .203 -1.016 .163 h -4.264 l 1.137 1.341 2.803 -.04 c 1.218 0 1.623 -.122 2.07 -.366 a 2.449 2.449 0 0 0 1.016 -1.016 c .243 -.406 .365 -.813 .365 -2.03 V 4.77 c 0 -1.218 -.122 -1.623 -.365 -2.07 a 2.449 2.449 0 0 0 -1.016 -1.015 c -.447 -.244 -.852 -.366 -2.07 -.366 H 3.412 z m 8.451 12.198 a .501 .501 0 0 0 -.37 .187 l -7.106 8.162 a .465 .465 0 0 0 -.123 .326 .47 .47 0 0 0 .488 .487 h 14.293 c .122 0 .245 -.04 .326 -.121 .203 -.163 .204 -.489 .041 -.692 l -7.107 -8.162 -.041 -.04 a .594 .594 0 0 0 -.4 -.147 z").toNodes(),
            fill = SolidColor(Color.White)
        )
    }.build()

    val Mirroring: ImageVector = ImageVector.Builder("ScreenMirroring", 28.dp, 28.dp, 28f, 28f).apply {
        path(fill = SolidColor(Color.White), pathFillType = PathFillType.EvenOdd) {
            moveTo(3f,3f); lineTo(18f,3f); curveTo(19.66f,3f,21f,4.34f,21f,6f)
            lineTo(21f,8f); lineTo(19f,8f); lineTo(19f,6f)
            curveTo(19f,5.45f,18.55f,5f,18f,5f); lineTo(3f,5f)
            curveTo(2.45f,5f,2f,5.45f,2f,6f); lineTo(2f,17f)
            curveTo(2f,17.55f,2.45f,18f,3f,18f); lineTo(6f,18f)
            lineTo(6f,20f); lineTo(3f,20f); curveTo(1.34f,20f,0f,18.66f,0f,17f)
            lineTo(0f,6f); curveTo(0f,4.34f,1.34f,3f,3f,3f); close()
            moveTo(10f,9f); lineTo(25f,9f); curveTo(26.66f,9f,28f,10.34f,28f,12f)
            lineTo(28f,23f); curveTo(28f,24.66f,26.66f,26f,25f,26f)
            lineTo(10f,26f); curveTo(8.34f,26f,7f,24.66f,7f,23f)
            lineTo(7f,12f); curveTo(7f,10.34f,8.34f,9f,10f,9f); close()
            moveTo(10f,11f); curveTo(9.45f,11f,9f,11.45f,9f,12f)
            lineTo(9f,23f); curveTo(9f,23.55f,9.45f,24f,10f,24f)
            lineTo(25f,24f); curveTo(25.55f,24f,26f,23.55f,26f,23f)
            lineTo(26f,12f); curveTo(26f,11.45f,25.55f,11f,25f,11f); close()
        }
    }.build()
}
