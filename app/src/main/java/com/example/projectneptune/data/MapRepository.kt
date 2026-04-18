package com.example.projectneptune.data

import android.content.Context
import android.util.JsonReader
import android.util.Log
import com.arcgismaps.geometry.*
import com.arcgismaps.mapping.ArcGISMap
import com.arcgismaps.mapping.BasemapStyle
import com.arcgismaps.mapping.Basemap
import com.arcgismaps.mapping.layers.ArcGISVectorTiledLayer
import com.arcgismaps.tasks.exportvectortiles.ExportVectorTilesTask
import com.arcgismaps.tasks.Job as ArcGISJob
import com.example.projectneptune.R
import java.io.File
import com.example.projectneptune.isInternetAvailable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

class MapRepository(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val mapDao = db.mapDao()

    private val _catchEntries = MutableStateFlow<List<CatchEntry>>(emptyList())
    val catchEntries: StateFlow<List<CatchEntry>> = _catchEntries.asStateFlow()

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _basemapDownloadProgress = MutableStateFlow(-1)
    val basemapDownloadProgress: StateFlow<Int> = _basemapDownloadProgress.asStateFlow()

    private var cachedBasemapUrl: String? = null
    private var basemapDownloadJob: Job? = null
    private var activeArcGISJob: ArcGISJob<*>? = null

    private val _features = MutableStateFlow<List<Layer20Feature>>(emptyList())
    val features: StateFlow<List<Layer20Feature>> = _features.asStateFlow()

    private val _stations = MutableStateFlow<List<Station>>(emptyList())
    val stations: StateFlow<List<Station>> = _stations.asStateFlow()

    private val _staticBoundaries = MutableStateFlow<List<StaticBoundary>>(emptyList())
    val staticBoundaries: StateFlow<List<StaticBoundary>> = _staticBoundaries.asStateFlow()

    private val _selectedSpecies = MutableStateFlow<Set<String>>(emptySet())
    val selectedSpecies: StateFlow<Set<String>> = _selectedSpecies.asStateFlow()

    private val requiredFields = listOf(
        "OBJECTID", "PO_NUM", "PLACE_NAME_EN", "PUBLIC_NOTICE_URL", "REASON", "ENFORCE_DATE_EN",
        "ALL_BIVALVES", "BUTTER_CLAM", "GEODUCK_CLAM", "HORSE_CLAM", "LITTLENECK_CLAM", "MANILA_CLAM",
        "NUTTALLS_COCKLE", "PACIFIC_RAZOR_CLAM", "SOFTSHELL_CLAM", "VARNISH_CLAM", "BLUE_MUSSEL",
        "CALIFORNIA_MUSSEL", "OLYMPIA_OYSTER", "PACIFIC_OYSTER", "PINK_SCALLOP",
        "PURPLE_HINGE_ROCK_SCALLOP", "SPINY_SCALLOP", "WEATHERVANE_SCALLOP"
    ).joinToString(",")

    fun updateSpeciesFilter(species: Set<String>) {
        _selectedSpecies.value = species
    }

    suspend fun refreshLocalFeatures() {
        val startTime = System.currentTimeMillis()
        _features.value = mapDao.getAllLayer20Features()
        _stations.value = mapDao.getAllStations()
        _staticBoundaries.value = mapDao.getAllStaticBoundaries()
        _catchEntries.value = mapDao.getAllCatchEntries()
        Log.d("MapPerformance", "Loaded local data in ${System.currentTimeMillis() - startTime}ms")
    }

    private suspend fun isDataStale(): Boolean {
        val lastUpdatedStr = mapDao.getMetadata("last_updated") ?: return true
        return try {
            val lastUpdatedDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(lastUpdatedStr) ?: return true
            System.currentTimeMillis() - lastUpdatedDate.time > 60 * 60 * 1000L
        } catch (e: Exception) { true }
    }

    suspend fun getTideDownloadDays(): Int {
        return mapDao.getMetadata("tide_download_days")?.toIntOrNull() ?: 7
    }

    suspend fun updateTideDownloadDays(days: Int) {
        mapDao.insertMetadata(AppMetadata("tide_download_days", days.toString()))
    }

    suspend fun isTtsEnabled(): Boolean {
        return mapDao.getMetadata("tts_enabled")?.toBoolean() ?: true
    }

    suspend fun updateTtsEnabled(enabled: Boolean) {
        mapDao.insertMetadata(AppMetadata("tts_enabled", enabled.toString()))
    }

    suspend fun getCatchLimit(zoneId: String): CatchLimit? {
        return mapDao.getCatchLimit(zoneId)
    }

    suspend fun fetchAndCacheLayer20Data(force: Boolean = false) {
        if (_isSyncing.value) return
        
        // Always try to bootstrap local assets first
        initializeStaticData()
        
        if (_features.value.isEmpty()) refreshLocalFeatures()
        if (!force && !isDataStale()) return

        withContext(Dispatchers.IO) {
            if (!isInternetAvailable(context)) return@withContext

            try {
                _isSyncing.value = true
                val syncStartTime = System.currentTimeMillis()

                // 0. Sync Basemap (Offline VTPK)
                try {
                    val vtpkFile = java.io.File(context.filesDir, "basemap.vtpk")
                    if (!vtpkFile.exists() && isInternetAvailable(context)) {
                        Log.d("MapPerformance", "Downloading offline basemap...")
                        // Placeholder for a VTPK download if needed, 
                        // but since we found a VectorTileServer URL, we can use it directly in MapDestination.
                        // However, if we want TRUE offline, we'd need to use ExportVectorTilesTask.
                    }
                } catch (e: Exception) {
                    Log.e("MapPerformance", "Basemap sync failed: ${e.message}")
                }
                
                // 1. Sync Stations First
                val stationList = fetchAndCacheStations()
                
                // 2. Sync Layer 20 Features
                val whereClause = URLEncoder.encode("DFO_REGION = 4", "UTF-8")
                val baseUrl = "https://egisp.dfo-mpo.gc.ca/arcgis/rest/services/CSSP/CSSP_Base_Public/MapServer/20/query"
                val idResponse = URL("$baseUrl?where=$whereClause&returnIdsOnly=true&f=json").readText()
                val idsArray = JSONObject(idResponse).optJSONArray("objectIds")
                
                if (idsArray != null) {
                    val allIds = mutableListOf<Int>()
                    for (i in 0 until idsArray.length()) allIds.add(idsArray.getInt(i))
                    
                    // Increased chunk size and parallelized requests for much faster sync.
                    // ArcGIS REST API typically handles 100+ IDs easily in a single query.
                    val chunks = allIds.chunked(100)
                    chunks.chunked(4).forEach { parallelBatch ->
                        coroutineScope {
                            parallelBatch.map { chunk ->
                                async(Dispatchers.IO) {
                                    try {
                                        val idsString = chunk.joinToString(",")
                                        val chunkUrl = "$baseUrl?objectIds=$idsString&outFields=$requiredFields&f=json&returnGeometry=true&outSR=4326&maxAllowableOffset=0.0001&geometryPrecision=6"
                                        val chunkFeatures = parseFeaturesStreaming(chunkUrl, stationList)
                                        if (chunkFeatures.isNotEmpty()) mapDao.insertAllLayer20Features(chunkFeatures)
                                    } catch (e: Exception) {
                                        Log.e("MapPerformance", "Chunk failed: ${e.message}")
                                    }
                                }
                            }.awaitAll()
                        }
                    }
                    mapDao.deleteFeaturesNotInList(allIds)
                }

                // 3. Sync Tide Data for relevant stations only
                // This is a major optimization: only download tide data for stations 
                // that are actually associated with at least one fishery area.
                val days = getTideDownloadDays()
                val neededStationIds = mapDao.getUniqueClosestStationIds().toSet()
                val relevantStations = stationList.filter { it.id in neededStationIds }
                Log.d("MapPerformance", "Syncing tides for ${relevantStations.size} relevant stations out of ${stationList.size}")
                syncTideData(relevantStations, days)

                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                mapDao.insertMetadata(AppMetadata("last_updated", timestamp))
                refreshLocalFeatures()
                Log.d("MapPerformance", "Full sync done in ${System.currentTimeMillis() - syncStartTime}ms")
            } catch (e: Exception) {
                Log.e("MapPerformance", "Sync error: ${e.message}")
            } finally {
                _isSyncing.value = false
            }
        }
    }

    private suspend fun initializeStaticData() {

        val existingBoundaries = mapDao.getAllStaticBoundaries()
        // Initialize or Update Static Boundaries (e.g. Pacific Rim)
        // If empty OR if the existing Pacific Rim boundary is missing the correct spatial reference
        val needsUpdate = existingBoundaries.isEmpty() || 
                existingBoundaries.any { it.id == "PRIM" && !it.geometryJson.contains("3978") }

        if (needsUpdate) {
            try {
                val jsonString = context.assets.open("pacific-rim-boundary.json").bufferedReader().use { it.readText() }
                val root = JSONObject(jsonString)
                val feature = root.getJSONObject("feature")
                val attributes = feature.getJSONObject("attributes")
                val geometry = feature.getJSONObject("geometry")
                
                // Inject Canada Atlas Lambert (3978) WKID. 
                // The coordinates in the JSON asset are in this projection.
                if (!geometry.has("spatialReference")) {
                    geometry.put("spatialReference", JSONObject().put("wkid", 3978))
                }
                
                val boundary = StaticBoundary(
                    id = attributes.getString("adminAreaId"),
                    name = attributes.getString("adminAreaNameEng"),
                    geometryJson = geometry.toString()
                )
                mapDao.insertStaticBoundary(boundary)
                Log.d("MapRepository", "Initialized/Updated Pacific Rim boundary with WKID 3978")
            } catch (e: Exception) {
                Log.e("MapRepository", "Failed to initialize static boundary: ${e.message}")
            }
        }

        // Initialize Catch Limits
        if (mapDao.getCatchLimitCount() == 0) {
            try {
                val jsonString = context.assets.open("catch_limits.json").bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val limit = CatchLimit(
                        zoneId = obj.getString("zoneId"),
                        butterClam = obj.optInt("butterClam", 0),
                        geoduck = obj.optInt("geoduck", 0),
                        horseClam = obj.optInt("horseClam", 0),
                        littleneckClam = obj.optInt("littleneckClam", 0),
                        manilaClam = obj.optInt("manilaClam", 0),
                        nuttallsCockle = obj.optInt("nuttallsCockle", 0),
                        pacificRazorClam = obj.optInt("pacificRazorClam", 0),
                        softshellClam = obj.optInt("softshellClam", 0),
                        varnishClam = obj.optInt("varnishClam", 0),
                        blueMussel = obj.optInt("blueMussel", 0),
                        californiaMussel = obj.optInt("californiaMussel", 0),
                        olympiaOyster = obj.optInt("olympiaOyster", 0),
                        pacificOyster = obj.optInt("pacificOyster", 0),
                        pinkScallop = obj.optInt("pinkScallop", 0),
                        purpleScallop = obj.optInt("purpleScallop", 0),
                        spinyScallop = obj.optInt("spinyScallop", 0),
                        weathervaneScallop = obj.optInt("weathervaneScallop", 0),
                        allClams = obj.optInt("allClams", 0),
                        allMussels = obj.optInt("allMussels", 0),
                        pinkAndSpiny = obj.optInt("pinkAndSpiny", 0),
                        purpleAndWeathervane = obj.optInt("purpleAndWeathervane", 0)
                    )
                    mapDao.insertCatchLimit(limit)
                }
                Log.d("MapRepository", "Initialized catch limits from assets")
            } catch (e: Exception) {
                Log.e("MapRepository", "Failed to initialize catch limits: ${e.message}")
            }
        }
    }

    private suspend fun fetchAndCacheStations(): List<Station> {
        try {
            val url = "https://api-sine.dfo-mpo.gc.ca/api/v1/stations?chs-region-code=PAC"
            val response = URL(url).readText()
            val stationsJson = JSONArray(response)
            val stationList = mutableListOf<Station>()
            
            for (i in 0 until stationsJson.length()) {
                val obj = stationsJson.getJSONObject(i)
                val id = obj.optString("id")
                val name = obj.optString("officialName")
                val lat = obj.optDouble("latitude")
                val lon = obj.optDouble("longitude")
                if (id.isNotEmpty() && !lat.isNaN() && !lon.isNaN()) {
                    stationList.add(Station(id, name, lat, lon))
                }
            }
            
            if (stationList.isNotEmpty()) {
                mapDao.clearStations()
                mapDao.insertAllStations(stationList)
            }
            return stationList
        } catch (e: Exception) {
            Log.e("MapPerformance", "Station sync failed: ${e.message}")
            return mapDao.getAllStations()
        }
    }

    private suspend fun syncTideData(stations: List<Station>, days: Int) = coroutineScope {
        if (stations.isEmpty()) return@coroutineScope
        
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val now = Calendar.getInstance()
        val from = sdf.format(now.time)
        now.add(Calendar.DAY_OF_YEAR, days)
        val to = sdf.format(now.time)

        val encodedFrom = URLEncoder.encode(from, "UTF-8")
        val encodedTo = URLEncoder.encode(to, "UTF-8")

        // Process tide data in parallel batches. This removes the sequential 500ms delay
        // and uses concurrent network requests to significantly speed up the process.
        stations.chunked(10).forEach { batch ->
            batch.map { station ->
                async(Dispatchers.IO) {
                    try {
                        val url = "https://api-sine.dfo-mpo.gc.ca/api/v1/stations/${station.id}/data?time-series-code=wlp-hilo&from=$encodedFrom&to=$encodedTo"
                        val connection = URL(url).openConnection() as HttpURLConnection
                        connection.connectTimeout = 10000
                        connection.readTimeout = 10000
                        
                        if (connection.responseCode == 200) {
                            val response = connection.inputStream.bufferedReader().use { it.readText() }
                            val dataJson = JSONArray(response)
                            
                            // Formats must be local to the async block as SimpleDateFormat is not thread-safe
                            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

                            val tideList = mutableListOf<TideData>()
                            for (i in 0 until dataJson.length()) {
                                val obj = dataJson.getJSONObject(i)
                                val rawTime = obj.optString("eventDate")
                                val value = obj.optDouble("value")
                                if (rawTime.isNotEmpty() && !value.isNaN()) {
                                    val dateObj = inputFormat.parse(rawTime)
                                    if (dateObj != null) {
                                        tideList.add(TideData(
                                            stationId = station.id,
                                            timestamp = dateObj.time,
                                            value = value
                                        ))
                                    }
                                }
                            }
                            if (tideList.isNotEmpty()) {
                                mapDao.deleteTideDataForStation(station.id)
                                mapDao.insertTideData(tideList)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MapPerformance", "Tide failed for ${station.id}: ${e.message}")
                    }
                }
            }.awaitAll()
            yield()
        }
    }

    private fun parseFeaturesStreaming(urlString: String, stations: List<Station>): List<Layer20Feature> {
        val result = mutableListOf<Layer20Feature>()
        var globalSR: String? = null
        
        // Use WGS84 for stations since the feature query uses outSR=4326
        val stationsWithGeom = stations.map { station ->
            station.id to com.arcgismaps.geometry.Point(station.longitude, station.latitude, SpatialReference.wgs84())
        }

        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.connectTimeout = 20000
        connection.readTimeout = 60000

        if (connection.responseCode == 200) {
            JsonReader(InputStreamReader(connection.inputStream, "UTF-8")).use { reader ->
                reader.isLenient = true
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "spatialReference" -> globalSR = parseRawJson(reader)
                        "features" -> {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                val feat = parseSingleFeature(reader, globalSR, stationsWithGeom)
                                if (feat != null) result.add(feat)
                            }
                            reader.endArray()
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
            }
        }
        return result
    }

    private fun parseSingleFeature(reader: JsonReader, globalSR: String?, stationsWithGeom: List<Pair<String, com.arcgismaps.geometry.Point>>): Layer20Feature? {
        var geometry: String? = null
        var attributes: String? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "attributes" -> attributes = parseRawJson(reader)
                "geometry" -> geometry = parseRawJson(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (geometry != null && attributes != null && geometry.length < 100 * 1024) {
            val attrObj = JSONObject(attributes)
            val geomObj = JSONObject(geometry)
            if (!geomObj.has("spatialReference")) {
                if (globalSR != null) geomObj.put("spatialReference", JSONObject(globalSR))
                else geomObj.put("spatialReference", JSONObject().put("wkid", 3857))
            }
            
            val arcgisGeom = Geometry.fromJsonOrNull(geomObj.toString())
            val extent = arcgisGeom?.extent
            
            val centerLat = if (extent != null) (extent.yMin + extent.yMax) / 2.0 else 0.0
            val centerLon = if (extent != null) (extent.xMin + extent.xMax) / 2.0 else 0.0
            
            // Fast distance check using pre-projected stations
            val closestId = if (extent != null) {
                stationsWithGeom.minByOrNull { (_, p) ->
                    val dx = p.x - centerLon
                    val dy = p.y - centerLat
                    dx * dx + dy * dy
                }?.first
            } else null
            
            return Layer20Feature(
                objectId = attrObj.getInt("OBJECTID"),
                geometryJson = geomObj.toString(),
                minX = extent?.xMin ?: 0.0, minY = extent?.yMin ?: 0.0, maxX = extent?.xMax ?: 0.0, maxY = extent?.yMax ?: 0.0,
                poNum = attrObj.optString("PO_NUM", "N/A"),
                placeNameEn = attrObj.optString("PLACE_NAME_EN", "N/A"),
                publicNoticeUrl = attrObj.optString("PUBLIC_NOTICE_URL", "N/A"),
                reason = attrObj.optInt("REASON", -1),
                enforceDateEn = attrObj.optString("ENFORCE_DATE_EN", "N/A"),
                allBivalves = attrObj.optInt("ALL_BIVALVES", -1),
                butterClam = attrObj.optInt("BUTTER_CLAM", -1),
                geoduckClam = attrObj.optInt("GEODUCK_CLAM", -1),
                horseClam = attrObj.optInt("HORSE_CLAM", -1),
                littleneckClam = attrObj.optInt("LITTLENECK_CLAM", -1),
                manilaClam = attrObj.optInt("MANILA_CLAM", -1),
                nuttallsCockle = attrObj.optInt("NUTTALLS_COCKLE", -1),
                pacificRazorClam = attrObj.optInt("PACIFIC_RAZOR_CLAM", -1),
                softshellClam = attrObj.optInt("SOFTSHELL_CLAM", -1),
                varnishClam = attrObj.optInt("VARNISH_CLAM", -1),
                blueMussel = attrObj.optInt("BLUE_MUSSEL", -1),
                californiaMussel = attrObj.optInt("CALIFORNIA_MUSSEL", -1),
                olympiaOyster = attrObj.optInt("OLYMPIA_OYSTER", -1),
                pacificOyster = attrObj.optInt("PACIFIC_OYSTER", -1),
                pinkScallop = attrObj.optInt("PINK_SCALLOP", -1),
                purpleHingeRockScallop = attrObj.optInt("PURPLE_HINGE_ROCK_SCALLOP", -1),
                spinyScallop = attrObj.optInt("SPINY_SCALLOP", -1),
                weathervaneScallop = attrObj.optInt("WEATHERVANE_SCALLOP", -1),
                closestStationId = closestId
            )
        }
        return null
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    suspend fun getTideDataForStation(stationId: String): List<TideData> {
        return mapDao.getTideDataForStation(stationId)
    }

    /**
     * Attempts to find tide data by checking stations in order of proximity to the feature.
     * Returns the station used and the tide data, or null if no data is found for any station.
     */
    suspend fun getTideDataWithFallback(feature: Layer20Feature): Pair<Station, List<TideData>>? {
        val allStations = _stations.value
        if (allStations.isEmpty()) return null

        // 1. Try the specifically assigned closest station first
        val assignedStation = allStations.find { it.id == feature.closestStationId }
        if (assignedStation != null) {
            val data = mapDao.getTideDataForStation(assignedStation.id)
            if (data.isNotEmpty()) {
                return Pair(assignedStation, data)
            }
        }

        // 2. Fallback: Search other stations by proximity
        // Note: feature bounds are likely in Web Mercator (meters), so we convert 
        // station coords to a rough approximation for sorting if needed, 
        // or just rely on the stored ID as the primary source.
        val centerLat = (feature.minY + feature.maxY) / 2.0
        val centerLon = (feature.minX + feature.maxX) / 2.0

        val sortedStations = allStations.sortedBy { station ->
            // If the feature is in meters (3857) and station in degrees (4326), 
            // this distance is not accurate, but it serves as a fallback heuristic.
            // However, prioritize the assigned one above.
            calculateDistance(centerLat, centerLon, station.latitude, station.longitude)
        }

        for (station in sortedStations) {
            if (station.id == feature.closestStationId) continue // Already checked
            val data = mapDao.getTideDataForStation(station.id)
            if (data.isNotEmpty()) {
                return Pair(station, data)
            }
        }

        return null
    }

    fun hasOfflineBasemap(): Boolean = File(context.filesDir, "basemap.vtpk").exists()

    fun getOfflineBasemapSize(): String {
        val file = File(context.filesDir, "basemap.vtpk")
        if (!file.exists()) return "0 MB"
        val sizeInMb = file.length() / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f MB", sizeInMb)
    }

    fun clearOfflineBasemap(): Boolean {
        val file = File(context.filesDir, "basemap.vtpk")
        return if (file.exists()) file.delete() else false
    }

    suspend fun getValidationWarning(speciesName: String, quantity: Int, locationStr: String, timeStr: String): String? {
        val latLon = locationStr.split(",").map { it.trim().toDoubleOrNull() }
        if (latLon.size != 2 || latLon[0] == null || latLon[1] == null) return null
        
        val lat = latLon[0]!!
        val lon = latLon[1]!!

        val resources = context.resources
        
        // 1. Find the relevant regulation area (Layer20Feature)
        val allFeatures = _features.value
        if (allFeatures.isEmpty()) return null

        // Map species display name to internal property for closure checking
        // We need to check against both English and French names if the app supports both
        val configEn = android.content.res.Configuration(resources.configuration).apply { setLocale(java.util.Locale.ENGLISH) }
        val configFr = android.content.res.Configuration(resources.configuration).apply { setLocale(java.util.Locale.FRENCH) }
        val resEn = context.createConfigurationContext(configEn).resources
        val resFr = context.createConfigurationContext(configFr).resources

        fun isMatch(id: Int, input: String): Boolean {
            if (id == 0) return false
            return resEn.getString(id).equals(input, ignoreCase = true) || 
                   resFr.getString(id).equals(input, ignoreCase = true)
        }

        val speciesInternalName = when {
            isMatch(R.string.bc_name, speciesName) -> "butterClam"
            isMatch(R.string.g_name, speciesName) -> "geoduckClam"
            isMatch(R.string.hc_name, speciesName) -> "horseClam"
            isMatch(R.string.lc_name, speciesName) -> "littleneckClam"
            isMatch(R.string.mc_name, speciesName) -> "manilaClam"
            isMatch(R.string.nc_name, speciesName) -> "nuttallsCockle"
            isMatch(R.string.rc_name, speciesName) || speciesName.lowercase().contains("razor clam") -> "pacificRazorClam"
            isMatch(R.string.sc_name, speciesName) -> "softshellClam"
            isMatch(R.string.vc_name, speciesName) -> "varnishClam"
            isMatch(R.string.bm_name, speciesName) -> "blueMussel"
            isMatch(R.string.cm_name, speciesName) -> "californiaMussel"
            isMatch(R.string.oo_name, speciesName) -> "olympiaOyster"
            isMatch(R.string.po_name, speciesName) -> "pacificOyster"
            isMatch(R.string.ps_name, speciesName) -> "pinkScallop"
            isMatch(R.string.ss_name, speciesName) -> "spinyScallop"
            isMatch(R.string.rs_name, speciesName) || speciesName.lowercase().contains("purple hinge rock scallop") -> "purpleScallop"
            isMatch(R.string.ws_name, speciesName) -> "weathervaneScallop"
            else -> null
        }

        // Helper to check if a feature is "Closed" (1) for the target species
        fun isClosed(feature: Layer20Feature): Boolean {
            if (speciesInternalName == null) return false
            return when (speciesInternalName) {
                "butterClam" -> feature.butterClam
                "geoduckClam" -> feature.geoduckClam
                "horseClam" -> feature.horseClam
                "littleneckClam" -> feature.littleneckClam
                "manilaClam" -> feature.manilaClam
                "nuttallsCockle" -> feature.nuttallsCockle
                "pacificRazorClam" -> feature.pacificRazorClam
                "softshellClam" -> feature.softshellClam
                "varnishClam" -> feature.varnishClam
                "blueMussel" -> feature.blueMussel
                "californiaMussel" -> feature.californiaMussel
                "olympiaOyster" -> feature.olympiaOyster
                "pacificOyster" -> feature.pacificOyster
                "pinkScallop" -> feature.pinkScallop
                "spinyScallop" -> feature.spinyScallop
                "purpleScallop" -> feature.purpleHingeRockScallop
                "weathervaneScallop" -> feature.weathervaneScallop
                else -> -1
            } == 1
        }

        // Find features that contain the point OR are within ~100m
        val userPoint = com.arcgismaps.geometry.Point(lon, lat, SpatialReference.wgs84())
        
        val candidateFeatures = allFeatures.filter { 
            // Broad bounding box check (0.002 degrees is ~220m, safe buffer)
            lon >= (it.minX - 0.002) && lon <= (it.maxX + 0.002) && 
            lat >= (it.minY - 0.002) && lat <= (it.maxY + 0.002)
        }

        // Refine using actual geometry intersection with a 100m geodetic buffer
        val intersectingFeatures = candidateFeatures.filter { feature ->
            val geom = Geometry.fromJsonOrNull(feature.geometryJson) ?: return@filter false
            
            // Project user point to feature's SR if needed
            val targetPoint = if (geom.spatialReference != userPoint.spatialReference && geom.spatialReference != null) {
                GeometryEngine.projectOrNull(userPoint, geom.spatialReference!!) as? com.arcgismaps.geometry.Point ?: userPoint
            } else {
                userPoint
            }

            // Check if point is within 100m geodetically
            try {
                // For ArcGIS Maps SDK 200.x, use nearestCoordinate to find distance
                val result = GeometryEngine.nearestCoordinate(geom, targetPoint)
                result?.distance ?: Double.MAX_VALUE <= 100.0
            } catch (e: Exception) {
                // Fallback to simple intersection
                GeometryEngine.intersects(geom, targetPoint)
            }
        }

        val bestFeature = if (intersectingFeatures.isNotEmpty()) {
            // Priority:
            // 1. Any area that is CLOSED (red) for this species
            // 2. Otherwise, the smallest area (most specific)
            intersectingFeatures.find { isClosed(it) } ?: intersectingFeatures.minBy { 
                (it.maxX - it.minX) * (it.maxY - it.minY) 
            }
        } else if (candidateFeatures.isNotEmpty()) {
            // Fallback to closest bounding box if no actual intersection
            candidateFeatures.minBy { 
                val centerX = (it.minX + it.maxX) / 2.0
                val centerY = (it.minY + it.maxY) / 2.0
                calculateDistance(lat, lon, centerY, centerX)
            }
        } else {
            // Ultimate fallback: Find the geographically nearest area based on center point distance
            allFeatures.minByOrNull { feature ->
                val centerX = (feature.minX + feature.maxX) / 2.0
                val centerY = (feature.minY + feature.maxY) / 2.0
                calculateDistance(lat, lon, centerY, centerX)
            }
        }
        
        if (bestFeature == null) return null

        // 3. Check if species is closed in this area
        if (speciesInternalName != null) {
            if (isClosed(bestFeature)) {
                return resources.getString(R.string.harvest_closed_warning, speciesName, bestFeature.poNum)
            }
        }

        // 4. Check daily limits
        val zoneId = if (bestFeature.placeNameEn.contains("Pacific Rim", ignoreCase = true)) "PACIFIC-RIM" else "DEFAULT"
        val limitObj = mapDao.getCatchLimit(zoneId) ?: mapDao.getCatchLimit("DEFAULT")
        
        if (limitObj != null && speciesInternalName != null) {
            val limit = when (speciesInternalName) {
                "butterClam" -> limitObj.butterClam
                "geoduckClam" -> limitObj.geoduck
                "horseClam" -> limitObj.horseClam
                "littleneckClam" -> limitObj.littleneckClam
                "manilaClam" -> limitObj.manilaClam
                "nuttallsCockle" -> limitObj.nuttallsCockle
                "pacificRazorClam" -> limitObj.pacificRazorClam
                "softshellClam" -> limitObj.softshellClam
                "varnishClam" -> limitObj.varnishClam
                "blueMussel" -> limitObj.blueMussel
                "californiaMussel" -> limitObj.californiaMussel
                "olympiaOyster" -> limitObj.olympiaOyster
                "pacificOyster" -> limitObj.pacificOyster
                "pinkScallop" -> limitObj.pinkScallop
                "spinyScallop" -> limitObj.spinyScallop
                "purpleScallop" -> limitObj.purpleScallop
                "weathervaneScallop" -> limitObj.weathervaneScallop
                else -> 0
            }
            
            if (limit > 0) {
                // Check daily limits including previous entries for the same species today
                val todayDate = try {
                    val sdf = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
                    val parsedTime = SimpleDateFormat("MM/dd/yyyy hh:mm a z", Locale.getDefault()).parse(timeStr)
                    if (parsedTime != null) sdf.format(parsedTime) else timeStr.split(" ").firstOrNull()
                } catch (e: Exception) {
                    timeStr.split(" ").firstOrNull()
                }

                val previousEntries = mapDao.getAllCatchEntries().filter { entry ->
                    val entryDate = entry.time.split(" ").firstOrNull()
                    val entrySpeciesMatch = isMatch(when(speciesInternalName) {
                        "butterClam" -> R.string.bc_name
                        "geoduckClam" -> R.string.g_name
                        "horseClam" -> R.string.hc_name
                        "littleneckClam" -> R.string.lc_name
                        "manilaClam" -> R.string.mc_name
                        "nuttallsCockle" -> R.string.nc_name
                        "pacificRazorClam" -> R.string.rc_name
                        "softshellClam" -> R.string.sc_name
                        "varnishClam" -> R.string.vc_name
                        "blueMussel" -> R.string.bm_name
                        "californiaMussel" -> R.string.cm_name
                        "olympiaOyster" -> R.string.oo_name
                        "pacificOyster" -> R.string.po_name
                        "pinkScallop" -> R.string.ps_name
                        "spinyScallop" -> R.string.ss_name
                        "purpleScallop" -> R.string.rs_name
                        "weathervaneScallop" -> R.string.ws_name
                        else -> 0
                    }, entry.species)

                    entrySpeciesMatch && entryDate == todayDate && entry.id != 0
                }
                
                val totalHarvestedToday = previousEntries.sumOf { it.quantity.toIntOrNull() ?: 0 } + quantity
                
                if (totalHarvestedToday > limit) {
                    return if (previousEntries.isEmpty()) {
                        resources.getString(R.string.daily_limit_exceeded_new, speciesName, limit, quantity)
                    } else {
                        val alreadyHarvested = totalHarvestedToday - quantity
                        resources.getString(R.string.daily_limit_exceeded_existing, speciesName, limit, alreadyHarvested, quantity, totalHarvestedToday)
                    }
                }

                // 5. Group Limits
                val groupInfo = when (speciesInternalName) {
                    "butterClam", "horseClam", "littleneckClam", "manilaClam", "softshellClam", "varnishClam", "pacificRazorClam" ->
                        Triple(resources.getString(R.string.allClams), limitObj.allClams, listOf(
                            R.string.bc_name, R.string.hc_name, R.string.lc_name, R.string.mc_name, 
                            R.string.sc_name, R.string.vc_name, R.string.rc_name
                        ))
                    "blueMussel", "californiaMussel" -> 
                        Triple(resources.getString(R.string.allMussels), limitObj.allMussels, listOf(R.string.bm_name, R.string.cm_name))
                    "pinkScallop", "spinyScallop" -> 
                        Triple(resources.getString(R.string.pinkAndSpiny), limitObj.pinkAndSpiny, listOf(R.string.ps_name, R.string.ss_name))
                    "purpleScallop", "weathervaneScallop" -> 
                        Triple(resources.getString(R.string.purpleAndWeathervane), limitObj.purpleAndWeathervane, listOf(R.string.rs_name, R.string.ws_name))
                    else -> null
                }

                if (groupInfo != null && groupInfo.second > 0) {
                    val groupName = groupInfo.first
                    val groupLimit = groupInfo.second
                    val speciesIdsInGroup = groupInfo.third
                    
                    val previousGroupEntries = mapDao.getAllCatchEntries().filter { entry ->
                        val entryDate = entry.time.split(" ").firstOrNull()
                        entryDate == todayDate && entry.id != 0 && speciesIdsInGroup.any { isMatch(it, entry.species) }
                    }
                    
                    val totalGroupHarvested = previousGroupEntries.sumOf { it.quantity.toIntOrNull() ?: 0 } + quantity
                    
                    if (totalGroupHarvested > groupLimit) {
                        return if (previousGroupEntries.isEmpty() || (previousGroupEntries.size == 1 && isMatch(groupInfo.third.first(), previousGroupEntries[0].species))) {
                             resources.getString(R.string.aggregate_limit_exceeded_new, groupName, groupLimit, quantity)
                        } else {
                            val alreadyHarvested = totalGroupHarvested - quantity
                            resources.getString(R.string.aggregate_limit_exceeded_existing, groupName, groupLimit, alreadyHarvested, quantity, totalGroupHarvested)
                        }
                    }
                }
            }
        }

        return null
    }

    suspend fun upsertCatchEntry(species: String, quantity: String, time: String, location: String, id: Int = 0) {
        val entry = CatchEntry(
            id = if (id == 0) 0 else id,
            species = species,
            quantity = quantity,
            time = time,
            location = location
        )
        mapDao.insertCatchEntry(entry)
        _catchEntries.value = mapDao.getAllCatchEntries()
    }

    suspend fun deleteCatchEntry(id: Int) {
        mapDao.deleteCatchEntry(id)
        _catchEntries.value = mapDao.getAllCatchEntries()
    }

    /**
     * Starts the basemap download in a background scope that persists even if the UI is dismissed.
     */
    fun startBasemapDownload(envelope: Envelope) {
        if (_basemapDownloadProgress.value in 0..99) return // Already downloading

        basemapDownloadJob?.cancel()
        basemapDownloadJob = repositoryScope.launch {
            try {
                downloadBasemapArea(envelope) { progress ->
                    _basemapDownloadProgress.value = progress
                }
                // Small delay to show 100% before resetting
                delay(1500)
                _basemapDownloadProgress.value = -1
            } catch (e: Exception) {
                Log.e("MapRepository", "Background download failed", e)
                _basemapDownloadProgress.value = -2 // Indicate error
                delay(3000)
                _basemapDownloadProgress.value = -1
            }
        }
    }

    fun cancelBasemapDownload() {
        repositoryScope.launch {
            activeArcGISJob?.cancel()
        }
        basemapDownloadJob?.cancel()
        _basemapDownloadProgress.value = -1
    }

    suspend fun downloadBasemapArea(envelope: Envelope, onProgress: (Int) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val vtpkFile = File(context.filesDir, "basemap.vtpk")
            Log.d("MapRepository", "Starting basemap download to: ${vtpkFile.absolutePath}")

            // Use cached URL or extract it from the basemap style
            val url = cachedBasemapUrl ?: run {
                val tempMap = ArcGISMap(BasemapStyle.ArcGISNavigation)
                tempMap.load().getOrElse {
                    Log.e("MapRepository", "Failed to load basemap style: ${it.message}")
                    return@withContext Result.failure(it)
                }
                val vectorLayer = tempMap.basemap.value?.baseLayers?.filterIsInstance<ArcGISVectorTiledLayer>()?.firstOrNull()
                val extractedUrl = vectorLayer?.uri ?: return@withContext Result.failure(Exception("No vector layer found"))
                cachedBasemapUrl = extractedUrl
                extractedUrl
            }
            
            Log.d("MapRepository", "Using basemap URL: $url")

            if (vtpkFile.exists()) vtpkFile.delete()

            val exportTask = ExportVectorTilesTask(url)
            
            // Optimization: Increasing maxScale to 25000 significantly reduces tile count 
            // and download time while maintaining good detail for coastal navigation.
            val parameters = exportTask.createDefaultExportVectorTilesParameters(
                areaOfInterest = envelope,
                maxScale = 25000.0 
            ).getOrElse { return@withContext Result.failure(it) }

            val job = exportTask.createExportVectorTilesJob(parameters, vtpkFile.absolutePath)
            activeArcGISJob = job
            
            val progressJob = launch {
                job.progress.collect { progress -> onProgress(progress) }
            }

            job.start()
            val result = job.result()
            progressJob.cancel()
            activeArcGISJob = null

            result.getOrThrow()
            Log.d("MapRepository", "Download completed: ${vtpkFile.length()} bytes")
            Result.success(Unit)
        } catch (e: Exception) {
            activeArcGISJob = null
            if (e is CancellationException) throw e
            Log.e("MapRepository", "Download failed", e)
            Result.failure(e)
        }
    }


    private fun parseRawJson(reader: JsonReader): String {
        val sb = StringBuilder()
        fun recursive(r: JsonReader) {
            when (r.peek()) {
                android.util.JsonToken.BEGIN_OBJECT -> {
                    r.beginObject(); sb.append("{")
                    var first = true
                    while (r.hasNext()) {
                        if (!first) sb.append(","); sb.append("\"").append(r.nextName()).append("\":")
                        recursive(r); first = false
                    }
                    r.endObject(); sb.append("}")
                }
                android.util.JsonToken.BEGIN_ARRAY -> {
                    r.beginArray(); sb.append("[")
                    var first = true
                    while (r.hasNext()) {
                        if (!first) sb.append(","); recursive(r); first = false
                    }
                    r.endArray(); sb.append("]")
                }
                android.util.JsonToken.STRING -> sb.append(JSONObject.quote(r.nextString()))
                android.util.JsonToken.NUMBER -> sb.append(r.nextString())
                android.util.JsonToken.BOOLEAN -> sb.append(r.nextBoolean())
                android.util.JsonToken.NULL -> { r.nextNull(); sb.append("null") }
                else -> r.skipValue()
            }
        }
        recursive(reader)
        return sb.toString()
    }
    
    
}
