package ar.jornadas.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicBoolean

class AgendaRepository(private val api: ProgramaApi, private val db: Archivo) {
    private val dao = db.cartelera()
    private val cargaInicialHecha = AtomicBoolean(false)

    val programa: Flow<List<Charla>> = dao.observar()
    val hayEdicion: Flow<Boolean> = dao.contarEdiciones().map { it > 0 }

    // true solo la primera vez en el proceso (el repositorio vive lo mismo que el proceso).
    fun reclamarCargaInicial(): Boolean = cargaInicialHecha.compareAndSet(false, true)

    suspend fun sincronizar() {
        // La red va FUERA de la transacción: puede tardar 8 s y no debe bloquear la base.
        val remotas = api.consultar()
        // Todo o nada. Las favoritas se leen recién acá, así se respeta una marca
        // agregada o quitada mientras esperábamos la respuesta.
        db.withTransaction {
            val favoritas = dao.idsFavoritas().toSet()
            dao.borrarTodo()
            dao.insertar(remotas.map {
                Charla(it.id, it.title, it.track, it.day, it.time, it.room, it.speaker, it.summary,
                    favorita = it.id in favoritas)
            })
            dao.guardarEdicion(Edicion(descargadaEn = System.currentTimeMillis()))
        }
    }

    suspend fun cambiarFavorita(id: String) = dao.alternar(id)
}
