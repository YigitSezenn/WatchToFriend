package com.watch.watchtofriend.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watch.watchtofriend.R
import com.watch.watchtofriend.ui.theme.AvatarColors
import com.watch.watchtofriend.ui.theme.BrandGradientEnd
import com.watch.watchtofriend.ui.theme.BrandGradientMid
import com.watch.watchtofriend.ui.theme.BrandGradientStart
import kotlin.math.absoluteValue

/** Marka degradesi (başlık/aksanlar için). */
val brandGradient: Brush
    get() = Brush.linearGradient(
        listOf(BrandGradientStart, BrandGradientMid, BrandGradientEnd)
    )

/** Uygulama logosu (yuvarlatılmış). */
@Composable
fun BrandLogo(size: Int = 96, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.brand_logo),
        contentDescription = "Watch with Friends",
        modifier = modifier
            .size(size.dp)
            .clip(MaterialTheme.shapes.large)
    )
}

/** Login için: arkasında degrade parıltı + gölgeli yükseltilmiş logo. */
@Composable
fun BrandLogoHero(size: Int = 112) {
    val glow = size * 1.9f
    Box(contentAlignment = Alignment.Center) {
        // Yumuşak degrade parıltı halesi
        Box(
            modifier = Modifier
                .size(glow.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            BrandGradientMid.copy(alpha = 0.45f),
                            BrandGradientEnd.copy(alpha = 0.18f),
                            Color.Transparent
                        ),
                        center = Offset.Unspecified
                    ),
                    shape = CircleShape
                )
        )
        // Yükseltilmiş, yuvarlatılmış logo
        Image(
            painter = painterResource(R.drawable.brand_logo),
            contentDescription = "Watch with Friends",
            modifier = Modifier
                .size(size.dp)
                .shadow(
                    elevation = 28.dp,
                    shape = RoundedCornerShape((size * 0.20f).dp),
                    ambientColor = BrandGradientStart,
                    spotColor = BrandGradientEnd
                )
                .clip(RoundedCornerShape((size * 0.20f).dp))
        )
    }
}

/** Degrade dolgulu, arkasında parıltı olan ikon rozeti (Create/Join başlıkları için). */
@Composable
fun GlowIconBadge(icon: ImageVector, size: Int = 84) {
    val glow = size * 2.0f
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(glow.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            BrandGradientMid.copy(alpha = 0.40f),
                            BrandGradientEnd.copy(alpha = 0.14f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(size.dp)
                .shadow(
                    elevation = 20.dp,
                    shape = CircleShape,
                    ambientColor = BrandGradientStart,
                    spotColor = BrandGradientEnd
                )
                .clip(CircleShape)
                .background(brandGradient),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size((size * 0.48f).dp)
            )
        }
    }
}

/** base64 JPEG'i bir kez çözüp ImageBitmap'e döndürür (boşsa null). */
@Composable
fun rememberPhoto(photoBase64: String?): androidx.compose.ui.graphics.ImageBitmap? {
    return androidx.compose.runtime.remember(photoBase64) {
        if (photoBase64.isNullOrBlank()) null
        else try {
            val bytes = android.util.Base64.decode(photoBase64, android.util.Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
}

/** İsimden tutarlı renkli baş-harf avatarı; fotoğraf varsa onu gösterir. */
@Composable
fun InitialAvatar(name: String, size: Int = 44, photoBase64: String? = null) {
    val photo = rememberPhoto(photoBase64)
    if (photo != null) {
        Image(
            bitmap = photo,
            contentDescription = name,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
        )
        return
    }
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val color = AvatarColors[(name.hashCode().absoluteValue) % AvatarColors.size]
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(color, color.copy(alpha = 0.75f))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initial,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size * 0.42f).sp
        )
    }
}

/** İkon + başlık + açıklamalı boş durum. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp)
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/** Video bağlantısı türüne göre ikon (YouTube vs. web). */
fun videoIconFor(url: String): ImageVector {
    val u = url.lowercase()
    return if (u.contains("youtu")) Icons.Filled.SmartDisplay else Icons.Filled.Language
}
