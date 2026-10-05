package dev.sebastiano.clockblocker.opus.feature.plan

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The handful of Material Symbols (Rounded, 24 dp, wght 400) the plan screen needs, as [ImageVector]s, so the
 * module doesn't pull in the deprecated icon libraries (design.md §2.4 Iconography).
 */
internal object PlanIcons {
    val ArrowBack: ImageVector by lazy {
        icon("ArrowBack", "M7.83,13l4.88,4.88q0.3,0.3 0.29,0.7t-0.3,0.71q-0.3,0.28 -0.7,0.29t-0.7,-0.29l-6.6,-6.6q-0.15,-0.15 -0.21,-0.33T4.43,12t0.06,-0.38t0.21,-0.32l6.6,-6.6q0.28,-0.28 0.69,-0.28t0.71,0.28q0.3,0.3 0.3,0.71t-0.3,0.71L7.83,11H19q0.43,0 0.71,0.29T20,12t-0.29,0.71T19,13z")
    }
    val MoreVert: ImageVector by lazy {
        icon("MoreVert", "M12,20q-0.82,0 -1.41,-0.59T10,18t0.59,-1.41T12,16t1.41,0.59T14,18t-0.59,1.41T12,20m0,-6q-0.82,0 -1.41,-0.59T10,12t0.59,-1.41T12,10t1.41,0.59T14,12t-0.59,1.41T12,14m0,-6q-0.82,0 -1.41,-0.59T10,6t0.59,-1.41T12,4t1.41,0.59T14,6t-0.59,1.41T12,8")
    }
    val Check: ImageVector by lazy {
        icon("Check", "M9.55,15.15l8.48,-8.48q0.3,-0.3 0.7,-0.3t0.7,0.3t0.3,0.71t-0.3,0.71l-9.18,9.2q-0.3,0.3 -0.7,0.3t-0.7,-0.3l-4.3,-4.3q-0.3,-0.3 -0.29,-0.71t0.31,-0.71t0.71,-0.3t0.71,0.3z")
    }
    val ArrowDropDown: ImageVector by lazy {
        icon("ArrowDropDown", "M11.48,14.48l-2.6,-2.6q-0.08,-0.08 -0.13,-0.18T8.7,11.5q0,-0.2 0.14,-0.35T9.2,11h5.6q0.23,0 0.36,0.15t0.14,0.35q0,0.05 -0.15,0.35l-2.6,2.6q-0.13,0.13 -0.25,0.18t-0.3,0.05t-0.3,-0.05t-0.22,-0.15")
    }
    val MyLocation: ImageVector by lazy {
        icon("MyLocation", "M11,22v-1.05q-3.12,-0.35 -5.36,-2.59T3.05,13H2q-0.43,0 -0.71,-0.29T1,12t0.29,-0.71T2,11h1.05q0.35,-3.12 2.59,-5.36T11,3.05V2q0,-0.43 0.29,-0.71T12,1t0.71,0.29T13,2v1.05q3.12,0.35 5.36,2.59T20.95,11H22q0.43,0 0.71,0.29T23,12t-0.29,0.71T22,13h-1.05q-0.35,3.13 -2.59,5.36T13,20.95V22q0,0.43 -0.29,0.71T12,23t-0.71,-0.29T11,22m1,-3q2.9,0 4.95,-2.05T19,12t-2.05,-4.95T12,5T7.05,7.05T5,12t2.05,4.95T12,19m0,-3q-1.65,0 -2.82,-1.17T8,12t1.18,-2.82T12,8t2.83,1.18T16,12t-1.17,2.83T12,16")
    }
    val CalendarToday: ImageVector by lazy {
        icon("CalendarToday", "M5,22q-0.82,0 -1.41,-0.59T3,20V6q0,-0.82 0.59,-1.41T5,4h1V3q0,-0.43 0.29,-0.71T7,2t0.71,0.29T8,3v1h8V3q0,-0.43 0.29,-0.71T17,2t0.71,0.29T18,3v1h1q0.83,0 1.41,0.59T21,6v14q0,0.83 -0.59,1.41T19,22zM5,20h14V10H5z")
    }
    val Help: ImageVector by lazy {
        icon("Help", "M11.95,18q0.53,0 0.89,-0.36t0.36,-0.89t-0.36,-0.89t-0.89,-0.36t-0.89,0.36t-0.36,0.89t0.36,0.89t0.89,0.36M12,22q-2.07,0 -3.9,-0.79t-3.17,-2.14t-2.14,-3.17T2,12t0.79,-3.9t2.14,-3.17T8.1,2.79T12,2t3.9,0.79t3.17,2.14t2.14,3.17T22,12t-0.79,3.9t-2.14,3.17t-3.17,2.14T12,22m0,-2q3.35,0 5.68,-2.32T20,12t-2.32,-5.68T12,4T6.33,6.33T4,12t2.33,5.68T12,20m0.1,-12.3q0.63,0 1.09,0.4t0.46,1q0,0.55 -0.34,0.98t-0.76,0.8q-0.57,0.5 -1.01,1.1t-0.44,1.35q0,0.35 0.26,0.59t0.61,0.24q0.38,0 0.64,-0.25t0.34,-0.63q0.1,-0.53 0.45,-0.94t0.75,-0.79q0.58,-0.55 0.99,-1.2t0.41,-1.45q0,-1.27 -1.04,-2.09T12.1,6q-0.95,0 -1.81,0.4T8.98,7.63q-0.17,0.3 -0.11,0.64t0.34,0.51q0.35,0.2 0.73,0.13t0.63,-0.43q0.27,-0.37 0.68,-0.57t0.86,-0.2")
    }
    val NightsStay: ImageVector by lazy {
        icon("NightsStay", "M12.1,22q-2.1,0 -3.94,-0.8t-3.2,-2.17T2.8,15.83T2,11.9q0,-3.4 2.06,-6.05T9.4,2.3q0.5,-0.13 0.86,0.11t0.49,0.64t0.06,0.8t-0.39,0.65q-0.6,0.6 -0.94,1.39T9.15,7.5q0,1.8 1.26,3.08t3.09,1.27q0.78,0 1.48,-0.24t1.27,-0.69q0.33,-0.25 0.72,-0.31t0.72,0.09t0.55,0.46t0.11,0.84q-0.65,2.8 -2.92,4.4T12.1,22")
    }

    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
            .build()
}
