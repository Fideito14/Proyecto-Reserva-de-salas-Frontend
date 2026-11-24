package cl.DSY1105.reservasalas.data.api

import cl.DSY1105.reservasalas.data.session.SessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/** Agrega "Authorization: Bearer <jwt>" a todas las peticiones salvo login/registro. */
class AuthInterceptor(private val sessionManager: SessionManager) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        if (original.url.encodedPath.contains("/auth/")) {
            return chain.proceed(original)
        }

        // OkHttp ejecuta el interceptor en un hilo de fondo, por eso runBlocking es seguro aquí.
        val token = runBlocking { sessionManager.token() }

        val request = if (token.isNotEmpty()) {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }
        return chain.proceed(request)
    }
}
