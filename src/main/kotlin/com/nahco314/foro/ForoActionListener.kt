package com.nahco314.foro

import com.intellij.ide.actions.SaveAllAction
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import io.ktor.util.reflect.*

class ForoActionListener : AnActionListener {
    override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
        if (action.instanceOf(SaveAllAction::class)) {
            service<ForoDetectManualSaveService>().setSavingManually(true)
        }
    }

    override fun afterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult) {
        if (action.instanceOf(SaveAllAction::class)) {
            service<ForoDetectManualSaveService>().setSavingManually(false)
        }
    }
}