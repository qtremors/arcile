package dev.qtremors.arcile.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp), // For standard large cards
    extraLarge = RoundedCornerShape(28.dp) // Optimized squircle approximation
)

// Custom expressive shape variants for unique components
val ExpressiveCutShape = CutCornerShape(topStart = 24.dp, bottomEnd = 24.dp)
val ExpressiveAsymmetricShape = RoundedCornerShape(topStart = 32.dp, bottomStart = 8.dp, topEnd = 32.dp, bottomEnd = 32.dp)

// Semantic Shapes
val Shapes.fileRow: androidx.compose.ui.graphics.Shape
    get() = RoundedCornerShape(12.dp)

val Shapes.fileGridCard: androidx.compose.ui.graphics.Shape
    get() = RoundedCornerShape(16.dp)

val Shapes.toolbarPill: androidx.compose.ui.graphics.Shape
    get() = RoundedCornerShape(50) // Pill shape

val Shapes.menuGroupFirst: androidx.compose.ui.graphics.Shape
    get() = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp)

val Shapes.menuGroupMiddle: androidx.compose.ui.graphics.Shape
    get() = RoundedCornerShape(4.dp)

val Shapes.menuGroupLast: androidx.compose.ui.graphics.Shape
    get() = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)

val Shapes.menuGroupSingle: androidx.compose.ui.graphics.Shape
    get() = RoundedCornerShape(16.dp)

val Shapes.sheet: androidx.compose.ui.graphics.Shape
    get() = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

val Shapes.dialog: androidx.compose.ui.graphics.Shape
    get() = RoundedCornerShape(28.dp)

val Shapes.storageCard: androidx.compose.ui.graphics.Shape
    get() = RoundedCornerShape(24.dp)
