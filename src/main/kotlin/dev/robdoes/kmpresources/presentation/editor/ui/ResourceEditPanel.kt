package dev.robdoes.kmpresources.presentation.editor.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import dev.robdoes.kmpresources.core.infrastructure.i18n.KmpResourcesBundle
import dev.robdoes.kmpresources.domain.model.*
import dev.robdoes.kmpresources.domain.usecase.ResourceKeyValidator
import dev.robdoes.kmpresources.presentation.editor.controller.ResourceEditPanelController
import java.awt.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.text.AbstractDocument
import javax.swing.text.AttributeSet
import javax.swing.text.DocumentFilter

class ResourceEditPanel(project: Project) : JPanel(BorderLayout()) {

    enum class EditMode { ADD, UPDATE }

    var onSaveRequested: ((XmlResource) -> Unit)? = null
    var onCancelRequested: (() -> Unit)? = null

    private var currentEditMode = EditMode.ADD
    private var isUntranslatableState = false

    private val titleLabel = JBLabel().apply { font = font.deriveFont(Font.BOLD, 14f) }
    private val descriptionLabel =
        JBLabel().apply { foreground = UIUtil.getContextHelpForeground(); font = font.deriveFont(11f) }

    private val typeComboBox = ComboBox(arrayOf("string", "plurals", "string-array"))
    private val keyField =
        JBTextField(25).apply { emptyText.text = KmpResourcesBundle.message("ui.panel.add.key.placeholder") }

    private val saveKeyButton = JButton(AllIcons.Actions.Checked).apply {
        toolTipText = KmpResourcesBundle.message("action.table.save.key.text")
        isBorderPainted = false
        isContentAreaFilled = false
        isOpaque = false
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    private val stringValueField =
        JBTextField(35).apply { emptyText.text = KmpResourcesBundle.message("ui.panel.add.value.placeholder") }
    private val pluralValueFields = mutableMapOf<String, JBTextField>()

    private val arrayItemsContainer = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    private val arrayValueFields = mutableListOf<JBTextField>()

    private val mainActionButton = JButton(KmpResourcesBundle.message("ui.panel.btn.add"), AllIcons.General.Add)
    private val cancelButton = JButton(KmpResourcesBundle.message("ui.panel.btn.cancel"))

    private val validQuantities = ResourceType.Plural.supportedQuantities

    private lateinit var stringRow: Row
    private val pluralsRows = mutableListOf<Row>()
    private val arrayRows = mutableListOf<Row>()

    private val controller = ResourceEditPanelController(project)

    init {
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor.border()),
            JBUI.Borders.empty(5, 10, 10, 10)
        )

        (keyField.document as AbstractDocument).documentFilter = object : DocumentFilter() {
            override fun insertString(fb: FilterBypass, offset: Int, string: String?, attr: AttributeSet?) {
                if (string != null && ResourceKeyValidator.isValid(string)) {
                    super.insertString(fb, offset, string, attr)
                } else {
                    Toolkit.getDefaultToolkit().beep()
                }
            }

            override fun replace(fb: FilterBypass, offset: Int, length: Int, text: String?, attrs: AttributeSet?) {
                if (text != null && ResourceKeyValidator.isValid(text)) {
                    super.replace(fb, offset, length, text, attrs)
                } else {
                    Toolkit.getDefaultToolkit().beep()
                }
            }
        }

        validQuantities.forEach { q ->
            pluralValueFields[q] = JBTextField(35).apply {
                emptyText.text = KmpResourcesBundle.message("ui.panel.add.plural.placeholder", q)
            }
        }

        val formPanel = panel {
            row { cell(titleLabel) }
            row { cell(descriptionLabel) }

            separator()

            row("Resource:") {
                cell(typeComboBox)
                cell(keyField).align(AlignX.FILL)
                cell(saveKeyButton)
            }

            stringRow = row("Value:") {
                cell(stringValueField).align(AlignX.FILL)
            }

            validQuantities.forEach { q ->
                val r = row(q) {
                    cell(pluralValueFields[q]!!).align(AlignX.FILL)
                }
                pluralsRows.add(r)
            }

            val addArrayItemBtn =
                JButton(KmpResourcesBundle.message("ui.panel.add.array.btn.add"), AllIcons.General.Add).apply {
                    addActionListener { buildArrayItemRow("") }
                }

            arrayRows.add(row { cell(addArrayItemBtn) })
            arrayRows.add(row {
                val arrayScroll = JBScrollPane(arrayItemsContainer).apply {
                    preferredSize = Dimension(400, 120)
                    border = JBUI.Borders.empty()
                }
                cell(arrayScroll).align(AlignX.FILL)
            })

            separator()

            row {
                cell(mainActionButton)
                cell(cancelButton)
            }
        }

        typeComboBox.addActionListener {
            val selected = typeComboBox.selectedItem as String
            updateFormVisibility(selected)
            if (currentEditMode == EditMode.ADD) {
                titleLabel.text = controller.getHeaderTitle(false, selected)
                descriptionLabel.text = controller.getHeaderDescription(false, selected)

                if (selected == "string-array" && arrayValueFields.isEmpty()) buildArrayItemRow("")
            }
        }

