@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.opluskeycontrol.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import dev.opluskeycontrol.R
import dev.opluskeycontrol.domain.ActionKind

object OpColors {
    val Background = Color(0xFF101216)
    val Surface = Color(0xFF181C22)
    val Border = Color(0xFF303844)
    val Accent = Color(0xFF82B3FF)
    val Mint = Accent // compatibility for UI call sites; primary accent is blue
    val Success = Color(0xFF86D5B1)
    val Blue = Color(0xFF82B3FF)
    val Amber = Color(0xFFE9BD76)
    val Red = Color(0xFFFF969B)
    val Purple = Color(0xFFADBFFF)
    val Muted = Color(0xFFA8B2C1)
}

@Composable fun OPlusTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = OpColors.Mint, onPrimary = OpColors.Background, primaryContainer = Color(0xFF20334F),
        onPrimaryContainer = OpColors.Mint, secondary = OpColors.Blue, onSecondary = OpColors.Background,
        secondaryContainer = Color(0xFF253042), onSecondaryContainer = OpColors.Blue,
        background = OpColors.Background, onBackground = Color(0xFFF1F3F7),
        surface = OpColors.Surface, onSurface = Color(0xFFF1F3F7), surfaceVariant = Color(0xFF222832),
        onSurfaceVariant = OpColors.Muted, outline = OpColors.Border, error = OpColors.Red
    ), typography = Typography(
        headlineMedium = androidx.compose.ui.text.TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
        bodySmall = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
        labelLarge = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
    ), shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp)), content = content)
}

fun ActionKind.tint(): Color = if (this == ActionKind.RECORD_TOGGLE) OpColors.Red else OpColors.Accent
fun ActionKind.glyph() = when (this) {
    ActionKind.RECORD_TOGGLE -> "mic"
    ActionKind.STOCK_CAMERA_CAPTURE -> "camera"
    ActionKind.APP -> "apps"
    ActionKind.ACTIVITY -> "android"
    else -> "torch"
}

/** Small vector glyphs, drawn at device density rather than cropped from mockups. */
@Composable fun OpGlyph(name: String, color: Color = OpColors.Mint, size: Dp = 24.dp) {
    Canvas(Modifier.size(size)) {
        scale(this.size.width / 24f, this.size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(1.8f, cap = StrokeCap.Round)
            fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(color, Offset(x,y), Offset(x2,y2), 1.8f, StrokeCap.Round)
            fun rect(x: Float,y: Float,w: Float,h: Float,r: Float = 2f) = drawRoundRect(color,Offset(x,y),Size(w,h),CornerRadius(r),style=stroke)
            when (name) {
                "mic" -> { rect(9f,2f,6f,13f,3f); drawArc(color,0f,180f,false,Offset(5f,5f),Size(14f,14f),style=stroke); line(12f,19f,12f,23f); line(8f,23f,16f,23f) }
                "torch" -> { line(5f,5f,19f,5f); line(5f,5f,9f,11f); line(19f,5f,15f,11f); rect(9f,11f,6f,11f,1f); line(12f,0f,12f,2f); line(4f,0f,5f,2f); line(20f,0f,19f,2f) }
                "power" -> { drawArc(color,-55f,290f,false,Offset(3f,4f),Size(18f,18f),style=stroke); line(12f,1f,12f,11f) }
                "camera" -> { rect(2f,6f,20f,15f); drawCircle(color,4f,Offset(12f,13f),style=stroke); line(7f,6f,9f,3f); line(9f,3f,15f,3f); line(15f,3f,17f,6f) }
                "apps" -> { for (x in listOf(3f,14f)) for (y in listOf(3f,14f)) rect(x,y,7f,7f) }
                "file" -> { val p=Path().apply { moveTo(5f,2f); lineTo(14f,2f); lineTo(20f,8f); lineTo(20f,22f); lineTo(5f,22f); close() }; drawPath(p,color,style=stroke); line(14f,2f,14f,8f); line(14f,8f,20f,8f); line(9f,12f,16f,12f); line(9f,16f,16f,16f) }
                "shield" -> { val p=Path().apply { moveTo(12f,2f); lineTo(21f,6f); lineTo(20f,15f); quadraticTo(18f,21f,12f,23f); quadraticTo(6f,21f,4f,15f); lineTo(3f,6f); close() }; drawPath(p,color,style=stroke); line(8f,12f,11f,15f); line(11f,15f,16f,9f) }
                "clock" -> { drawCircle(color,9f,Offset(12f,12f),style=stroke); line(12f,6f,12f,12f); line(12f,12f,17f,15f) }
                "link" -> { rect(2f,9f,13f,7f,3f); rect(9f,3f,13f,7f,3f) }
                "pulse" -> { line(1f,13f,6f,13f); line(6f,13f,9f,4f); line(9f,4f,14f,21f); line(14f,21f,18f,11f); line(18f,11f,23f,11f) }
                "leaf" -> { val p=Path().apply { moveTo(5f,20f); cubicTo(-1f,4f,10f,0f,22f,2f); cubicTo(22f,15f,18f,23f,5f,20f) }; drawPath(p,color,style=stroke); line(3f,23f,16f,8f) }
                "plus" -> { rect(5f,2f,12f,20f); line(20f,8f,23f,6f); line(20f,12f,24f,12f); line(20f,16f,23f,18f); drawCircle(color,1f,Offset(11f,18f)) }
                "volume" -> { rect(2f,9f,5f,7f,0f); line(7f,9f,13f,4f); line(13f,4f,13f,21f); line(13f,21f,7f,16f); drawArc(color,-60f,120f,false,Offset(11f,4f),Size(12f,17f),style=stroke) }
                else -> { rect(4f,3f,16f,18f); line(8f,9f,16f,9f); line(8f,14f,16f,14f) }
            }
        }
    }
}

