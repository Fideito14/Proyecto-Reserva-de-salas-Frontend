package cl.DSY1105.reservasalas.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import cl.DSY1105.reservasalas.utils.Roles

/** Copia local de los usuarios del backend (el id es el de PostgreSQL). */
@Entity(tableName = "usuarios")
data class Usuario(
    @PrimaryKey
    val id: Long,
    val correo: String,
    val nombre: String,
    val rol: String,
    val coordinadorPrincipal: Boolean
) {
    val esCoordinador: Boolean
        get() = rol == Roles.COORDINADOR
}
