package top.fifthlight.armorstand.manage.scan

interface ModelScanner {
    suspend fun scan(fileHandler: FileHandler)
}
