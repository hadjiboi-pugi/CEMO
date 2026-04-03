package com.example.cemo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cemo.ui.theme.PrimaryGreen
import androidx.compose.ui.res.painterResource
import com.example.cemo.R
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip


@Composable
fun AppLogo(size: Dp = 80.dp) {
    Image(
        painter = painterResource(id = R.drawable.app_icon),
        contentDescription = "App Logo",
        modifier = Modifier
            .size(size)
            // Add the clip modifier to round the edges
            .clip(RoundedCornerShape(12.dp))
    )
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun InputField(
    label: String,
    icon: ImageVector,
    isPassword: Boolean = false,
    isNumeric: Boolean = false,
    value: String,
    onValueChange: (String) -> Unit
) {
    Column {
        Text(
            label,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            leadingIcon = { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isNumeric) KeyboardType.Number else KeyboardType.Text
            ),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor  = MaterialTheme.colorScheme.surfaceVariant,
                focusedContainerColor    = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedIndicatorColor  = Color.Transparent,
                focusedIndicatorColor    = MaterialTheme.colorScheme.primary,
                unfocusedTextColor       = MaterialTheme.colorScheme.onSurface,
                focusedTextColor         = MaterialTheme.colorScheme.onSurface,
            )
        )
    }
}

@Composable
fun MetricSmallCard(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, null, tint = color)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}