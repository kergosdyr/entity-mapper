package org.kibo.entitymapper.dialog

import com.intellij.ide.util.TreeClassChooserFactory
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.util.childrenOfType
import com.intellij.util.ui.JBUI
import org.apache.commons.text.similarity.LevenshteinDistance
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.GridLayout
import java.util.*
import javax.swing.*

class MapperDialog(private val project: Project, private val psiFile: PsiFile) : DialogWrapper(true) {

    private var selectedSourceClass: PsiClass? = null
    private var selectedDestClass: PsiClass? = null


    private val sourceClassName = JTextField("Not selected").apply {
        isEditable = false
        border = null
    }
    private val destClassName = JTextField("Not selected").apply {
        isEditable = false
        border = null
    }

    private val setterButton = JRadioButton("Setter Style")
    private val builderButton = JRadioButton("Builder Style").apply {
        isSelected = true
    }


    private val flexibleButton = JRadioButton("Flexible Mapping")
    private val strictButton = JRadioButton("Strict Mapping").apply {
        isSelected = true
    }
    private val mappingStyleButtonGroup = ButtonGroup().apply {
        add(flexibleButton)
        add(strictButton)
    }
    private val styleButtonGroup = ButtonGroup().apply {
        add(setterButton)
        add(builderButton)
    }


    private val sourceClassBrowseButton = JButton("Browse").apply {
        addActionListener {
            val classChooser =
                TreeClassChooserFactory.getInstance(project).createAllProjectScopeChooser("Select Source Class")
            classChooser.showDialog()
            val selectedClass = classChooser.selected
            if (selectedClass != null) {
                selectedSourceClass = selectedClass
                sourceClassName.text = selectedClass.qualifiedName
            }
        }
    }

    private val destClassBrowseButton = JButton("Browse").apply {
        addActionListener {
            val classChooser =
                TreeClassChooserFactory.getInstance(project).createAllProjectScopeChooser("Select Destination Class")
            classChooser.showDialog()
            val selectedClass = classChooser.selected
            if (selectedClass != null) {
                selectedDestClass = selectedClass
                destClassName.text = selectedClass.qualifiedName
            }
        }
    }

    private val methodNameField = JTextField()
    // TODO: Add other fields

    init {
        title = "Entity Mapper"
        init()
    }
    override fun doValidate(): ValidationInfo? {
        if (sourceClassName.text.trim().isEmpty()) {
            return ValidationInfo("Source class name is required.", sourceClassName)
        }

        if (destClassName.text.trim().isEmpty()) {
            return ValidationInfo("Destination class name is required.", destClassName)
        }

        if (methodNameField.text.trim().isEmpty()) {
            return ValidationInfo("Method name is required.", methodNameField)
        }

        return super.doValidate()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())

        val c = GridBagConstraints()

        c.fill = GridBagConstraints.HORIZONTAL
        c.insets = JBUI.insets(10)

        c.gridx = 0
        c.gridy = 0
        panel.add(JLabel("Source Class:"), c)

        c.gridx = 1
        c.gridy = 0
        panel.add(sourceClassName, c)

        c.gridx = 2
        c.gridy = 0
        panel.add(sourceClassBrowseButton, c)

        c.gridx = 0
        c.gridy = 1
        panel.add(JLabel("Destination Class:"), c)

        c.gridx = 1
        c.gridy = 1
        panel.add(destClassName, c)

        c.gridx = 2
        c.gridy = 1
        panel.add(destClassBrowseButton, c)

        c.gridx = 0
        c.gridy = 2
        panel.add(JLabel("Method Name:"), c)

        c.gridx = 1
        c.gridy = 2
        panel.add(methodNameField, c)

        c.gridx = 0
        c.gridy = 3
        panel.add(JLabel("Method Style:"), c)

        val styleRadioPannel = JPanel().apply {
            GridLayout(1, 2)
            add(setterButton)
            add(builderButton)
        }

