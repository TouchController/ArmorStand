package top.fifthlight.armorstand.manage.watch

import java.nio.file.Path

interface ModelWatcher {
    fun start(onChanged: (dir: Path) -> Unit)
    fun stop()
}
