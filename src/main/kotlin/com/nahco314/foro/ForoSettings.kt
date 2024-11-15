package com.nahco314.foro

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil.copyBean


@Service(Service.Level.APP)
@State(name = "ForoSettings", storages = [Storage("foro.xml")])
class ForoSettings : PersistentStateComponent<ForoSettings> {
    var enabled: Boolean = false
    var formatOnManualSave: Boolean = false
    var formatOnAutoSave: Boolean = false
    var givenForoExecutablePath: String = ""
    var givenConfigFile: String = ""
    var givenCacheDir: String = ""
    var givenSocketDir: String = ""
    var configFile: String? = null
    var cacheDir: String? = null
    var socketDir: String? = null
    var foroExecutablePath: String? = null

    override fun getState() = this

    override fun loadState(state: ForoSettings) {
        copyBean(state, this)
    }

    companion object {
        fun getInstance(): ForoSettings {
            return ApplicationManager.getApplication()
                .getService(ForoSettings::class.java)
        }
    }
}
