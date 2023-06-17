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

    private val targetIsThisCheck = JCheckBox("This Class").apply {
        addActionListener() {
            if (isSelected) {
                selectedSourceClass = psiFile.childrenOfType<PsiClass>()[0]
                sourceClassName.text = selectedSourceClass!!.qualifiedName
                sourceClassBrowseButton.isEnabled = false
            } else {
                selectedSourceClass = null
                sourceClassName.text = "Not selected"
                sourceClassBrowseButton.isEnabled = true
            }
        }
        isSelected = true
        selectedSourceClass = psiFile.childrenOfType<PsiClass>()[0]
        sourceClassName.text = selectedSourceClass!!.qualifiedName
        sourceClassBrowseButton.isEnabled = false
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
        c.insets = JBUI.insets(5)

        c.gridx = 0
        c.gridy = 0
        panel.add(JLabel("Source Class:"), c)


        c.gridx = 1
        c.gridy = 0
        panel.add(JPanel(GridLayout(0, 3)).apply {
            add(sourceClassName)
            add(sourceClassBrowseButton)
            add(targetIsThisCheck)
        }, c)


        c.gridx = 0
        c.gridy = 1
        panel.add(JLabel("Destination Class:"), c)

        c.gridx = 1
        c.gridy = 1
        panel.add(JPanel(GridLayout(0, 3)).apply {
            add(destClassName)
            add(destClassBrowseButton)
        }, c)

        c.gridx = 0
        c.gridy = 2
        panel.add(JLabel("Method Name:"), c)

        c.gridx = 1
        c.gridy = 2
        panel.add(methodNameField, c)

        c.gridx = 0
        c.gridy = 3
        panel.add(JLabel("Method Style:"), c)

        val styleRadioPannel = JPanel(GridLayout(1, 2)).apply {
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

        val mappingRadioPannel = JPanel(GridLayout(1, 2)).apply {
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
                if (strictButton.isSelected) "Strict" else "Flexible",
                targetIsThisCheck.isSelected
            ), null
        )
        WriteCommandAction.runWriteCommandAction(project) {
            psiClass.add(method)
        }


        super.doOKAction()
    }

    fun generateMappingMethod(
        sourceClass: PsiClass,
        destClass: PsiClass,
        methodName: String,
        style: String,
        mappingStyle: String,
        targetIsThisCheck: Boolean
    ): String {
        val destFieldsNames = destClass.allFields.map { it.name }
        val sourceFieldsNames = sourceClass.allFields.map { it.name }

        val commonFields = sourceFieldsNames.intersect(destFieldsNames.toSet())
        val similarFieldsMap = createSimilarityMap(destFieldsNames, sourceFieldsNames, mappingStyle)

        return buildMethodString(
            destClass,
            sourceClass,
            methodName,
            style,
            mappingStyle,
            targetIsThisCheck,
            commonFields,
            similarFieldsMap
        )
    }

    private fun createSimilarityMap(
        destFieldsNames: List<String>,
        sourceFieldsNames: List<String>,
        mappingStyle: String
    ): Map<String, String> {
        val destToSourceSimilarityMap = mutableMapOf<String, String>()

        if (mappingStyle == "Flexible") {
            val levenshtein = LevenshteinDistance.getDefaultInstance()

            for (destFieldName in destFieldsNames) {
                val mostSimilarSourceFieldName = sourceFieldsNames.minByOrNull { levenshtein.apply(destFieldName, it) }
                if (mostSimilarSourceFieldName != null) {
                    destToSourceSimilarityMap[destFieldName] = mostSimilarSourceFieldName
                }
            }
        }

        return destToSourceSimilarityMap
    }

    private fun buildMethodString(
        destClass: PsiClass,
        sourceClass: PsiClass,
        methodName: String,
        style: String,
        mappingStyle: String,
        targetIsThisCheck: Boolean,
        commonFields: Set<String>,
        similarFieldsMap: Map<String, String>
    ): String {
        val sb = StringBuilder()
        val methodHeader =
            if (targetIsThisCheck) "public ${destClass.name} $methodName(${sourceClass.name} source) "
            else "public static ${destClass.name} $methodName(${sourceClass.name} source) "
        sb.appendLine(methodHeader)
        sb.append(" {")

        when (style) {
            "Builder" -> buildWithBuilderStyle(
                destClass,
                sourceClass,
                sb,
                mappingStyle,
                targetIsThisCheck,
                commonFields,
                similarFieldsMap
            )

            "Setter" -> buildWithSetterStyle(
                destClass,
                sourceClass,
                sb,
                mappingStyle,
                targetIsThisCheck,
                commonFields,
                similarFieldsMap
            )
        }

        sb.appendLine("}")
        return sb.toString()
    }

    private fun buildWithBuilderStyle(
        destClass: PsiClass, sourceClass: PsiClass, sb: StringBuilder, mappingStyle: String, targetIsThisCheck: Boolean,
        commonFields: Set<String>, similarFieldsMap: Map<String, String>
    ) {
        sb.appendLine("    return ${destClass.name}.builder()")
        for (field in destClass.allFields.map { it.name }) {
            when {
                commonFields.contains(field) -> sb.appendLine(
                    "        .${field}(${
                        getMappingExpression(
                            sourceClass,
                            field,
                            targetIsThisCheck
                        )
                    })"
                )

                mappingStyle == "Flexible" -> sb.appendLine(
                    "        .${field}(${
                        getMappingExpression(
                            sourceClass,
                            similarFieldsMap[field]!!,
                            targetIsThisCheck
                        )
                    })"
                )

                else -> sb.appendLine("        .${field}(/* TODO Add mapping for $field */)")
            }
        }
        sb.appendLine("        .build();")
    }

    private fun buildWithSetterStyle(
        destClass: PsiClass, sourceClass: PsiClass, sb: StringBuilder, mappingStyle: String, targetIsThisCheck: Boolean,
        commonFields: Set<String>, similarFieldsMap: Map<String, String>
    ) {
        sb.appendLine("    ${destClass.name} dest = new ${destClass.name}();")
        for (field in destClass.allFields.map { it.name }) {
            when {
                commonFields.contains(field) -> sb.appendLine(
                    "    dest.set${toCapitalize(field)}(${
                        getMappingExpression(
                            sourceClass,
                            field,
                            targetIsThisCheck
                        )
                    });"
                )

                mappingStyle == "Flexible" -> sb.appendLine(
                    "    dest.set${toCapitalize(field)}(${
                        getMappingExpression(
                            sourceClass,
                            similarFieldsMap[field]!!,
                            targetIsThisCheck
                        )
                    });"
                )

                else -> sb.appendLine("    dest.set${toCapitalize(field)}(/* TODO Add mapping for $field */);")
            }
        }
        sb.appendLine("    return dest;")
    }

    private fun getMappingExpression(sourceClass: PsiClass, field: String, targetIsThisCheck: Boolean): String {
        return if (targetIsThisCheck) "this.${field}" else "source.get${toCapitalize(field)}()"
    }

    private fun toCapitalize(field: @NlsSafe String) =
        field.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

}
