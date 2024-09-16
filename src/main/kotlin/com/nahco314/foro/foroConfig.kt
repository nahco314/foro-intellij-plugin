package com.nahco314.foro

import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveFileDocumentManagerListener
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Path

//val FORO_COMMAND = when {
//    SystemInfo.isWindows -> "foro.exe"
//    else -> "foro"
//}

val FORO_COMMAND = "foro"

class ForoConfigException(message: String): Exception(message)

data class ForoConfigResult(
    val foroExecutablePath: Path,
    val configFile: Path,
    val cacheDir: Path,
    val socketDir: Path
)

fun findForoFromPath(): Path? {
    val path = System.getenv("PATH")
    val paths = path.split(File.pathSeparator)
    return paths.map { Path.of(it) }
        .map { it.resolve(FORO_COMMAND) }
        .firstOrNull { it.toFile().exists() }
}

fun loadForoConfig(givenForoExecutablePath: Path?, givenConfigFile: Path?, givenCacheDir: Path?, givenSocketDir: Path?): ForoConfigResult {
    val foroExecutablePath = givenForoExecutablePath ?: findForoFromPath()

    if (foroExecutablePath == null) {
        throw ForoConfigException("Foro executable not found. Please set the path to the Foro executable.")
    }

    val inputMap = mapOf(
        "given_config_file" to givenConfigFile,
        "given_cache_dir" to givenCacheDir,
        "given_socket_dir" to givenSocketDir
    )

    val inputJson = Json.encodeToString(inputMap)

    val tmp = File.createTempFile("foro-idea-tmp", null)
    tmp.deleteOnExit()
    tmp.writer().write(inputJson)

    val parts = arrayOf(foroExecutablePath.toString(), "internal", "info")

    // /home/nahco314/RustroverProjects/foro/target/release/foro

    val result: ForoConfigResult

    try {
        val proc = ProcessBuilder(*parts)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()

        val writer = proc.outputWriter()
        writer.write(inputJson)
        writer.flush()
        writer.close()

        proc.waitFor()

        val reader = proc.inputReader()
        val outputString = reader.use { it.readText() }
        val outputMap = Json.decodeFromString<Map<String, String>>(outputString)

        result = ForoConfigResult(
            foroExecutablePath,
            Path.of(outputMap["config_file"]!!),
            Path.of(outputMap["cache_dir"]!!),
            Path.of(outputMap["socket_dir"]!!)
        )
    } catch (e: Exception) {
        throw ForoConfigException("Error running Foro: ${e.message}")
    }

    return result
}

class ForoActionOnSave : ActionsOnSaveFileDocumentManagerListener.ActionOnSave() {
    override fun isEnabledForProject(project: Project): Boolean {
        return true
    }

    override fun processDocuments(project: Project, documents: Array<Document?>) {
        val undoManager = UndoManager.getInstance(project)
        val psiDocumentManager = PsiDocumentManager.getInstance(project)
        val foroSettings = ForoSettings.getInstance()

        println(foroSettings.state)

        if (!foroSettings.state.runFormatOnSave) {
            return
        }

        val formatter = ForoFormatter()

        for (document in documents) {
            if (document == null) continue

            val psiFile = psiDocumentManager.getPsiFile(document) ?: continue
            val path = psiFile.virtualFile.path
            val parent = psiFile.virtualFile.parent.path

            val args = FormatArgs(
                Path.of(path),
                psiFile.text,
                Path.of(project.basePath ?: parent),
                Path.of(foroSettings.state.foroExecutablePath),
                foroSettings.state.configFile!!,
                foroSettings.state.cacheDir!!,
                foroSettings.state.socketDir!!
            )

            val result: FormatResult

            try {
                result = formatter.format(args)
            } catch (e: ForoUnexpectedErrorException) {
                val notification = Notification(
                    "Foro",
                    "Foro error",
                    String.format("Error formatting file: %s", e.message),
                    NotificationType.ERROR
                )
                notification.addAction(object : NotificationAction("Open settings") {
                    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                        val dataContext = e.dataContext;
                        val p = dataContext.getData("project") as Project
                        ShowSettingsUtil.getInstance().showSettingsDialog(p, ForoApplicationConfigurable::class.java)
                    }
                })

                Notifications.Bus.notify(notification, project)

                foroSettings.state.runFormatOnSave = false

                return
            }

            println(result)

            when (result) {
                is FormatResult.Success -> {}
                is FormatResult.Ignored -> continue
                is FormatResult.Error -> continue
            }

            runInEdt {
                runWriteAction {
                    CommandProcessor.getInstance().executeCommand(project, {
                        if (!undoManager.isUndoInProgress) {
                            document.setText(result.formattedContent)
                        }
                    }, "Foro Format", "Foro Format")
                }
            }
        }
    }
}