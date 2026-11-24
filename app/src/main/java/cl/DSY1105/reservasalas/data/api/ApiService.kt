package cl.DSY1105.reservasalas.data.api

import cl.DSY1105.reservasalas.data.dto.CambiarRolRequest
import cl.DSY1105.reservasalas.data.dto.CambiarRolResponse
import cl.DSY1105.reservasalas.data.dto.LoginRequest
import cl.DSY1105.reservasalas.data.dto.LoginResponse
import cl.DSY1105.reservasalas.data.dto.RegistroRequest
import cl.DSY1105.reservasalas.data.dto.RegistroResponse
import cl.DSY1105.reservasalas.data.dto.UsuarioDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** El backend solo maneja autenticación y usuarios. Las reservas viven en Room. */
interface ApiService {

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("auth/registro")
    suspend fun registro(@Body request: RegistroRequest): Response<RegistroResponse>

    // Perfil del usuario autenticado (sirve también para validar el token)
    @GET("usuarios/me")
    suspend fun perfil(): Response<UsuarioDto>

    // Solo coordinadores
    @GET("usuarios")
    suspend fun usuarios(): Response<List<UsuarioDto>>

    // Solo coordinadores
    @PUT("usuarios/{id}/rol")
    suspend fun cambiarRol(
        @Path("id") id: Long,
        @Body request: CambiarRolRequest
    ): Response<CambiarRolResponse>
}
