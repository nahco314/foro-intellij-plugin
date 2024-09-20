package com.nahco314.foro

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
import java.nio.file.Path

class ForoEditorFormatHandler(val project: Project) {
    private val undoManager: UndoManager = UndoManager.getInstance(project)
    private val psiDocumentManager: PsiDocumentManager = PsiDocumentManager.getInstance(project)
    private val foroSettings: ForoSettings = ForoSettings.getInstance()
    private val commandProcessor: CommandProcessor = CommandProcessor.getInstance()

    fun format(document: Document, isAutoSave: Boolean) {
        val start = System.currentTimeMillis()

        if (!foroSettings.state.enabled) {
            return
        }

        if ((!foroSettings.state.autoFormatOnSave) && isAutoSave) {
            return
        }

        val psiFile = psiDocumentManager.getPsiFile(document) ?: return

        if ((psiFile.modificationStamp != document.modificationStamp) && isAutoSave) {
            return
        }

        val formatter = ForoFormatter()

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

            foroSettings.state.autoFormatOnSave = false

            return
        }

        when (result) {
            is FormatResult.Success -> {}
            is FormatResult.Ignored -> return
            is FormatResult.Error -> return
        }

        runInEdt {
            runWriteAction {
                commandProcessor.executeCommand(project, {
                    if (undoManager.isUndoInProgress) {
                        return@executeCommand
                    }

                    document.setText(result.formattedContent)
                }, "Foro Format", "Foro Format")
            }
        }

        val end = System.currentTimeMillis()
        println("Foro: Formatted in ${end - start}ms")
    }
}