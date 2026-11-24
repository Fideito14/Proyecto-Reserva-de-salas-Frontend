package cl.DSY1105.reservasalas.ui.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cl.DSY1105.reservasalas.ui.theme.Paleta
import cl.DSY1105.reservasalas.viewmodel.AgendaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsuariosScreen(
    vm: AgendaViewModel,
    onVolver: () -> Unit
) {
    val usuarios by vm.usuarios.collectAsState()
    val miId by vm.usuarioId.collectAsState()
    val online by vm.online.collectAsState()
    val cargando by vm.cargando.collectAsState()
    val msg by vm.mensaje.collectAsState()
    val alerta by vm.alertaConexion.collectAsState()

    val snack = remember { SnackbarHostState() }

    // Al entrar se actualiza la lista desde el backend (sin conexión queda la copia local).
    LaunchedEffect(Unit) {
        vm.cargarUsuarios()
    }

    LaunchedEffect(msg) {
        msg?.let {
            snack.showSnackbar(it)
            vm.mensaje.value = null
        }
    }

    AlertaConexion(
        mensaje = alerta,
        onCerrar = { vm.alertaConexion.value = null }
    )

    Scaffold(
        snackbarHost = {
            SnackbarHost(snack)
        },
        topBar = {
            TopAppBar(
                title = { Text("Usuarios") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    TextButton(onClick = onVolver) {
                        Text("Volver")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier.padding(pad)
        ) {
            BannerSinConexion(online)

            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "Como coordinador puedes asignar o quitar el rol de coordinador. " +
                            "El coordinador principal no se puede modificar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Paleta.TextoSec
                )

                Spacer(Modifier.height(12.dp))

                if (usuarios.isEmpty()) {
                    Text("No hay usuarios guardados localmente.")
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(usuarios, key = { it.id }) { usuario ->
                            Card(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            usuario.nombre,
                                            style = MaterialTheme.typography.titleMedium
                                        )

                                        Text(
                                            usuario.correo,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Paleta.TextoSec
                                        )

                                        Text(
                                            when {
                                                usuario.coordinadorPrincipal -> "Coordinador principal"
                                                usuario.esCoordinador -> "Coordinador"
                                                else -> "Relator"
                                            },
                                            color = if (usuario.esCoordinador) {
                                                Paleta.ExitoTexto
                                            } else {
                                                Paleta.TextoSec
                                            }
                                        )
                                    }

                                    // El principal y uno mismo no se pueden modificar.
                                    if (!usuario.coordinadorPrincipal && usuario.id != miId) {
                                        if (usuario.esCoordinador) {
                                            OutlinedButton(
                                                enabled = !cargando,
                                                onClick = {
                                                    if (!online) {
                                                        vm.avisarSinConexion()
                                                    } else {
                                                        vm.cambiarRol(usuario, false)
                                                    }
                                                }
                                            ) {
                                                Text("Quitar")
                                            }
                                        } else {
                                            Button(
                                                enabled = !cargando,
                                                onClick = {
                                                    if (!online) {
                                                        vm.avisarSinConexion()
                                                    } else {
                                                        vm.cambiarRol(usuario, true)
                                                    }
                                                }
                                            ) {
                                                Text("Hacer coord.")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