@Composable fun IconTile(glyph: String, tint: Color = OpColors.Mint, size: Dp = 36.dp) {
    Surface(shape=RoundedCornerShape(8.dp),color=tint.copy(alpha=.08f),modifier=Modifier.size(size)) {
        Box(contentAlignment=Alignment.Center) { OpGlyph(glyph,tint,size*.52f) }
    }
}
@Composable fun OpCard(modifier: Modifier = Modifier, selected: Boolean = false, tint: Color = OpColors.Mint, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth().widthIn(max=720.dp),color=if(selected) tint.copy(alpha=.07f) else OpColors.Surface,
        shape=RoundedCornerShape(12.dp),border=if(selected) BorderStroke(1.dp,tint.copy(alpha=.7f)) else null) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }
}
@Composable fun StatusChip(text: String, tint: Color = OpColors.Mint) {
    Surface(color=tint.copy(alpha=.10f),shape=RoundedCornerShape(6.dp)) {
        Text(text,Modifier.padding(horizontal=8.dp,vertical=4.dp),color=tint,style=MaterialTheme.typography.bodySmall)
    }
}
@Composable fun SectionTitle(text: String) { Text(text,style=MaterialTheme.typography.titleMedium,color=OpColors.Muted,modifier=Modifier.fillMaxWidth().padding(top=12.dp,bottom=4.dp)) }
@Composable fun OpRow(title: String, subtitle: String? = null, glyph: String = "file", tint: Color = OpColors.Mint, onClick: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if(onClick!=null) Modifier.clickable(onClick=onClick) else Modifier).heightIn(min=56.dp).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        OpGlyph(glyph,tint,24.dp)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(title,style=MaterialTheme.typography.titleMedium)
            if(!subtitle.isNullOrBlank()) Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=OpColors.Muted)
        }
        if(trailing!=null) trailing() else if(onClick!=null) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,null,tint=OpColors.Muted)
    }
}
@Composable fun OpAppBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(title={ Text(title,style=MaterialTheme.typography.titleLarge,maxLines=if(title==stringResource(R.string.app_name)) 1 else 2,overflow=TextOverflow.Ellipsis) },
        navigationIcon={ if(onBack!=null) IconButton(onClick=onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,stringResource(R.string.back)) } },
        actions=actions,colors=TopAppBarDefaults.topAppBarColors(containerColor=OpColors.Background))
}
