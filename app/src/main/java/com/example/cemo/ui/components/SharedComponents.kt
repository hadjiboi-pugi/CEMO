package com.example.cemo.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cemo.R
import com.example.cemo.ui.theme.*
import com.example.cemo.data.TranslatorManager
import com.example.cemo.data.model.WasteEntry
import java.text.SimpleDateFormat
import java.util.*

/**
 * A Text component that automatically translates its content using Google ML Kit.
 */
@Composable
fun TranslatedText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    fontStyle: androidx.compose.ui.text.font.FontStyle? = null,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    textDecoration: androidx.compose.ui.text.style.TextDecoration? = null,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE
) {
    val targetLanguage by com.example.cemo.AppState.selectedLanguage.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // 1. Try to find a local resource first for instant switching
    val resourceText = remember(text, targetLanguage) {
        if (targetLanguage == "English") return@remember text
        
        try {
            // Find resource ID by name (snake_case)
            val resName = text.lowercase().replace(" ", "_").replace("?", "").replace("!", "")
            val resId = context.resources.getIdentifier(resName, "string", context.packageName)
            if (resId != 0) {
                context.getString(resId)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    var translatedText by remember(text, targetLanguage) {
        mutableStateOf(resourceText ?: text)
    }

    // 2. If no local resource, use ML Kit as fallback
    if (resourceText == null && targetLanguage != "English") {
        LaunchedEffect(text, targetLanguage) {
            translatedText = TranslatorManager.translate(text, targetLanguage)
        }
    }

    // Preserve the source's casing. An ALL-CAPS label ("TOTAL WASTE", "VERIFIED")
    // must stay all-caps after translation — otherwise it renders as title case
    // ("Kabuuang basura") and reads like a different font/style than the English.
    val isAllCaps = remember(text) {
        val letters = text.filter { it.isLetter() }
        letters.isNotEmpty() && letters == letters.uppercase()
    }
    val displayText = if (isAllCaps) translatedText.uppercase() else translatedText

    Text(
        text = displayText,
        modifier = modifier,
        style = style,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines
    )
}

/**
 * Section header. Uppercase + tracking is the single job of [Typography.labelMedium],
 * and it is the one place in the app that shouts — which is what lets it act as a
 * divider without needing a rule or a colour of its own.
 */
@Composable
fun TranslatedSectionTitle(title: String) {
    TranslatedText(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Space.sm, bottom = Space.sm)
    )
}

/**
 * Translated variant of PremiumButton.
 */
@Composable
fun TranslatedPremiumButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = Color.White
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(Size.touchTarget),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.3f),
            disabledContentColor = contentColor.copy(alpha = 0.5f)
        ),
        contentPadding = PaddingValues(horizontal = Space.xl)
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(Size.icon))
            Spacer(Modifier.width(Space.sm))
        }
        TranslatedText(
            text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

/**
 * Translated variant of PremiumInputField.
 */
@Composable
fun TranslatedPremiumInputField(
    label: String,
    icon: ImageVector,
    isPassword: Boolean = false,
    isNumeric: Boolean = false,
    value: String,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        TranslatedText(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { TranslatedText(label, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) },
            modifier = Modifier
                .fillMaxWidth()
                .cemoSurface(shape = MaterialTheme.shapes.medium),
            shape = MaterialTheme.shapes.medium,
            leadingIcon = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isNumeric) KeyboardType.Number else KeyboardType.Text
            ),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            singleLine = true,
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor  = Color.Transparent,
                focusedContainerColor    = Color.Transparent,
                unfocusedIndicatorColor  = Color.Transparent,
                focusedIndicatorColor    = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                unfocusedTextColor       = MaterialTheme.colorScheme.onSurface,
                focusedTextColor         = MaterialTheme.colorScheme.onSurface,
            )
        )
    }
}

/**
 * Translated variant of CompactInputField.
 */
