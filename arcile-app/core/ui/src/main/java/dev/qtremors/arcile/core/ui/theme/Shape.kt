package dev.qtremors.arcile.core.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun expressiveSegmentedShapes(index: Int, count: Int): ListItemShapes {
    val shape = when {
        count <= 1 -> RoundedCornerShape(28.dp)
        index == 0 -> RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
        index == count - 1 -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 28.dp, bottomEnd = 28.dp)
        else -> RoundedCornerShape(4.dp)
    }
    return ListItemDefaults.shapes(
        shape = shape,
        selectedShape = shape
    )
}

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
    get() = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 6.dp)

val Shapes.menuGroupMiddle: androidx.compose.ui.graphics.Shape
    get() = RoundedCornerShape(6.dp)

val Shapes.menuGroupLast: androidx.compose.ui.graphics.Shape
    get() = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 20.dp, bottomEnd = 20.dp)

val Shapes.menuGroupSingle: androidx.compose.ui.graphics.Shape
    get() = RoundedCornerShape(20.dp)

val Shapes.sheet: androidx.compose.ui.graphics.Shape
    get() = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

val Shapes.dialog: androidx.compose.ui.graphics.Shape
    get() = RoundedCornerShape(28.dp)

val Shapes.storageCard: androidx.compose.ui.graphics.Shape
    get() = RoundedCornerShape(24.dp)
