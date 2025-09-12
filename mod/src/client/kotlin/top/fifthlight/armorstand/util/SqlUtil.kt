package top.fifthlight.armorstand.util

import java.sql.PreparedStatement
import java.sql.ResultSet

class ParamBinderScope(private val stmt: PreparedStatement) {
    private var index = 1

    fun string(value: String?) {
        stmt.setString(index++, value)
    }

    fun int(value: Int) {
        stmt.setInt(index++, value)
    }

    fun long(value: Long) {
        stmt.setLong(index++, value)
    }

    fun boolean(value: Boolean) {
        stmt.setBoolean(index++, value)
    }

    fun bytes(value: ByteArray?) {
        stmt.setBytes(index++, value)
    }
}

fun PreparedStatement.bind(block: ParamBinderScope.() -> Unit): PreparedStatement {
    try {
        ParamBinderScope(this).apply(block)
    } catch (ex: Exception) {
        close()
        throw ex
    }
    return this
}

fun <T> PreparedStatement.withExecuted(block: ResultSet.() -> T): T = use {
    executeQuery().use {
        block(it)
    }
}

fun PreparedStatement.exists() = withExecuted { next() }

fun PreparedStatement.count() = withExecuted {
    require(next()) { "No result when counting" }
    getInt(1)
}

fun <T> ResultSet.map(transform: ResultSet.() -> T): List<T> = buildList {
    while (next()) {
        add(transform(this@map))
    }
}

fun <T> ResultSet.first(transform: ResultSet.() -> T): T? = if (next()) {
    transform(this)
} else {
    null
}

fun <T> PreparedStatement.mapExecuted(transform: ResultSet.() -> T): List<T> = withExecuted { map(transform) }

fun <T> PreparedStatement.firstExecuted(transform: ResultSet.() -> T): T? = withExecuted { first(transform) }
