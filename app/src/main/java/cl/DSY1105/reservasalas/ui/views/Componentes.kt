package cl.DSY1105.reservasalas.ui.views

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Franja que avisa que la app está en modo solo lectura. */
@Composable
fun BannerSinConexion(online: Boolean) {
    if (online) return

    Surface(
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "Sin conexión: solo puedes consultar. Para reservar, cancelar o cambiar roles necesitas conexión.",
            color = MaterialTheme.colorScheme.surface,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

/** Alerta que se muestra cuando se intenta una operación que requiere conexión. */
@Composable
fun AlertaConexion(
    mensaje: String?,
    onCerrar: () -> Unit
) {
    if (mensaje == null) return

    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Sin conexión") },
        text = { Text(mensaje) },
        confirmButton = {
            TextButton(onClick = onCerrar) {
                Text("Entendido")
            }
        }
    )
}
