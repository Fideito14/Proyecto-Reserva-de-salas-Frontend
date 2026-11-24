package cl.DSY1105.reservasalas.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import cl.DSY1105.reservasalas.ui.views.AgendaScreen
import cl.DSY1105.reservasalas.ui.views.LoginScreen
import cl.DSY1105.reservasalas.ui.views.RegistroScreen
import cl.DSY1105.reservasalas.ui.views.UsuariosScreen
import cl.DSY1105.reservasalas.viewmodel.AgendaViewModel

private const val LOGIN = "login"
private const val REGISTRO = "registro"
private const val AGENDA = "agenda"
private const val USUARIOS = "usuarios"

@Composable
fun AppNavigation(
    navHostController: NavHostController,
    vm: AgendaViewModel
) {
    val sesionRestaurada by vm.sesionInicialRestaurada.collectAsState()
    val email by vm.emailActual.collectAsState()
    val entrada by navHostController.currentBackStackEntryAsState()
    val ruta = entrada?.destination?.route

    // La navegación depende solo del estado de la sesión:
    //  - con sesión  -> agenda
    //  - sin sesión  -> login (también cuando el token expira o se cierra sesión)
    LaunchedEffect(sesionRestaurada, email, ruta) {
        if (!sesionRestaurada || ruta == null) return@LaunchedEffect

        val conSesion = email.isNotBlank()

        if (conSesion && (ruta == LOGIN || ruta == REGISTRO)) {
            navHostController.navigate(AGENDA) {
                popUpTo(navHostController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        } else if (!conSesion && (ruta == AGENDA || ruta == USUARIOS)) {
            navHostController.navigate(LOGIN) {
                popUpTo(navHostController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // Mientras se recupera la sesión guardada no se muestra el login para evitar parpadeos.
    if (!sesionRestaurada) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        // Se calcula una sola vez: cambiarlo después reiniciaría la pila de navegación.
        val inicio = remember { if (email.isNotBlank()) AGENDA else LOGIN }

        NavHost(
            navController = navHostController,
            startDestination = inicio
        ) {
            composable(LOGIN) {
                LoginScreen(
                    vm,
                    onRegistro = {
                        navHostController.navigate(REGISTRO)
                    }
                )
            }

            composable(REGISTRO) {
                RegistroScreen(
                    vm,
                    onVolver = {
                        navHostController.popBackStack()
                    },
                    onOk = {
                        navHostController.popBackStack()
                    }
                )
            }

            composable(AGENDA) {
                AgendaScreen(
                    vm,
                    onUsuarios = {
                        navHostController.navigate(USUARIOS)
                    },
                    onSalir = {
                        vm.cerrarSesion()
                    }
                )
            }

            composable(USUARIOS) {
                UsuariosScreen(
                    vm,
                    onVolver = {
                        navHostController.popBackStack()
                    }
                )
            }
        }
    }
}
