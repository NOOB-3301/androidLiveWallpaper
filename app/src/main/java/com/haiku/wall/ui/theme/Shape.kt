package com.haiku.wall.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val StampShape = RoundedCornerShape(3.dp)
val ControlShape = RoundedCornerShape(14.dp)
val CardShape = RoundedCornerShape(24.dp)
val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

val HaikuShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(6.dp),
    medium = ControlShape,
    large = RoundedCornerShape(16.dp),
    extraLarge = CardShape,
)
