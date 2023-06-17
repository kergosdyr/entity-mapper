package org.kibo.entitymapper

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import org.kibo.entitymapper.dialog.MapperDialog

class EntityMapperAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        val document = editor.document
        val caretModel = editor.caretModel
        val offset = caretModel.offset
        val psiFileNow = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return
        val elementAt = psiFileNow.findElementAt(offset)
        val currentClass = PsiTreeUtil.getParentOfType(elementAt, PsiClass::class.java) ?: return

        val dialog = MapperDialog(project, currentClass)
        dialog.showAndGet()

    }



}
