package ru.vladm.routefederal.ui.main

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.BoundingBox
import com.yandex.mapkit.geometry.Circle
import com.yandex.mapkit.geometry.Geometry
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.Polyline
import com.yandex.mapkit.map.LineStyle
import com.yandex.mapkit.mapview.MapView
import ru.vladm.routefederal.data.City

@Composable
fun CityMap(
    cities: List<City>,
    departureCity: City?,
    arrivalCity: City?,
    routeCities: List<City>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    MapKitFactory.getInstance().onStart()
                    mapView.onStart()
                }
                Lifecycle.Event.ON_STOP -> {
                    mapView.onStop()
                    MapKitFactory.getInstance().onStop()
                }
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                mapView.onStop()
                MapKitFactory.getInstance().onStop()
            }
        }
    }

    AndroidView(
        factory = {
            mapView.post {
                val map = mapView.mapWindow.map
                map.move(map.cameraPosition(Geometry.fromBoundingBox(boundsOf(cities))))
            }
            mapView
        },
        update = { view ->
            val objects = view.mapWindow.map.mapObjects
            objects.clear()

            if (routeCities.size >= 2) {
                objects.addPolyline(Polyline(routeCities.map { it.point() })).apply {
                    setStrokeColor(ROUTE_COLOR)
                    style = LineStyle().setStrokeWidth(4f)
                }
            }
            for (city in cities) {
                val selected = city == departureCity || city == arrivalCity
                objects.addCircle(Circle(city.point(), if (selected) 15000f else 10000f)).apply {
                    fillColor = if (selected) SELECTED_COLOR else CITY_COLOR
                    strokeColor = Color.WHITE
                    strokeWidth = 1.5f
                }
            }
        },
        modifier = modifier,
    )
}

private fun City.point() = Point(latitude, longitude)

private fun boundsOf(cities: List<City>): BoundingBox {
    val margin = 1
    return BoundingBox(
        Point(cities.minOf { it.latitude } - margin, cities.minOf { it.longitude } - margin),
        Point(cities.maxOf { it.latitude } + margin, cities.maxOf { it.longitude } + margin),
    )
}

private val CITY_COLOR = Color.rgb(90, 90, 90)
private val SELECTED_COLOR = Color.rgb(220, 50, 50)
private val ROUTE_COLOR = Color.rgb(30, 100, 220)
