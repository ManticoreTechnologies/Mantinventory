package com.manticore.mantinventory.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "boxes",
    indices = [Index(value = ["labelCode"], unique = true)]
)
data class BoxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val location: String,
    val description: String,
    @ColumnInfo(name = "labelCode") val labelCode: String,
    @ColumnInfo(name = "labelPngPath") val labelPngPath: String = "",
    @ColumnInfo(name = "createdAt") val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = BoxEntity::class,
            parentColumns = ["id"],
            childColumns = ["boxId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("boxId"), Index("barcodeOrQr"), Index("name"), Index("description")]
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(index = true) val boxId: Long,
    val name: String,
    val description: String,
    val quantity: Int = 1,
    val minimumStock: Int = 1,
    @ColumnInfo(name = "barcodeOrQr") val barcodeOrQr: String = "",
    @ColumnInfo(name = "updatedAt") val updatedAt: Long = System.currentTimeMillis()
)

data class BoxWithItems(
    @Embedded val box: BoxEntity,
    @Relation(parentColumn = "id", entityColumn = "boxId")
    val items: List<ItemEntity>
)