@Composable
fun TranslatedCompactInputField(
    label: String,
    icon: ImageVector,
    isPassword: Boolean = false,
    isNumeric: Boolean = false,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean = false,
    errorMessage: String? = null
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val hideText = isPassword && !passwordVisible
    val scheme = MaterialTheme.colorScheme

    // When there's an error, the field's label becomes the message — so it reads
    // inside the input (as the resting placeholder when empty, or the floating
    // label when filled) instead of on a separate line.
    val shownLabel = if (isError && !errorMessage.isNullOrBlank()) errorMessage else label

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        isError = isError,
        singleLine = true,
        label = {
            TranslatedText(
                shownLabel,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(20.dp)) },
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else null,
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                isNumeric -> KeyboardType.Number
                isPassword -> KeyboardType.Password
                else -> KeyboardType.Text
            }
        ),
        visualTransformation = if (hideText) PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor    = scheme.surfaceVariant.copy(alpha = 0.5f),
            unfocusedContainerColor  = scheme.surfaceVariant.copy(alpha = 0.5f),
            errorContainerColor      = scheme.errorContainer.copy(alpha = 0.25f),
            focusedBorderColor       = scheme.primary,
            unfocusedBorderColor     = Color.Transparent,
            errorBorderColor         = scheme.error,
            cursorColor              = scheme.primary,
            focusedTextColor         = scheme.onSurface,
            unfocusedTextColor       = scheme.onSurface,
            focusedLeadingIconColor  = scheme.primary,
            unfocusedLeadingIconColor = scheme.onSurfaceVariant,
            errorLeadingIconColor    = scheme.error,
            focusedTrailingIconColor = scheme.primary,
            unfocusedTrailingIconColor = scheme.onSurfaceVariant,
            errorTrailingIconColor   = scheme.error,
            focusedLabelColor        = scheme.primary,
            unfocusedLabelColor      = scheme.onSurfaceVariant,
            errorLabelColor          = scheme.error
        )
    )
}

/**
 * A Text component that shrinks its font size to fit the available width on a single line.
 */
@Composable
fun AutoResizingText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null
) {
    var resizedTextStyle by remember(text) { mutableStateOf(style) }
    var readyToDraw by remember(text) { mutableStateOf(false) }

    Text(
        text = text,
        color = color,
        modifier = modifier.drawWithContent {
            if (readyToDraw) drawContent()
        },
        softWrap = false,
        style = resizedTextStyle,
        textAlign = textAlign,
        onTextLayout = { result ->
            if (result.didOverflowWidth) {
                if (resizedTextStyle.fontSize > 8.sp) {
                    resizedTextStyle = resizedTextStyle.copy(
                        fontSize = resizedTextStyle.fontSize * 0.92f
                    )
                } else {
                    readyToDraw = true
                }
            } else {
                readyToDraw = true
            }
        },
        maxLines = maxLines
    )
}

@Composable
fun TranslatedMetricSmallCard(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier,
    subValue: String? = null
) {
    Box(
        modifier = modifier
            .cemoSurface(shape = MaterialTheme.shapes.large)
    ) {
        Column(modifier = Modifier.padding(Space.card)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The colour lives on the icon alone. Tinting the icon, the label
                // and the value triples the ink for one piece of information.
                Icon(icon, null, tint = color, modifier = Modifier.size(Size.iconSmall))
                Spacer(modifier = Modifier.width(Space.xs))
                AutoResizingText(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(Space.sm))
            AutoResizingText(
                value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            subValue?.let {
                Spacer(modifier = Modifier.height(Space.xs))
                AutoResizingText(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun GlassBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.cemoSurface(shape = MaterialTheme.shapes.small),
        color = containerColor.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = contentColor
        )
    }
}

@Composable
fun PremiumGlassCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.extraLarge,
    glowColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .cemoSurface(SurfaceLevel.Raised, shape)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            content()
        }
    }
}

@Composable
fun PremiumAnimatedBackground(isDark: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    
    val animX1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Reverse), label = "x1"
    )
    val animY1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(25000, easing = LinearEasing), RepeatMode.Reverse), label = "y1"
    )
    
    val particleAnim by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing), RepeatMode.Restart), label = "particles"
    )

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val color1 = if (isDark) PrimaryGreen.copy(alpha = 0.15f) else PrimaryGreen.copy(alpha = 0.1f)
            val color2 = if (isDark) Color(0xFF1A3326) else Color(0xFFD0E8DD)
            
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color1, Color.Transparent),
                    center = Offset(size.width * animX1, size.height * animY1),
                    radius = size.width * 0.8f
                )
            )
            
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color2.copy(alpha = 0.1f), Color.Transparent),
                    center = Offset(size.width * (1-animY1), size.height * (1-animX1)),
                    radius = size.width * 0.6f
                )
            )

            // Particles
            repeat(10) { i ->
                val x = (size.width * (i * 0.1f + particleAnim * 0.2f)) % size.width
                val y = (size.height * (i * 0.15f + particleAnim * 0.3f)) % size.height
                drawCircle(
                    color = PrimaryGreen.copy(alpha = 0.05f),
                    center = Offset(x, y),
                    radius = 4.dp.toPx()
                )
            }
        }
    }
}

