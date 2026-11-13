package app.recetario.shopping

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import app.recetario.recipes.quantity.MeasureUnit
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Entity(tableName = "shopping_items")
data class ShoppingItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val quantity: Double?,
    val unit: MeasureUnit,
    val customUnit: String?,
    val section: ShopSection,
    val checked: Boolean,
    val sourceRecipeId: String?,
    val createdAt: Instant,
) {
    fun toModel() = ShoppingItem(id, name, quantity, unit, customUnit, section, checked, sourceRecipeId, createdAt)
}

fun ShoppingItem.toEntity() =
    ShoppingItemEntity(id, name, quantity, unit, customUnit, section, checked, sourceRecipeId, createdAt)

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_items ORDER BY createdAt, rowid")
    fun observeAll(): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_items ORDER BY createdAt, rowid")
    suspend fun getAll(): List<ShoppingItemEntity>

    @Upsert
    suspend fun upsert(items: List<ShoppingItemEntity>)

    @Query("UPDATE shopping_items SET checked = NOT checked WHERE id = :id")
    suspend fun toggle(id: String)

    @Query("DELETE FROM shopping_items WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM shopping_items WHERE checked = 1")
    suspend fun deleteChecked()
}
