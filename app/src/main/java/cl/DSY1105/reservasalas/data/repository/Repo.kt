package cl.DSY1105.reservasalas.data.repository

import androidx.room.withTransaction
import cl.DSY1105.reservasalas.data.api.ApiService
import cl.DSY1105.reservasalas.data.dto.CambiarRolRequest
import cl.DSY1105.reservasalas.data.dto.ErrorResponse
import cl.DSY1105.reservasalas.data.dto.LoginRequest
import cl.DSY1105.reservasalas.data.dto.RegistroRequest
import cl.DSY1105.reservasalas.data.dto.toEntity
import cl.DSY1105.reservasalas.data.network.NetworkMonitor
import cl.DSY1105.reservasalas.data.room.AppDatabase
import cl.DSY1105.reservasalas.data.room.Reserva
import cl.DSY1105.reservasalas.data.room.Sala
import cl.DSY1105.reservasalas.data.room.Usuario
import cl.DSY1105.reservasalas.data.session.SessionManager
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import retrofit2.Response
import java.io.IOException

/**
 * Única fuente de datos de la app:
 *  - Backend (Retrofit): autenticación y usuarios/roles.
 *  - Room: salas y reservas (viven en el dispositivo) + copia local de usuarios.
 */
class Repo(
    private val api: ApiService,
    private val db: AppDatabase,
    private val session: SessionManager,
    private val red: NetworkMonitor
) {

    private val dao = db.dao()
    private val gson = Gson()

    // ---- Conectividad ----

    val online: Flow<Boolean> = red.observar()

    fun hayRed(): Boolean = red.isOnline()

    // ---- Lecturas locales (funcionan sin conexión) ----

    fun salas() = dao.salas()

    fun reservas(fecha: String) = dao.reservas(fecha)

    fun usuarios() = dao.usuarios()

    // ---- Llamadas al backend ----

    private fun normalizarCorreo(correo: String) = correo.trim().lowercase()

    private fun mensajeDeError(resp: Response<*>): String? {
        val texto = resp.errorBody()?.string() ?: return null
        return try {
            gson.fromJson(texto, ErrorResponse::class.java)?.mensaje
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun <T> llamar(
        requiereSesion: Boolean = true,
        bloque: suspend () -> Response<T>
    ): Res<T> {
        if (!red.isOnline()) return Res.SinConexion

        return try {
            val resp = bloque()

            if (resp.isSuccessful) {
                val cuerpo = resp.body()
                if (cuerpo != null) {
                    Res.Ok(cuerpo)
                } else {
                    Res.Fallo("El servidor respondió sin datos.")
                }
            } else {
                val codigo = resp.code()
                when {
                    codigo == 401 && requiereSesion -> Res.SesionExpirada
                    codigo == 403 -> Res.Fallo("No tienes permisos para realizar esta acción.")
                    else -> Res.Fallo(
                        mensajeDeError(resp)
                            ?: if (codigo == 400) {
                                "Datos inválidos. Revisa la información ingresada."
                            } else {
                                "Error del servidor ($codigo)."
                            }
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            // Sin internet, servidor apagado, timeout, etc.
            Res.SinConexion
        } catch (_: Exception) {
            Res.Fallo("Ocurrió un error inesperado.")
        }
    }

    // ---- Autenticación ----

    suspend fun login(correo: String, password: String): Res<Usuario> {
        val token: String = when (
            val r = llamar(requiereSesion = false) {
                api.login(LoginRequest(normalizarCorreo(correo), password))
            }
        ) {
            is Res.Ok -> r.dato.token
            is Res.Fallo -> return r
            Res.SinConexion -> return Res.SinConexion
            Res.SesionExpirada -> return Res.SesionExpirada
        }

        session.guardarToken(token)

        // El login solo entrega el token: pedimos el perfil (id, nombre, rol).
        val perfil = llamar { api.perfil() }
        if (perfil !is Res.Ok) {
            session.limpiar()
        }

        return perfil.mapear { dto ->
            val usuario = dto.toEntity()
            session.guardarUsuario(usuario)
            usuario
        }
    }

    suspend fun registrar(nombre: String, correo: String, password: String): Res<Unit> =
        llamar(requiereSesion = false) {
            api.registro(
                RegistroRequest(
                    nombre = nombre.trim(),
                    correo = normalizarCorreo(correo),
                    password = password
                )
            )
        }.mapear { }

    /** Pide el perfil al backend, actualiza la sesión guardada y confirma que hay conexión. */
    suspend fun refrescarPerfil(): Res<Usuario> =
        llamar { api.perfil() }.mapear { dto ->
            val usuario = dto.toEntity()
            session.guardarUsuario(usuario)
            usuario
        }

    /**
     * Recupera la sesión al abrir la app.
     * Con conexión valida el token y actualiza el rol; sin conexión usa el perfil guardado.
     */
    suspend fun restaurarSesion(): Usuario? {
        if (session.token().isEmpty()) return null
        val local = session.usuario() ?: return null

        return when (val r = refrescarPerfil()) {
            is Res.Ok -> r.dato
            Res.SesionExpirada -> {
                session.limpiar()
                null
            }
            else -> local
        }
    }

    suspend fun cerrarSesion() {
        session.limpiar()
    }

    // ---- Usuarios y roles (backend) ----

    /** Descarga la lista de usuarios (solo coordinadores) y la guarda en Room. */
    suspend fun sincronizarUsuarios(): Res<Unit> =
        llamar { api.usuarios() }.mapear { lista ->
            db.withTransaction {
                dao.borrarUsuarios()
                dao.guardarUsuarios(lista.map { it.toEntity() })
            }
        }

    suspend fun cambiarRol(id: Long, nuevoRol: String): Res<String> =
        llamar { api.cambiarRol(id, CambiarRolRequest(nuevoRol)) }
            .mapear { it.mensaje ?: "Rol actualizado correctamente." }

    // ---- Reservas (solo Room) ----

    suspend fun reservarGrupo(reservas: List<Reserva>): Boolean {
        if (reservas.isEmpty()) return false

        return try {
            dao.insertarGrupo(reservas)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Normalmente SQLiteConstraintException: algún bloque ya estaba ocupado.
            false
        }
    }

    suspend fun cancelarReserva(reservaId: Long) {
        dao.eliminarReserva(reservaId)
    }

    // ---- Catálogo de salas ----

    /** Las salas no vienen del backend: se cargan una sola vez en Room. */
    suspend fun sembrarSalas() {
        if (dao.contarSalas() > 0) return

        dao.guardarSalas(
            listOf(
                Sala(1, "Sala A101", 30, true, false, true),
                Sala(2, "Lab Computación 1", 20, true, true, true),
                Sala(3, "Sala B201", 40, false, false, false),
                Sala(4, "Multiuso Auditorio", 80, true, false, true)
            )
        )
    }
}
