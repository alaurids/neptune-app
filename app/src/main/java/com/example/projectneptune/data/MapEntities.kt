package com.example.projectneptune.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "layer_20_features")
data class Layer20Feature(
    @PrimaryKey val objectId: Int,
    val geometryJson: String,
    
    // Spatial Bounds for efficient filtering
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double,
    
    // Core Attributes
    val poNum: String,
    val placeNameEn: String,
    val publicNoticeUrl: String,
    val reason: Int,
    val enforceDateEn: String,
    
    // Species Status (0 = Open, 1 = Closed, -1 = N/A)
    val allBivalves: Int,
    val butterClam: Int,
    val geoduckClam: Int,
    val horseClam: Int,
    val littleneckClam: Int,
    val manilaClam: Int,
    val nuttallsCockle: Int,
    val pacificRazorClam: Int,
    val softshellClam: Int,
    val varnishClam: Int,
    val blueMussel: Int,
    val californiaMussel: Int,
    val olympiaOyster: Int,
    val pacificOyster: Int,
    val pinkScallop: Int,
    val purpleHingeRockScallop: Int,
    val spinyScallop: Int,
    val weathervaneScallop: Int,

    // Link to Tides
    val closestStationId: String? = null
)

@Entity(tableName = "stations")
data class Station(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double
)

@Entity(tableName = "tide_data")
data class TideData(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val stationId: String,
    val dateLabel: String, // e.g., "Oct 12"
    val timeLabel: String, // e.g., "14:30"
    val value: Double
)

@Entity(tableName = "static_boundaries")
data class StaticBoundary(
    @PrimaryKey val id: String, // adminAreaId
    val name: String,
    val geometryJson: String
)

@Entity(tableName = "catch_limits")
data class CatchLimit(
    @PrimaryKey val zoneId: String, // e.g., "DEFAULT", "PACIFIC-RIM"
    val butterClam: Int,
    val geoduck: Int,
    val horseClam: Int,
    val littleneckClam: Int,
    val manilaClam: Int,
    val nuttallsCockle: Int,
    val pacificRazorClam: Int,
    val softshellClam: Int,
    val varnishClam: Int,
    val blueMussel: Int,
    val californiaMussel: Int,
    val olympiaOyster: Int,
    val pacificOyster: Int,
    val pinkScallop: Int,
    val purpleScallop: Int,
    val spinyScallop: Int,
    val weathervaneScallop: Int,
    
    // Combined categories
    val allClams: Int,
    val allMussels: Int,
    val pinkAndSpiny: Int,
    val purpleAndWeathervane: Int
)

@Entity(tableName = "app_metadata")
data class AppMetadata(
    @PrimaryKey val key: String,
    val value: String
)
