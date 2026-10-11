/*
 * Copyright 2026 Chris Sinco (c5inco)
 * Copyright 2026 Sebastiano Poggi and contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Adapted from Prime Meridian's TicketShape
 * (https://github.com/c5inco/m3e-vibes/tree/main/apps/prime-meridian, Apache-2.0): the notches sit at a fixed
 * distance from the bottom edge (the ticket's stub) instead of a fraction of the height, so they stay on the
 * perforation whatever the card's content and font scale.
 */
package dev.sebastiano.clockblocker.opus.core.designsystem.component

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * A boarding-pass silhouette: rounded corners with a semicircular notch cut into each side where the stub tears off.
 * The notches are centred [stubHeight] above the bottom edge; with [stubHeight] 0 (or taller than the card) the
 * ticket has no notches. Draw the perforation line between them in the content.
 */
@Immutable
data class TicketShape(
    val stubHeight: Dp,
    val corner: Dp = 28.dp,
    val notch: Dp = 12.dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { corner.toPx() }
        val n = with(density) { notch.toPx() }
        val body = Path().apply { addRoundRect(RoundRect(Rect(0f, 0f, size.width, size.height), CornerRadius(r, r))) }
        val y = size.height - with(density) { stubHeight.toPx() }
        // Keep the notches clear of the rounded corners; otherwise draw a plain card.
        if (stubHeight <= 0.dp || y - n < r || y + n > size.height - r) return Outline.Generic(body)
        val holes = Path().apply {
            addOval(Rect(-n, y - n, n, y + n))
            addOval(Rect(size.width - n, y - n, size.width + n, y + n))
        }
        return Outline.Generic(Path.combine(PathOperation.Difference, body, holes))
    }
}
