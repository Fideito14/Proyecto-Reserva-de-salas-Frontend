package cl.DSY1105.reservasalas.data.repository

/** Resultado de una operación contra el backend. */
sealed class Res<out T> {
    data class Ok<out T>(val dato: T) : Res<T>()

    /** El backend respondió con un error (credenciales, validación, permisos...). */
    data class Fallo(val mensaje: String) : Res<Nothing>()

    /** No hay red o no se pudo llegar al servidor. */
    data object SinConexion : Res<Nothing>()

    /** El token venció o ya no es válido (HTTP 401). */
    data object SesionExpirada : Res<Nothing>()
}

/** Transforma el dato de un resultado exitoso y deja pasar los errores tal cual. */
inline fun <T, R> Res<T>.mapear(transformar: (T) -> R): Res<R> = when (this) {
    is Res.Ok -> Res.Ok(transformar(dato))
    is Res.Fallo -> Res.Fallo(mensaje)
    Res.SinConexion -> Res.SinConexion
    Res.SesionExpirada -> Res.SesionExpirada
}