        c.gridx = 1
        c.gridy = 3
        panel.add(styleRadioPannel, c)

        c.gridx = 0
        c.gridy = 4
        panel.add(JLabel("Mapping Style:"), c)

        val mappingRadioPannel = JPanel().apply {
            GridLayout(1, 2)
            add(flexibleButton)
            add(strictButton)
        }

        c.gridx = 1
        c.gridy = 4
        panel.add(mappingRadioPannel, c)

        return panel
    }

    override fun doOKAction() {
        // TODO: Do something with the input
        val psiClass = psiFile.childrenOfType<PsiClass>()[0]


        val factory = JavaPsiFacade.getElementFactory(project)
        val method = factory.createMethodFromText(
            generateMappingMethod(
                selectedSourceClass!!,
                selectedDestClass!!,
                methodNameField.text,
                if (setterButton.isSelected) "Setter" else "Builder",
                if (strictButton.isSelected) "Strict" else "Flexible"
            ), null
        )
        WriteCommandAction.runWriteCommandAction(project) {
            psiClass.add(method)
        }


        super.doOKAction()
    }

    fun generateMappingMethod(
        sourceClass: PsiClass, destClass: PsiClass, methodName: String, style: String, mappingStyle: String
    ): String {
        val sourceFields = sourceClass.allFields
        val destFields = destClass.allFields

        val destFieldsNames = destFields.map { it.name }
        val sourceFieldsNames = sourceFields.map { it.name }
        val levenshtein = LevenshteinDistance.getDefaultInstance()

        val destToSourceSimilarityMap = HashMap<String, String>();

        if (mappingStyle == "Flexible") {
            for (destFieldName in destFieldsNames) {
                var minDistance = Int.MAX_VALUE
                var mostSimilarSourceFieldName = ""

                for (sourceFieldName in sourceFieldsNames) {
                    val distance = levenshtein.apply(destFieldName, sourceFieldName)
                    if (distance < minDistance) {
                        minDistance = distance
                        mostSimilarSourceFieldName = sourceFieldName
                    }
                }

                destToSourceSimilarityMap[destFieldName] = mostSimilarSourceFieldName
            }
        }

        val commonFields = sourceFields.map { it.name }.intersect(destFieldsNames.toSet())


        val sb = StringBuilder()

        sb.appendLine("public static ${destClass.name} $methodName(${sourceClass.name} source) {")

        when (style) {
            "Builder" -> {
                sb.appendLine("    return ${destClass.name}.builder()")
                for (field in destFieldsNames) {
                    if (!commonFields.contains(field)) {
                        if (mappingStyle == "Flexible") {
                            sb.appendLine("       .${field}(source.get${toCamelCase(destToSourceSimilarityMap[field]!!)}())")
                            continue
                        }
                        sb.appendLine("       .${field}(/* TODO Add mapping for $field */)")
                        continue
                    }
                    sb.appendLine("        .${field}(source.get${toCamelCase(field)}())")
                }
                sb.appendLine("        .build();")
            }

            "Setter" -> {
                sb.appendLine("    ${destClass.name} dest = new ${destClass.name}();")
                for (field in destFieldsNames) {
                    if (!commonFields.contains(field)) {
                        if (mappingStyle == "Flexible") {
                            sb.appendLine(
                                "    dest.set${toCamelCase(field)}(source.get${
                                    toCamelCase(
                                        destToSourceSimilarityMap[field]!!
                                    )
                                }());"
                            )
                            continue
                        }
                        sb.appendLine("    dest.set${toCamelCase(field)}(/* TODO Add mapping for $field */);")
                        continue
                    }
                    sb.appendLine("    dest.set${toCamelCase(field)}(source.get${toCamelCase(field)}());")
                }
                sb.appendLine("    return dest;")
            }
        }

        sb.appendLine("}")

        return sb.toString()
    }

    private fun toCamelCase(field: @NlsSafe String) =
        field.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

}
