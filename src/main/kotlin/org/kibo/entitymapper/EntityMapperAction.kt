package org.kibo.entitymapper

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import org.kibo.entitymapper.dialog.MapperDialog

class EntityMapperAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val psiFile = event.getData(CommonDataKeys.PSI_FILE) ?: return;

        val dialog = MapperDialog(project, psiFile)
        dialog.showAndGet()

    }

    private fun findAllClassesInProject(project: Project): Array<PsiClass> {
        return JavaPsiFacade.getInstance(project).findPackage("")?.getClasses(GlobalSearchScope.allScope(project))
                ?: emptyArray()
    }

    private fun showSourceClassSelectionPopup(classes: Array<PsiClass>, project: Project) {
        val classNames = classes.map { it.qualifiedName ?: "" }

        JBPopupFactory.getInstance().createListPopup(
                object : BaseListPopupStep<String>("Select Source Class", classNames) {
                    override fun onChosen(selectedValue: String, finalChoice: Boolean): PopupStep<*>? {
                        val classA = classes.find { it.qualifiedName == selectedValue }
                        if (classA != null) {
                            showDestinationClassSelectionPopup(classes, classA, project)
                        }
                        return PopupStep.FINAL_CHOICE
                    }
                }
        ).showInFocusCenter()
    }

    private fun showDestinationClassSelectionPopup(classes: Array<PsiClass>, classA: PsiClass, project: Project) {
        val classNames = classes.map { it.qualifiedName ?: "" }

        JBPopupFactory.getInstance().createListPopup(
                object : BaseListPopupStep<String>("Select Destination Class", classNames) {
                    override fun onChosen(selectedValue: String, finalChoice: Boolean): PopupStep<*>? {
                        val classB = classes.find { it.qualifiedName == selectedValue }
                        if (classB != null) {
                            showMethodInputDialog(classA, classB, project)
                        }
                        return PopupStep.FINAL_CHOICE
                    }
                }
        ).showInFocusCenter()
    }

    private fun showMethodInputDialog(classA: PsiClass, classB: PsiClass, project: Project) {
        Messages.showInputDialog(project, "ENTER METHOD NAME", "Method Name", null)?.let { methodName ->
            // Code to generate methods using classA, classB, and methodName
            Messages.showMessageDialog("Methods have been generated.", "Success", Messages.getInformationIcon())
        }
    }


}
