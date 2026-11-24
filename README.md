# Reserva de Salas (Android + Spring Boot + PostgreSQL)

## Cómo funciona

| Dónde | Qué guarda |
|---|---|
| **Backend** (Spring Boot + PostgreSQL) | Solo autenticación: registro, login (JWT), usuarios y roles |
| **App** (Room) | Salas y reservas, en el dispositivo |

* Con conexión: se puede reservar, cancelar y (coordinadores) cambiar roles.
* Sin conexión: solo consulta. Al intentar reservar/cancelar/cambiar roles aparece una alerta.
* Coordinador principal: lo crea el backend al iniciar (`coordinador@duocuc.cl`). Su rol no se puede quitar.
  Puede dar o quitar el rol de coordinador a los demás usuarios.

## 1. Levantar el backend

En la carpeta del backend:

```powershell
# 1) Archivo .env junto a docker-compose.yml
#    POSTGRES_PASSWORD=reserva_pass
docker compose up -d

# 2) Variables y arranque (PowerShell)
$env:DB_PASSWORD="reserva_pass"            # debe ser igual a POSTGRES_PASSWORD
$env:COORDINATOR_PASSWORD="Coordinador123" # mínimo 8 caracteres; si falta, no se crea el coordinador
.\mvnw.cmd spring-boot:run
```

En la consola debe aparecer `Coordinador principal creado correctamente.`
Queda en `http://localhost:8080`. Si cambiaste `POSTGRES_PASSWORD` después de crear el contenedor,
Postgres conserva la clave anterior: usa `docker compose down -v` y vuelve a levantar.

## 2. Apuntar la app al backend

La URL se lee de `local.properties` (raíz del proyecto, no se sube a git):

```properties
# Emulador de Android Studio (valor por defecto, no hace falta escribirlo)
api.baseUrl=http://10.0.2.2:8080/api/

# Celular real: IP de tu PC en la misma WiFi (ipconfig) y puerto 8080 abierto en el firewall
# api.baseUrl=http://192.168.1.50:8080/api/
```

Luego *Sync Project* y ejecutar. Si ya tenías la app instalada de antes, no hace falta desinstalarla
(Room migra de la versión 5 a la 6).

## 3. Probar

1. Iniciar sesión con `coordinador@duocuc.cl` y la clave de `COORDINATOR_PASSWORD`.
2. *Salir* → *Crear cuenta* (clave de 8+ caracteres) → iniciar sesión con la cuenta nueva (Relator).
3. Reservar uno o varios bloques consecutivos; cancelar una reserva propia.
4. Como coordinador: *Usuarios* → *Hacer coord.* a la cuenta nueva. Al volver a entrar a la agenda,
   esa cuenta ya ve el botón *Usuarios* y puede cancelar cualquier reserva.
5. Activar modo avión: aparece la franja "Sin conexión", la agenda se sigue viendo, y *Reservar* /
   *Cancelar* muestran la alerta.
6. Cerrar la app con modo avión y volver a abrirla: mantiene la sesión (solo lectura).

## 4. Generar el APK firmado

```powershell
keytool -genkeypair -v -keystore reservas.jks -alias reservas -keyalg RSA -keysize 2048 -validity 10000
copy keystore.properties.example keystore.properties   # completar contraseñas
.\gradlew.bat assembleRelease
```

El APK queda en `app\build\outputs\apk\release\`. Guarda `reservas.jks` y sus claves en un lugar seguro.
(Alternativa: *Build → Generate Signed App Bundle / APK* en Android Studio.)
Para un APK que se use fuera de tu red, el backend debe estar accesible desde esa red (idealmente con HTTPS).
