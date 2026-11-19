package ar.jornadas.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import retrofit2.http.GET
import kotlinx.coroutines.flow.Flow

data class FichaRemota(val id: String, val title: String, val track: String, val day: Int,
    val time: String, val room: String, val speaker: String, val summary: String)

interface ProgramaApi {
    @GET("programa") suspend fun consultar(): List<FichaRemota>
}

@Entity(tableName = "charlas")
data class Charla(@PrimaryKey val id: String, val titulo: String, val tema: String, val dia: Int,
    val hora: String, val sala: String, val ponente: String, val resumen: String,
    val favorita: Boolean = false)

// Una sola fila (id 0): existe si alguna vez se descargó una edición con éxito,
// aunque esa edición tenga cero charlas (v4).
@Entity(tableName = "edicion")
data class Edicion(@PrimaryKey val id: Int = 0, val descargadaEn: Long)

@Dao
interface CarteleraDao {
    @Query("SELECT * FROM charlas ORDER BY dia, hora, sala")
    fun observar(): Flow<List<Charla>>

    @Query("SELECT COUNT(*) FROM edicion")
    fun contarEdiciones(): Flow<Int>

    @Query("SELECT id FROM charlas WHERE favorita = 1")
    suspend fun idsFavoritas(): List<String>

    @Query("DELETE FROM charlas")
    suspend fun borrarTodo()

    @Insert
    suspend fun insertar(charlas: List<Charla>)

    // Acá REPLACE está bien: la fila no tiene datos locales que se puedan pisar.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarEdicion(edicion: Edicion)

    @Query("UPDATE charlas SET favorita = NOT favorita WHERE id = :id")
    suspend fun alternar(id: String)
}

@Database(entities = [Charla::class, Edicion::class], version = 2, exportSchema = false)
abstract class Archivo : RoomDatabase() {
    abstract fun cartelera(): CarteleraDao
}

// v1 -> v2: agrega la tabla edicion sin tocar charlas (se conservan las favoritas).
// Quien ya tenía charlas guardadas cuenta como "con edición previa".
val MIGRACION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `edicion` (`id` INTEGER NOT NULL, `descargadaEn` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("INSERT INTO `edicion` (`id`, `descargadaEn`) SELECT 0, 0 WHERE EXISTS (SELECT 1 FROM `charlas`)")
    }
}
