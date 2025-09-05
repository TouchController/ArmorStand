package top.fifthlight.blazerod.model.pmx

internal class VertexMaterialTable(
    vertexCount: Int,
    private val materialCount: Int,
) {
    private val table = IntArray(vertexCount * materialCount) { -1 }

    private fun index(vertexIndex: Int, materialIndex: Int) = vertexIndex * materialCount + materialIndex

    fun setLocalIndex(vertexIndex: Int, materialIndex: Int, localIndex: Int) {
        table[index(vertexIndex, materialIndex)] = localIndex
    }

    fun getLocalIndex(vertexIndex: Int, materialIndex: Int): Int {
        return table[index(vertexIndex, materialIndex)]
    }
}

