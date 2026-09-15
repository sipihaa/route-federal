package ru.milin.routefederal.ui

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ru.milin.routefederal.data.MapPoint
import ru.milin.routefederal.data.MapRegion
import ru.milin.routefederal.routing.City
import kotlin.math.min

private fun projected(point: MapPoint): Offset {
    val longitude = if (point.longitude < -80) point.longitude + 360 else point.longitude
    return Offset(longitude * 0.56f, -point.latitude)
}

private fun mapPath(rings: List<List<MapPoint>>) = Path().apply {
    fillType = PathFillType.EvenOdd
    for (ring in rings) {
        for ((index, point) in ring.withIndex()) {
            val position = projected(point)
            if (index == 0) moveTo(position.x, position.y) else lineTo(position.x, position.y)
        }
        close()
    }
}

@Composable
fun OfflineMap(
    polygons: List<List<MapPoint>>,
    regions: List<MapRegion>,
    cities: List<City>,
    selectedIds: List<String>,
    onCityClick: (City) -> Unit,
    modifier: Modifier = Modifier,
    showRoute: Boolean = false
) {
    var center by remember { mutableStateOf(Offset(56f, -62f)) }
    var scale by remember { mutableFloatStateOf(5f) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    val landPaths = remember(polygons) { polygons.map { mapPath(listOf(it)) } }
    val regionPaths = remember(regions) { regions.map { mapPath(it.rings) } }
    val cityPoints = remember(cities) {
        cities.mapNotNull { city ->
            val stops = city.stations.filter { it.latitude != null && it.longitude != null }
            if (stops.isEmpty()) null else city to projected(MapPoint(
                stops.map { it.longitude!! }.average().toFloat(), stops.map { it.latitude!! }.average().toFloat()))
        }
    }
    val stations = remember(cities) { cities.flatMap { it.stations }.associateBy { it.id } }
    fun position(point: Offset) = Offset(area.width / 2f, area.height / 2f) + (point - center) * scale
    fun fitCities() {
        if (cityPoints.isEmpty() || area.width == 0) return
        val points = cityPoints.map { it.second }
        val left = points.minOf { it.x }; val right = points.maxOf { it.x }
        val top = points.minOf { it.y }; val bottom = points.maxOf { it.y }
        center = Offset((left + right) / 2, (top + bottom) / 2)
        scale = min(area.width * 0.65f / (right - left).coerceAtLeast(2f),
            area.height * 0.58f / (bottom - top).coerceAtLeast(2f)).coerceIn(3f, 800f)
    }
    LaunchedEffect(cityPoints, area) { fitCities() }
    Box(modifier.clipToBounds().background(Color(0xFFDDEDF4))) {
        Canvas(Modifier.fillMaxSize().onSizeChanged { area = it }
            .semantics { contentDescription = "Офлайн-карта России с границами и названиями регионов. Выбор города нажатием на маркер." }
            .pointerInput(cityPoints, center, scale) {
                detectTapGestures { tap ->
                    val closest = cityPoints.minByOrNull { (position(it.second) - tap).getDistance() }
                    if (closest != null && (position(closest.second) - tap).getDistance() < 28.dp.toPx()) onCityClick(closest.first)
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, movement, factor, _ ->
                    val anchor = center + (centroid - Offset(area.width / 2f, area.height / 2f)) / scale
                    scale = (scale * factor).coerceIn(3f, 800f)
                    center = anchor - (centroid - Offset(area.width / 2f, area.height / 2f) + movement) / scale
                }
            }) {
            drawContext.canvas.save()
            drawContext.canvas.translate(size.width / 2f - center.x * scale, size.height / 2f - center.y * scale)
            drawContext.canvas.scale(scale, scale)
            for (path in landPaths) {
                drawPath(path, Color(0xFFF1F1EB))
                drawPath(path, Color(0xFF9DAEAD), style = Stroke(0.8.dp.toPx() / scale))
            }
            val colors = listOf(Color(0xFFE8EFE2), Color(0xFFF1EADD), Color(0xFFE6ECEF), Color(0xFFEDE7ED))
            for ((index, path) in regionPaths.withIndex()) {
                drawPath(path, colors[index % colors.size])
                drawPath(path, Color(0xFF99A693), style = Stroke(1.dp.toPx() / scale))
            }
            drawContext.canvas.restore()
            val routePoints = selectedIds.mapNotNull { stations[it] }.mapNotNull {
                if (it.longitude == null || it.latitude == null) null else position(projected(MapPoint(it.longitude.toFloat(), it.latitude.toFloat())))
            }
            if (showRoute) for (i in 0 until routePoints.lastIndex) {
                drawLine(Color(0xFF00796B), routePoints[i], routePoints[i + 1], strokeWidth = 3.dp.toPx())
            }
            val occupied = mutableListOf(RectF(0f, 0f, 145.dp.toPx(), 48.dp.toPx()),
                RectF(size.width - 60.dp.toPx(), 0f, size.width, 110.dp.toPx()))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12.dp.toPx() }
            fun label(text: String, point: Offset, city: Boolean) {
                paint.typeface = if (city) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                val width = paint.measureText(text)
                val height = 16.dp.toPx()
                val offsets = if (city) listOf(Offset(10.dp.toPx(), -8.dp.toPx()), Offset(10.dp.toPx(), 22.dp.toPx()),
                    Offset(-width - 10.dp.toPx(), -8.dp.toPx()), Offset(-width - 10.dp.toPx(), 22.dp.toPx()),
                    Offset(-width / 2, -30.dp.toPx()), Offset(-width / 2, 40.dp.toPx())) else listOf(Offset(-width / 2, 0f), Offset(-width / 2, 40.dp.toPx()), Offset(-width / 2, -35.dp.toPx()))
                for (offset in offsets) {
                    val x = point.x + offset.x; val y = point.y + offset.y
                    val box = RectF(x - 3, y - height, x + width + 3, y + 5)
                    if (box.left < 4 || box.right > size.width - 4 || box.top < 4 || box.bottom > size.height - 26.dp.toPx()) continue
                    if (occupied.any { RectF.intersects(it, box) }) continue
                    occupied.add(box)
                    if (city) drawLine(Color(0xFF718991), point,
                        Offset(point.x.coerceIn(box.left, box.right), point.y.coerceIn(box.top, box.bottom)),
                        strokeWidth = 0.8.dp.toPx())
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = 3.dp.toPx(); paint.color = android.graphics.Color.WHITE
                    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
                    paint.style = Paint.Style.FILL; paint.color = if (city) 0xFF203D49.toInt() else 0xFF657561.toInt()
                    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
                    break
                }
            }
            for ((city, point) in cityPoints) {
                val pixel = position(point)
                if (pixel.x !in 0f..size.width || pixel.y !in 0f..size.height) continue
                val selected = city.stations.any { it.id in selectedIds }
                drawCircle(Color.White, 7.dp.toPx(), pixel)
                drawCircle(if (selected) Color(0xFF00796B) else Color(0xFF3C6170), 4.dp.toPx(), pixel)
                label(city.name, pixel, true)
            }
            if (scale > 12f) for (region in regions) {
                label(region.name.replace("область", "обл.").replace("Республика", "Респ."), position(projected(region.center)), false)
            }
        }
        Row(Modifier.align(Alignment.TopStart).background(Color.White.copy(alpha = 0.9f))) {
            TextButton(onClick = { center = Offset(56f, -62f); scale = min(area.width / 96f, area.height / 45f).coerceAtLeast(3f) }) { Text("Россия") }
            TextButton(onClick = { fitCities() }) { Text("Города") }
        }
        Column(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            FilledTonalButton(onClick = { scale = (scale * 1.7f).coerceAtMost(800f) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(44.dp)) { Text("+") }
            Spacer(Modifier.height(4.dp))
            FilledTonalButton(onClick = { scale = (scale / 1.7f).coerceAtLeast(3f) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(44.dp)) { Text("−") }
        }
        Text("Natural Earth · регионы · офлайн", style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomStart).background(Color.White.copy(alpha = 0.9f)).padding(6.dp))
    }
}
