package cl.DSY1105.reservasalas.data.dto

import cl.DSY1105.reservasalas.data.room.Usuario

// Contrato del backend Spring Boot (/api/auth/** y /api/usuarios/**)

// POST /api/auth/login
data class LoginRequest(
    val correo: String,
    val password: String
)

data class LoginResponse(
    val mensaje: String?,
    val token: String
)

// POST /api/auth/registro
data class RegistroRequest(
    val nombre: String,
    val correo: String,
    val password: String
)

data class RegistroResponse(
    val mensaje: String?,
    val nombre: String?,
    val correo: String?,
    val rol: String?
)

// GET /api/usuarios/me  y  GET /api/usuarios
data class UsuarioDto(
    val id: Long,
    val nombre: String,
    val correo: String,
    val rol: String,
    val coordinadorPrincipal: Boolean
)

// PUT /api/usuarios/{id}/rol
data class CambiarRolRequest(
    val rol: String
)

data class CambiarRolResponse(
    val mensaje: String?,
    val usuario: UsuarioDto?
)

// Cuerpo de error del backend: {"mensaje": "..."}
data class ErrorResponse(
    val mensaje: String?
)

fun UsuarioDto.toEntity(): Usuario = Usuario(
    id = id,
    correo = correo,
    nombre = nombre,
    rol = rol,
    coordinadorPrincipal = coordinadorPrincipal
)