@Composable
fun AppLogo(size: Dp = 80.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .cemoSurface(shape = MaterialTheme.shapes.large),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.app_icon),
            contentDescription = "App Logo",
            modifier = Modifier
                .size(size * 0.7f)
                .clip(MaterialTheme.shapes.small)
        )
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.SemiBold
        ),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
        modifier = Modifier.padding(top = 28.dp, bottom = 12.dp, start = 4.dp)
    )
}

/**
 * Premium high-gloss action button with elevation and deep brand colors.
 */
@Composable
fun PremiumButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = Color.White
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .shadow(
                elevation = 8.dp,
                shape = MaterialTheme.shapes.medium,
                ambientColor = containerColor.copy(alpha = 0.4f),
                spotColor = containerColor.copy(alpha = 0.4f)
            ),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.3f),
            disabledContentColor = contentColor.copy(alpha = 0.5f)
        ),
        contentPadding = PaddingValues(horizontal = 24.dp)
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
fun CompactInputField(
    label: String,
    icon: ImageVector,
    isPassword: Boolean = false,
    isNumeric: Boolean = false,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
        modifier = Modifier
            .fillMaxWidth()
            .cemoSurface(shape = MaterialTheme.shapes.medium),
        shape = MaterialTheme.shapes.medium,
        leadingIcon = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isNumeric) KeyboardType.Number else KeyboardType.Text
        ),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        singleLine = true,
        colors = TextFieldDefaults.colors(
            unfocusedContainerColor  = Color.Transparent,
            focusedContainerColor    = Color.Transparent,
            unfocusedIndicatorColor  = Color.Transparent,
            focusedIndicatorColor    = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            unfocusedTextColor       = MaterialTheme.colorScheme.onSurface,
            focusedTextColor         = MaterialTheme.colorScheme.onSurface,
            unfocusedLabelColor      = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedLabelColor        = MaterialTheme.colorScheme.primary
        )
    )
}

/**
 * Enhanced input field with better spacing and visual hierarchy.
 */
@Composable
fun PremiumInputField(
    label: String,
    icon: ImageVector,
    isPassword: Boolean = false,
    isNumeric: Boolean = false,
    value: String,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) },
            modifier = Modifier
                .fillMaxWidth()
                .cemoSurface(shape = MaterialTheme.shapes.medium),
            shape = MaterialTheme.shapes.medium,
            leadingIcon = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isNumeric) KeyboardType.Number else KeyboardType.Text
            ),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            singleLine = true,
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor  = Color.Transparent,
                focusedContainerColor    = Color.Transparent,
                unfocusedIndicatorColor  = Color.Transparent,
                focusedIndicatorColor    = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
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
    modifier: Modifier,
    subValue: String? = null
) {
    Box(
        modifier = modifier
            .cemoSurface(shape = MaterialTheme.shapes.large)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = color.copy(alpha = 0.12f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                AutoResizingText(
                    text = label.uppercase(), 
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            AutoResizingText(
                value, 
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            subValue?.let {
                Spacer(modifier = Modifier.height(4.dp))
                AutoResizingText(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = color.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun UnifiedWasteEntryCard(
    entry: WasteEntry,
    onDelete: () -> Unit
) {
    val sdf = remember<SimpleDateFormat> { SimpleDateFormat("MMM dd, HH:mm", Locale.US) }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .cemoSurface(shape = MaterialTheme.shapes.large)
    ) {
        Row(
            modifier          = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f), MaterialTheme.shapes.small)
            ) {
                Icon(
                    Icons.Default.Delete, 
                    null, 
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    sdf.format(Date(entry.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        androidx.compose.material.icons.Icons.Default.WaterDrop,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "${String.format(Locale.US, "%.0f", entry.humidity)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        androidx.compose.material.icons.Icons.Default.Thermostat,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "${String.format(Locale.US, "%.1f", entry.temperature)}°C",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                Row {
                    TranslatedText(
                        "Added",
                        style = MaterialTheme.typography.titleMedium,
                        color      = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        " ${String.format(Locale.US, "%.2f", entry.weightAdded)}kg ",
                        style = MaterialTheme.typography.titleMedium,
                        color      = MaterialTheme.colorScheme.onSurface
                    )
                    TranslatedText(
                        "waste",
                        style = MaterialTheme.typography.titleMedium,
                        color      = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
