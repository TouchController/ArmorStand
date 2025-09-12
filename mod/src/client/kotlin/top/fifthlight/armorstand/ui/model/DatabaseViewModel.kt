package top.fifthlight.armorstand.ui.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.fifthlight.armorstand.manage.ModelManagerHolder
import top.fifthlight.armorstand.ui.state.DatabaseScreenState
import java.sql.ResultSet
import kotlin.time.measureTimedValue

class DatabaseViewModel(scope: CoroutineScope) : ViewModel(scope) {
    private val _uiState = MutableStateFlow(DatabaseScreenState())
    val uiState = _uiState.asStateFlow()

    fun updateQuery(newQuery: String) {
        _uiState.value = _uiState.value.copy(query = newQuery)
    }

    fun submitQuery() {
        scope.launch {
            _uiState.value = _uiState.value.copy(state = DatabaseScreenState.QueryState.Loading)

            try {
                val result = withContext(Dispatchers.IO) {
                    executeQuery(_uiState.value.query)
                }
                _uiState.value = _uiState.value.copy(state = result)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(state = DatabaseScreenState.QueryState.Failed(e.message))
            }
        }
    }

    private suspend fun executeQuery(query: String): DatabaseScreenState.QueryState =
        ModelManagerHolder.instance.databaseManager.transaction {
            connection.createStatement().use { stmt ->
                val (value, duration) = measureTimedValue {
                    stmt.execute(query)
                }
                if (value) {
                    val (headers, rows) = parseResultSet(stmt.resultSet)
                    DatabaseScreenState.QueryState.Result(duration, headers, rows)
                } else {
                    DatabaseScreenState.QueryState.Updated(duration, stmt.updateCount)
                }
            }
        }

    private fun parseResultSet(resultSet: ResultSet): Pair<List<String>, List<List<String>>> {
        val metaData = resultSet.metaData
        val headers = (1..metaData.columnCount).map { metaData.getColumnName(it) }
        val rows = buildList {
            while (resultSet.next()) {
                add((1..metaData.columnCount).map {
                    resultSet.getString(it) ?: "null" }
                )
            }
        }
        return Pair(headers, rows)
    }
}

