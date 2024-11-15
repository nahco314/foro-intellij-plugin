package com.nahco314.foro

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import java.nio.file.Path

internal class ForoFileSaveListener(private val project: Project) : FileDocumentManagerListener {
    private lateinit var handler: ForoEditorFormatHandler

    override fun beforeDocumentSaving(document: Document) {
        if (!::handler.isInitialized) {
            handler = ForoEditorFormatHandler(project)
        }

        val isManualSave = service<ForoDetectManualSaveService>().isSavingManually()

        handler.format(document, !isManualSave)
    }
}
