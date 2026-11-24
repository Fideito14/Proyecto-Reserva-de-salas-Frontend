package cl.DSY1105.reservasalas.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cl.DSY1105.reservasalas.data.api.RetrofitClient
import cl.DSY1105.reservasalas.data.network.NetworkMonitor
import cl.DSY1105.reservasalas.data.repository.Repo
import cl.DSY1105.reservasalas.data.repository.Res
import cl.DSY1105.reservasalas.data.room.AppDatabase
import cl.DSY1105.reservasalas.data.room.Reserva
import cl.DSY1105.reservasalas.data.room.Usuario
import cl.DSY1105.reservasalas.data.session.SessionManager
import cl.DSY1105.reservasalas.nativos.Notificaciones
import cl.DSY1105.reservasalas.utils.Bloques
import cl.DSY1105.reservasalas.utils.Roles
import cl.DSY1105.reservasalas.utils.hoy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AgendaViewModel(app: Application) : AndroidViewModel(app) {

    private val sessionManager = SessionManager(app)

    private val repo = Repo(
        api = RetrofitClient.create(sessionManager),
        db = AppDatabase.get(app),
        session = sessionManager,
        red = NetworkMonitor(app)
    )

    // ---- Estado de sesión ----
    val usuarioId = MutableStateFlow(0L)
    val relator = MutableStateFlow("")
    val emailActual = MutableStateFlow("")
    val esCoordinador = MutableStateFlow(false)
    val sesionInicialRestaurada = MutableStateFlow(false)

    // ---- Conectividad ----
    val online = repo.online.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        repo.hayRed()
    )

    // Alerta (diálogo) que se muestra al intentar reservar/cancelar/cambiar roles sin conexión.
    val alertaConexion = MutableStateFlow<String?>(null)

    // ---- Filtros ----
    val fecha = MutableStateFlow(hoy())
    val capMin = MutableStateFlow(0)
    val soloProyector = MutableStateFlow(false)

    // ---- Mensajes y carga ----
    val mensaje = MutableStateFlow<String?>(null)
    val error = MutableStateFlow<String?>(null)
    val aviso = MutableStateFlow<String?>(null)
    val cargando = MutableStateFlow(false)

    // ---- Reservas ----
    val reservas = fecha.flatMapLatest { fechaSeleccionada ->
        repo.reservas(fechaSeleccionada)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        emptyList()
    )

    // ---- Salas ----
    val salas = combine(
        repo.salas(),
        capMin,
        soloProyector
    ) { lista, capacidad, proyector ->
        lista.filter {
            it.capacidad >= capacidad &&
                    (!proyector || it.proyector)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        emptyList()
    )

    // ---- Usuarios (copia local de lo que devuelve el backend) ----
    val usuarios = repo.usuarios().stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        emptyList()
    )

    // ---- Inicialización ----
    init {
        viewModelScope.launch {
            try {
                repo.sembrarSalas()
            } catch (e: Exception) {
                error.value = "No se pudieron cargar los datos locales."
            }
        }

        viewModelScope.launch {
            try {
                val usuario = repo.restaurarSesion()

                if (usuario != null) {
                    aplicarUsuario(usuario)
                } else {
                    limpiarSesion()
                }
            } catch (e: Exception) {
                limpiarSesion()
            } finally {
                sesionInicialRestaurada.value = true
            }
        }
    }

    private fun aplicarUsuario(usuario: Usuario) {
        usuarioId.value = usuario.id
        relator.value = usuario.nombre
        emailActual.value = usuario.correo
        esCoordinador.value = usuario.esCoordinador
    }

    // ---- Inicio de sesión ----
    fun iniciarSesion(email: String, pass: String) = viewModelScope.launch {

        if (cargando.value) return@launch

        cargando.value = true
        error.value = null
        aviso.value = null

        try {
            when (val r = repo.login(email, pass)) {
                is Res.Ok -> aplicarUsuario(r.dato)
                is Res.Fallo -> error.value = r.mensaje
                Res.SinConexion -> error.value = MSG_LOGIN_SIN_CONEXION
                Res.SesionExpirada -> error.value = "No se pudo iniciar sesión."
            }
        } catch (e: Exception) {
            error.value = "Ocurrió un error al iniciar sesión."
        } finally {
            cargando.value = false
        }
    }

    // ---- Registro ----
    fun registrar(
        nombre: String,
        email: String,
        pass: String,
        onOk: () -> Unit
    ) = viewModelScope.launch {

        if (cargando.value) return@launch

        cargando.value = true
        error.value = null
        aviso.value = null

        try {
            when (val r = repo.registrar(nombre, email, pass)) {
                is Res.Ok -> {
                    aviso.value = "Cuenta creada. Ahora inicia sesión."
                    onOk()
                }
                is Res.Fallo -> error.value = r.mensaje
                Res.SinConexion -> error.value = MSG_REGISTRO_SIN_CONEXION
                Res.SesionExpirada -> error.value = "No se pudo crear la cuenta."
            }
        } catch (e: Exception) {
            error.value = "Ocurrió un error al crear la cuenta."
        } finally {
            cargando.value = false
        }
    }

    // ---- Cierre de sesión ----
    fun cerrarSesion() = viewModelScope.launch {
        repo.cerrarSesion()
        limpiarSesion()
    }

    private fun limpiarSesion() {
        usuarioId.value = 0L
        relator.value = ""
        emailActual.value = ""
        esCoordinador.value = false
        error.value = null
        aviso.value = null
        mensaje.value = null
        alertaConexion.value = null
    }

    // El token venció (o el usuario ya no existe): volver al login.
    private suspend fun expirarSesion() {
        repo.cerrarSesion()
        limpiarSesion()
        aviso.value = "Tu sesión expiró. Vuelve a iniciar sesión."
    }

    // ---- Conexión ----

    fun avisarSinConexion() {
        alertaConexion.value = MSG_SIN_CONEXION
    }

    /**
     * Confirma que hay conexión con el servidor (y que la sesión sigue vigente) antes de
     * reservar, cancelar o cambiar roles. También actualiza el rol del usuario.
     */
    private suspend fun exigirConexion(): Boolean {
        return when (val r = repo.refrescarPerfil()) {
            is Res.Ok -> {
                aplicarUsuario(r.dato)
                true
            }
            Res.SinConexion -> {
                avisarSinConexion()
                false
            }
            Res.SesionExpirada -> {
                expirarSesion()
                false
            }
            is Res.Fallo -> {
                mensaje.value = r.mensaje
                false
            }
        }
    }

    // Se llama al entrar a la agenda: actualiza el rol si hay conexión (silencioso si no).
    fun refrescarPerfil() = viewModelScope.launch {
        when (val r = repo.refrescarPerfil()) {
            is Res.Ok -> aplicarUsuario(r.dato)
            Res.SesionExpirada -> expirarSesion()
            else -> Unit
        }
    }

    // ---- Gestión de usuarios y roles (solo coordinadores) ----

    // Descarga la lista del backend. Sin conexión se muestra la última copia guardada.
    fun cargarUsuarios() = viewModelScope.launch {
        when (repo.sincronizarUsuarios()) {
            Res.SesionExpirada -> expirarSesion()
            else -> Unit
        }
    }

    fun cambiarRol(destino: Usuario, hacerCoordinador: Boolean) = viewModelScope.launch {

        if (!esCoordinador.value) return@launch

        if (destino.coordinadorPrincipal) {
            mensaje.value = "No se puede modificar el rol del coordinador principal."
            return@launch
        }

        if (destino.id == usuarioId.value) {
            mensaje.value = "No puedes modificar tu propio rol."
            return@launch
        }

        if (cargando.value) return@launch
        cargando.value = true

        try {
            val nuevoRol = if (hacerCoordinador) Roles.COORDINADOR else Roles.RELATOR

            when (val r = repo.cambiarRol(destino.id, nuevoRol)) {
                is Res.Ok -> {
                    mensaje.value = r.dato
                    repo.sincronizarUsuarios()
                }
                Res.SinConexion -> avisarSinConexion()
                Res.SesionExpirada -> expirarSesion()
                is Res.Fallo -> mensaje.value = r.mensaje
            }
        } catch (e: Exception) {
            mensaje.value = "No se pudo cambiar el rol."
        } finally {
            cargando.value = false
        }
    }

    // ---- Reservas por grupo de bloques (se guardan en Room, requieren conexión) ----

    // Permite reservar uno o varios bloques consecutivos.
    fun reservarBloques(
        salaId: Long,
        bloques: List<Int>,
        actividad: String
    ) = viewModelScope.launch {

        try {
            val bloquesOrdenados = bloques.distinct().sorted()

            // Validar que existan bloques seleccionados.
            if (bloquesOrdenados.isEmpty()) {
                mensaje.value = "Selecciona al menos un bloque."
                return@launch
            }

            // Validar índices de los bloques.
            if (bloquesOrdenados.any { it !in Bloques.lista.indices }) {
                mensaje.value = "Seleccionaste un bloque inválido."
                return@launch
            }

            // Validar que sean consecutivos.
            if (bloquesOrdenados.zipWithNext().any { (a, b) -> b != a + 1 }) {
                mensaje.value = "Selecciona bloques consecutivos."
                return@launch
            }

            if (actividad.isBlank()) {
                mensaje.value = "Debes ingresar una actividad."
                return@launch
            }

            // No se puede reservar en fechas pasadas (formato yyyy-MM-dd: se compara como texto).
            if (fecha.value < hoy()) {
                mensaje.value = "No puedes reservar en fechas pasadas."
                return@launch
            }

            // Sin conexión solo se puede ver: se muestra la alerta y no se reserva.
            if (!exigirConexion()) return@launch

            // Un mismo identificador para todos los bloques.
            val grupoId = java.util.UUID.randomUUID().toString()

            val reservasGrupo = bloquesOrdenados.map { bloque ->
                Reserva(
                    salaId = salaId,
                    fecha = fecha.value,
                    bloque = bloque,
                    relator = relator.value,
                    actividad = actividad.trim(),
                    grupoId = grupoId,
                    relatorCorreo = emailActual.value
                )
            }

            val ok = repo.reservarGrupo(reservasGrupo)

            if (ok) {
                val horarios = bloquesOrdenados.joinToString(", ") {
                    Bloques.lista[it]
                }

                Notificaciones.mostrar(
                    getApplication<Application>(),
                    "Reserva confirmada",
                    "${actividad.trim()} · $horarios · ${fecha.value}"
                )
            }

            mensaje.value = if (ok) {
                "Reserva confirmada correctamente."
            } else {
                "Uno o más bloques ya están reservados."
            }

        } catch (e: Exception) {
            mensaje.value = "No se pudo realizar la reserva."
        }
    }

    // ---- Cancelación (requiere conexión) ----

    fun cancelar(r: Reserva) = viewModelScope.launch {
        try {
            if (!exigirConexion()) return@launch

            // El coordinador cancela cualquier reserva; el relator solo las suyas.
            val esDuenio = r.relatorCorreo.isNotEmpty() && r.relatorCorreo == emailActual.value
            if (!esCoordinador.value && !esDuenio) {
                mensaje.value = "Solo puedes cancelar tus propias reservas."
                return@launch
            }

            repo.cancelarReserva(r.id)
            mensaje.value = "Bloque cancelado correctamente."

        } catch (e: Exception) {
            mensaje.value = "No se pudo cancelar el bloque."
        }
    }

    private companion object {
        const val MSG_SIN_CONEXION =
            "Necesitas conexión a internet y al servidor para reservar, cancelar reservas " +
                    "o cambiar roles. Sin conexión solo puedes consultar la agenda."
        const val MSG_LOGIN_SIN_CONEXION =
            "Necesitas conexión a internet y que el servidor esté disponible para iniciar sesión."
        const val MSG_REGISTRO_SIN_CONEXION =
            "Necesitas conexión a internet y que el servidor esté disponible para crear tu cuenta."
    }
}
