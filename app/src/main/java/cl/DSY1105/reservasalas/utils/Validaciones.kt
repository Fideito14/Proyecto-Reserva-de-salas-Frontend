package cl.DSY1105.reservasalas.utils

private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

fun esEmailValido(email: String): Boolean = EMAIL_REGEX.matches(email.trim())

// El backend exige mínimo 8 caracteres.
fun esPasswordValida(p: String): Boolean = p.length >= 8
