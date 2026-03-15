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
