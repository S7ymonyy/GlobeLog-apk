package pl.getdatrip.ui

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.util.MapTileIndex

internal const val SATELLITE_MAX_ZOOM = 12
internal const val SATELLITE_ATTRIBUTION = "NASA EOSDIS GIBS / USGS"
internal const val SATELLITE_CREDIT_URL = "https://nasa-gibs.github.io/gibs-api-docs/#data-use-guidance-and-acknowledgements"

internal object SatelliteTileSource : OnlineTileSourceBase(
    "NasaGibsSatelliteV1",
    0,
    SATELLITE_MAX_ZOOM,
    256,
    ".jpeg",
    arrayOf("https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/"),
    SATELLITE_ATTRIBUTION,
    TileSourcePolicy(
        2,
        TileSourcePolicy.FLAG_NO_BULK or TileSourcePolicy.FLAG_NO_PREVENTIVE or
            TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL,
    ),
) {
    override fun getTileURLString(tileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(tileIndex)
        val tileColumn = MapTileIndex.getX(tileIndex)
        val tileRow = MapTileIndex.getY(tileIndex)
        val layer = if (zoom <= 8) {
            "BlueMarble_NextGeneration/default/GoogleMapsCompatible_Level8"
        } else {
            "Landsat_WELD_CorrectedReflectance_TrueColor_Global_Annual/default/2000-12-01/GoogleMapsCompatible_Level12"
        }
        return "$baseUrl$layer/$zoom/$tileRow/$tileColumn.jpeg"
    }
}
