package ar.jornadas.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.jornadas.data.Charla

private val DIAS = listOf("Ambos días", "Día 1", "Día 2")
private val TEMAS = listOf(TODOS, "Android", "Datos", "Calidad")

// Solo pinta el estado y avisa eventos: la descarga automática la decide el ViewModel.
@Composable
fun Catalogo(model: ProgramaModel, abrir: (String) -> Unit) {
    val estado by model.estado.collectAsStateWithLifecycle()
    val dia by model.dia.collectAsStateWithLifecycle()
    val tema by model.tema.collectAsStateWithLifecycle()
    val visibles by model.visibles.collectAsStateWithLifecycle()
    Selector(DIAS, dia, model::elegirDia)
    Selector(TEMAS, TEMAS.indexOf(tema).coerceAtLeast(0)) { model.elegirTema(TEMAS[it]) }
    TextButton(onClick = model::recargar) { Text("Actualizar") }
    when (val e = estado) {
        EstadoPrograma.Cargando -> LinearProgressIndicator(Modifier.fillMaxWidth())
        EstadoPrograma.SinDescarga -> {
            Text("No se pudo descargar el programa.")
            OutlinedButton(onClick = model::recargar) { Text("Reintentar") }
        }
        is EstadoPrograma.Programa -> {
            if (e.actualizando) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (e.usandoGuardados) Text("Sin conexión: se están usando datos guardados.",
                color = MaterialTheme.colorScheme.secondary)
            when {
                e.charlas.isEmpty() -> Text("Sin charlas programadas.")
                visibles.isEmpty() -> Text("No hay charlas con esos filtros.")
                else -> Grilla(visibles, abrir)
            }
        }
    }
}

@Composable
private fun Selector(opciones: List<String>, indice: Int, elegir: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        opciones.forEachIndexed { i, texto ->
            FilterChip(selected = i == indice, onClick = { elegir(i) }, label = { Text(texto) })
        }
    }
}

@Composable
fun Grilla(fichas: List<Charla>, abrir: (String) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 12.dp)) {
        items(fichas, key = { it.id }) { ficha ->
            ElevatedCard(onClick = { abrir(ficha.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Día ${ficha.dia} · ${ficha.hora} · ${ficha.sala}", style = MaterialTheme.typography.labelMedium)
                    Text(ficha.titulo, style = MaterialTheme.typography.titleLarge)
                    Text(if (ficha.favorita) "${ficha.tema} · En mi agenda" else ficha.tema)
                }
            }
        }
    }
}
