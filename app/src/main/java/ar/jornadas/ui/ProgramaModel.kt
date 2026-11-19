package ar.jornadas.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import ar.jornadas.Servicios
import ar.jornadas.data.Charla
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.IOException

const val TODOS = "Todos"

// Estados excluyentes de la pantalla (en vez de aviso + cargando + lista sueltos).
sealed interface EstadoPrograma {
    data object Cargando : EstadoPrograma                      // abriendo, todavía sin datos
    data object SinDescarga : EstadoPrograma                   // nunca se descargó: ofrecer reintento
    data class Programa(                                       // hay una edición (puede tener 0 charlas)
        val charlas: List<Charla>,
        val actualizando: Boolean,
        val usandoGuardados: Boolean,
    ) : EstadoPrograma
}

private enum class Sincro { Inactiva, EnCurso, Ok, Fallo }

class ProgramaModel(app: Application, private val guardado: SavedStateHandle) : AndroidViewModel(app) {
    private val servicios = Servicios.de(app)
    val mesa = servicios.mesa
    private val repo = servicios.agenda

    private val _sincro = MutableStateFlow(Sincro.Inactiva)
    private var descarga: Job? = null

    val estado: StateFlow<EstadoPrograma> =
        combine(repo.programa, repo.hayEdicion, _sincro) { charlas, hayEdicion, sincro ->
            when {
                hayEdicion -> EstadoPrograma.Programa(
                    charlas = charlas,
                    actualizando = sincro == Sincro.EnCurso,
                    usandoGuardados = sincro == Sincro.Fallo,
                )
                sincro == Sincro.EnCurso -> EstadoPrograma.Cargando
                else -> EstadoPrograma.SinDescarga
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, EstadoPrograma.Cargando)

    // Filtros en SavedStateHandle: sobreviven a la rotación y a la muerte del proceso,
    // y una tarea nueva arranca sin filtros.
    val dia: StateFlow<Int> = guardado.getStateFlow(CLAVE_DIA, 0)
    val tema: StateFlow<String> = guardado.getStateFlow(CLAVE_TEMA, TODOS)

    val visibles: StateFlow<List<Charla>> =
        combine(estado, dia, tema) { e, d, t ->
            (e as? EstadoPrograma.Programa)?.charlas.orEmpty()
                .filter { (d == 0 || it.dia == d) && (t == TODOS || it.tema == t) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val agenda: StateFlow<List<Charla>> =
        estado.map { e -> (e as? EstadoPrograma.Programa)?.charlas.orEmpty().filter { it.favorita } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // Una sola carga automática por proceso (no por Activity, rotación ni pestaña).
        if (repo.reclamarCargaInicial()) recargar()
    }

    fun elegirDia(d: Int) { guardado[CLAVE_DIA] = d }
    fun elegirTema(t: String) { guardado[CLAVE_TEMA] = t }

    fun recargar() {
        if (descarga?.isActive == true) return   // toques durante una petición: no suman intentos
        descarga = viewModelScope.launch {
            _sincro.value = Sincro.EnCurso
            _sincro.value = try {
                repo.sincronizar()
                Sincro.Ok
            } catch (e: IOException) {
                Sincro.Fallo
            }
        }
    }

    fun marcar(id: String) { viewModelScope.launch { repo.cambiarFavorita(id) } }

    private companion object {
        const val CLAVE_DIA = "dia"
        const val CLAVE_TEMA = "tema"
    }
}
