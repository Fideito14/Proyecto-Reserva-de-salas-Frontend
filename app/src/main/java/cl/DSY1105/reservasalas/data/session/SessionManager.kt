package cl.DSY1105.reservasalas.data.session

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cl.DSY1105.reservasalas.data.room.Usuario
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "app_prefs")

private val KEY_TOKEN = stringPreferencesKey("jwt_token")
private val KEY_ID = longPreferencesKey("usuario_id")
private val KEY_CORREO = stringPreferencesKey("usuario_correo")
private val KEY_NOMBRE = stringPreferencesKey("usuario_nombre")
private val KEY_ROL = stringPreferencesKey("usuario_rol")
private val KEY_PRINCIPAL = booleanPreferencesKey("usuario_principal")

/**
 * Guarda el JWT y el perfil del usuario con sesión iniciada.
 * El perfil se guarda para poder abrir la app sin conexión (modo solo lectura).
 */
class SessionManager(context: Context) {

    private val store = context.applicationContext.dataStore

    val tokenFlow: Flow<String> = store.data.map { it[KEY_TOKEN] ?: "" }

    suspend fun token(): String = tokenFlow.first()

    suspend fun guardarToken(token: String) {
        store.edit { it[KEY_TOKEN] = token }
    }

    suspend fun guardarUsuario(usuario: Usuario) {
        store.edit {
            it[KEY_ID] = usuario.id
            it[KEY_CORREO] = usuario.correo
            it[KEY_NOMBRE] = usuario.nombre
            it[KEY_ROL] = usuario.rol
            it[KEY_PRINCIPAL] = usuario.coordinadorPrincipal
        }
    }

    suspend fun usuario(): Usuario? {
        val prefs = store.data.first()
        val id = prefs[KEY_ID] ?: return null
        val correo = prefs[KEY_CORREO] ?: return null
        return Usuario(
            id = id,
            correo = correo,
            nombre = prefs[KEY_NOMBRE] ?: correo.substringBefore("@"),
            rol = prefs[KEY_ROL] ?: "",
            coordinadorPrincipal = prefs[KEY_PRINCIPAL] ?: false
        )
    }

    suspend fun limpiar() {
        store.edit { it.clear() }
    }
}