        mainActionButton.addActionListener { submit() }
        saveKeyButton.addActionListener { submit() }
        cancelButton.addActionListener {
            isVisible = false
            onCancelRequested?.invoke()
        }

        val enterAdapter = object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER || e.keyCode == KeyEvent.VK_TAB) {
                    e.consume()
                    if (e.source == keyField && typeComboBox.selectedItem == "string") {
                        if (e.keyCode == KeyEvent.VK_ENTER) submit()
                        else stringValueField.requestFocusInWindow()
                    } else if (e.source == stringValueField) {
                        if (e.keyCode == KeyEvent.VK_ENTER) submit()
                    }
                }
            }
        }
        keyField.addKeyListener(enterAdapter)
        stringValueField.addKeyListener(enterAdapter)

        add(JBScrollPane(formPanel).apply {
            border = JBUI.Borders.empty()
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        }, BorderLayout.CENTER)

        updateFormVisibility("string")
    }

    private fun updateFormVisibility(selectedType: String) {
        stringRow.visible(selectedType == "string")
        pluralsRows.forEach { it.visible(selectedType == "plurals") }
        arrayRows.forEach { it.visible(selectedType == "string-array") }
    }

    private fun buildArrayItemRow(value: String) {
        val rowPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 2))

        val indexLabel =
            JLabel("[${arrayValueFields.size}]").apply { preferredSize = Dimension(35, preferredSize.height) }
        val field = JBTextField(30).apply {
            text = value; emptyText.text = KmpResourcesBundle.message("ui.panel.add.array.placeholder")
        }

        val removeBtn = JButton(AllIcons.General.Remove).apply {
            isBorderPainted = false; isContentAreaFilled = false; isOpaque = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener {
                arrayItemsContainer.remove(rowPanel)
                arrayValueFields.remove(field)
                updateArrayIndexLabels()
                arrayItemsContainer.revalidate()
                arrayItemsContainer.repaint()
            }
        }

        arrayValueFields.add(field)
        rowPanel.add(indexLabel)
        rowPanel.add(field)
        rowPanel.add(removeBtn)

        arrayItemsContainer.add(rowPanel)
        arrayItemsContainer.revalidate()
        arrayItemsContainer.repaint()
    }

    private fun updateArrayIndexLabels() {
        arrayItemsContainer.components.forEachIndexed { index, comp ->
            if (comp is JPanel) {
                val label = comp.components.find { it is JLabel } as? JLabel
                label?.text = "[$index]"
            }
        }
    }

    fun showForAdd() {
        currentEditMode = EditMode.ADD
        isUntranslatableState = false
        val selectedType = typeComboBox.selectedItem as String

        titleLabel.text = controller.getHeaderTitle(false, selectedType)
        descriptionLabel.text = controller.getHeaderDescription(false, selectedType)

        updateFormVisibility(selectedType)

        typeComboBox.isEnabled = true
        keyField.isEnabled = true
        keyField.text = ""
        stringValueField.text = ""
        pluralValueFields.values.forEach { it.text = "" }

        arrayItemsContainer.removeAll()
        arrayValueFields.clear()
        if (selectedType == "string-array") buildArrayItemRow("")

        mainActionButton.text = KmpResourcesBundle.message("ui.panel.btn.add")
        mainActionButton.icon = AllIcons.General.Add
        mainActionButton.isEnabled = true

        isVisible = true
        keyField.requestFocusInWindow()
    }

    fun showForUpdate(resource: XmlResource) {
        currentEditMode = EditMode.UPDATE
        isUntranslatableState = resource.isUntranslatable
        titleLabel.text = controller.getHeaderTitle(true, resource.xmlTag)
        descriptionLabel.text = controller.getHeaderDescription(true, resource.xmlTag)

        typeComboBox.selectedItem = resource.xmlTag
        typeComboBox.isEnabled = false
        updateFormVisibility(resource.xmlTag)

        keyField.text = resource.key
        keyField.isEnabled = true

        when (resource) {
            is StringResource -> {
                stringValueField.text = resource.values[null] ?: ""
                stringValueField.requestFocusInWindow()
            }

            is PluralsResource -> {
                val defaultItems = resource.localizedItems[null] ?: emptyMap()
                validQuantities.forEach { q ->
                    pluralValueFields[q]?.text = defaultItems[q] ?: ""
                }
            }

            is StringArrayResource -> {
                arrayItemsContainer.removeAll()
                arrayValueFields.clear()
                val defaultItems = resource.localizedItems[null] ?: emptyList()
                defaultItems.forEach { buildArrayItemRow(it) }
                if (defaultItems.isEmpty()) buildArrayItemRow("")
            }
        }

        mainActionButton.isEnabled = true
        mainActionButton.text = KmpResourcesBundle.message("ui.panel.btn.update")
        mainActionButton.icon = AllIcons.Actions.Edit
        isVisible = true
    }

    private fun submit() {
        val resourceToSave = controller.buildResourceFromInput(
            key = keyField.text.trim(),
            type = typeComboBox.selectedItem as String,
            isUntranslatable = isUntranslatableState,
            stringValue = stringValueField.text,
            pluralValues = pluralValueFields.mapValues { it.value.text },
            arrayValues = arrayValueFields.map { it.text }
        )

        if (resourceToSave != null) {
            onSaveRequested?.invoke(resourceToSave)
        }
    }

}
