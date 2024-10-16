package com.nahco314.foro

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import java.nio.file.Path


@Service(Service.Level.APP)
@State(name = "ForoSettings", storages = [Storage("foro.xml")])
class ForoSettings : SimplePersistentStateComponent<ForoSettingsState>(ForoSettingsState()) {
    companion object {
        fun getInstance(): ForoSettings {
            return ApplicationManager.getApplication()
                .getService(ForoSettings::class.java)
        }
    }
}

class ForoSettingsState : BaseState() {
    var enabled: Boolean = false
    var autoFormatOnSave: Boolean = false
    var foroExecutablePath: String = ""
    var givenConfigFile: String = ""
    var givenCacheDir: String = ""
    var givenSocketDir: String = ""
    var configFile: Path? = null
    var cacheDir: Path? = null
    var socketDir: Path? = null
}
