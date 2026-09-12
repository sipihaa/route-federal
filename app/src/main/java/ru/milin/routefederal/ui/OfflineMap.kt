package ru.milin.routefederal.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ru.milin.routefederal.data.MapPoint
import ru.milin.routefederal.routing.Station
import kotlin.math.min

@Composable
fun OfflineMap(
    polygons: List<List<MapPoint>>,
    stations: List<Station>,
    selectedIds: List<String>,
    onStationClick: (Station) -> Unit,
    modifier: Modifier = Modifier,
    showRoute: Boolean = false
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    val mapStations = remember(stations) { stations.filter { it.latitude != null && it.longitude != null } }
    val paths = remember(polygons) {
        polygons.map { ring ->
            Path().apply {
                for ((index, point) in ring.withIndex()) {
                    val x = (point.longitude - 100f) * 0.5f
                    val y = 62f - point.latitude
                    if (index == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
        }
    }
    fun position(station: Station): Offset {
        var lon = station.longitude!!.toFloat()
        if (lon < -80) lon += 360
        val scale = min(area.width / 90f, area.height / 52f) * zoom
        return Offset(area.width / 2f + (lon - 100f) * 0.5f * scale, area.height / 2f + (62f - station.latitude!!.toFloat()) * scale) + pan
    }
    Box(modifier.clipToBounds().background(Color(0xFFE4EDF2))) {
        Canvas(
            Modifier.fillMaxSize().onSizeChanged { area = it }
                .semantics { contentDescription = "Обзорная офлайн-карта. Станции также доступны в поиске по названию." }
                .pointerInput(mapStations, zoom, pan) {
                    detectTapGestures { tap ->
                        val closest = mapStations.minByOrNull { (position(it) - tap).getDistance() }
                        if (closest != null && (position(closest) - tap).getDistance() < 28.dp.toPx()) onStationClick(closest)
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, movement, factor, _ ->
                        zoom = (zoom * factor).coerceIn(1f, 64f)
                        pan += movement
                    }
                }
        ) {
            val scale = min(size.width / 90f, size.height / 52f) * zoom
            drawContext.canvas.save()
            drawContext.canvas.translate(size.width / 2f + pan.x, size.height / 2f + pan.y)
            drawContext.canvas.scale(scale, scale)
            for (path in paths) {
                drawPath(path, Color(0xFFF8F7F1))
                drawPath(path, Color(0xFFB9C6C8), style = Stroke(0.6f / scale))
            }
            drawContext.canvas.restore()
            val selected = selectedIds.mapNotNull { id -> mapStations.find { it.id == id } }
            if (showRoute) for (index in 0 until selected.lastIndex) {
                drawLine(Color(0xFF006A62), position(selected[index]), position(selected[index + 1]), strokeWidth = 3.dp.toPx())
            }
            for (station in mapStations) {
                val center = position(station)
                if (center.x !in 0f..size.width || center.y !in 0f..size.height) continue
                val isSelected = station.id in selectedIds
                drawCircle(Color.White, if (isSelected) 8.dp.toPx() else 5.dp.toPx(), center)
                drawCircle(if (isSelected) Color(0xFF006A62) else Color(0xFF5D7580), if (isSelected) 5.dp.toPx() else 3.dp.toPx(), center)
            }
        }
        Column(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            FilledTonalButton(onClick = { zoom = (zoom * 1.7f).coerceAtMost(64f) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(44.dp)) { Text("+") }
            Spacer(Modifier.height(4.dp))
            FilledTonalButton(onClick = { zoom = (zoom / 1.7f).coerceAtLeast(1f) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(44.dp)) { Text("−") }
        }
        Column(Modifier.align(Alignment.TopStart)) {
            TextButton(onClick = { zoom = 1f; pan = Offset.Zero }) { Text("Вся карта") }
            if (selectedIds.isNotEmpty()) TextButton(onClick = {
                val selected = mapStations.filter { it.id in selectedIds }
                if (selected.isNotEmpty() && area.width > 0 && area.height > 0) {
                    val longitudes = selected.map { if (it.longitude!! < -80) it.longitude + 360 else it.longitude }
                    val centerLon = (longitudes.min() + longitudes.max()) / 2
                    val centerLat = (selected.minOf { it.latitude!! } + selected.maxOf { it.latitude!! }) / 2
                    val width = ((longitudes.max() - longitudes.min()) * 0.5).coerceAtLeast(2.0)
                    val height = (selected.maxOf { it.latitude!! } - selected.minOf { it.latitude!! }).coerceAtLeast(2.0)
                    val base = min(area.width / 90f, area.height / 52f)
                    zoom = (min(area.width * 0.55 / width, area.height * 0.55 / height) / base).toFloat().coerceIn(1f, 64f)
                    pan = Offset(((100 - centerLon) * 0.5 * base * zoom).toFloat(), ((centerLat - 62) * base * zoom).toFloat())
                }
            }) { Text("К выбранным точкам") }
        }
        Text("Natural Earth · обзорная карта", style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomStart).background(Color.White.copy(alpha = 0.9f)).padding(6.dp))
    }
}
