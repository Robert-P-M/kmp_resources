package dev.robdoes.kmpresources.presentation.editor.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.DocumentAdapter
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
import javax.swing.event.DocumentEvent

/**
 * A user interface panel for creating and editing XML resources within a project.
 * This panel supports operations for managing "string", "plurals", and "string-array" resource types.
 *
 * @constructor
 * Initializes the ResourceEditPanel with the required project context.
 *
 * @param project An instance of the current project.
 *
 * Enums:
 * - `EditMode`: Defines the mode of editing, whether adding a new resource or updating an existing one.
 *
 * Callbacks:
 * - `onSaveRequested`: Invoked when the save operation is triggered, passing the created or updated `XmlResource` as an argument.
 * - `onCancelRequested`: Invoked when the cancel operation is triggered.
 *
 * Key Responsibilities:
 * - Handles input validation for resource keys.
 * - Manages the visibility of UI elements based on the selected resource type ("string", "plurals", or "string-array").
 * - Supports dynamic addition and removal of array elements in the "string-array" resource type.
 * - Provides localized labels and placeholders for fields and buttons.
 *
 * Layout and Components:
 * - Uses a combination of labels, text fields, and buttons for user input and interaction.
 * - Dynamically updates form components and visibility according to the selected resource type and edit mode.
 * - Enforces input validation via custom logic, updating the UI to provide feedback to the user.
 *
 * Behavior:
 * - The `keyField` validates the resource key for correctness and uniqueness.
 * - When editing plural or array resources, the appropriate input fields are either added or made visible.
 * - The main action button and the save key button trigger the save functionality, validating input before proceeding.
 * - Incorporates keyboard listeners for improved usability and faster interaction workflows.
 */
internal class ResourceEditPanel(project: Project) : JPanel(BorderLayout()) {

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

        keyField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                validateInput()
            }
        })

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
                        if (e.keyCode == KeyEvent.VK_ENTER && mainActionButton.isEnabled) submit()
                        else stringValueField.requestFocusInWindow()
                    } else if (e.source == stringValueField) {
                        if (e.keyCode == KeyEvent.VK_ENTER && mainActionButton.isEnabled) submit()
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

    /**
     * Validates the input in the key field and updates the UI elements accordingly.
     *
     * This method checks whether the text in the `keyField` adheres to the expected format and is not empty.
     * If the validation fails, visual feedback (e.g., an error outline and tooltip message) is applied to the `keyField`.
     * Also, the states of the `saveKeyButton` and `mainActionButton` are updated based on the validation result.
     *
     * Validation logic:
     * - The text must match the format defined by `ResourceKeyValidator.isValid`.
     * - The text must not be blank.
     *
     * Side effects:
     * - Updates the visual state of `keyField` (error outline and tooltip).
     * - Enables/disables `saveKeyButton` and `mainActionButton`.
     */
    private fun validateInput() {
        val text = keyField.text
        val isValidFormat = ResourceKeyValidator.isValid(text)
        val isNotEmpty = text.isNotBlank()

        val isKeyValid = isValidFormat && isNotEmpty

        if (!isValidFormat && text.isNotEmpty()) {
            keyField.putClientProperty("JComponent.outline", "error")
            keyField.toolTipText = KmpResourcesBundle.message("dialog.error.validation.key")
        } else {
            keyField.putClientProperty("JComponent.outline", null)
            keyField.toolTipText = null
        }

        saveKeyButton.isEnabled = isKeyValid
        mainActionButton.isEnabled = isKeyValid
    }

    /**
     * Updates the visibility of form components based on the selected resource type.
     *
     * @param selectedType The type of resource selected by the user.
     *                     Valid values are:
     *                     - "string": Displays form fields for string resources.
     *                     - "plurals": Displays form fields for plural resources.
     *                     - "string-array": Displays form fields for string array resources.
     */
    private fun updateFormVisibility(selectedType: String) {
        stringRow.visible(selectedType == "string")
        pluralsRows.forEach { it.visible(selectedType == "plurals") }
        arrayRows.forEach { it.visible(selectedType == "string-array") }
    }

    /**
     * Builds and adds a new row representing an item in a string array.
     *
     * This method creates a panel for representing a single string array item, including:
     * - An indexed label to indicate the position of the item.
     * - A text field pre-filled with the provided value and a placeholder for empty input.
     * - A remove button for deleting the row, which also updates related UI elements and internal data structures.
     *
     * The created row is added to `arrayItemsContainer`, and the associated data field is appended to `arrayValueFields`.
     * Required UI updates such as revalidation and repainting are also applied.
     *
     * @param value The initial value for the item's text field.
     */
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

    /**
     * Updates the text of index labels for all child components within the `arrayItemsContainer`.
     *
     * This method iterates through the components of `arrayItemsContainer`, identifies each child panel representing
     * an array item, and updates the associated label's text to reflect the current index of the item. The index is
     * displayed in a format such as "[0]", "[1]", etc.
     *
     * Key behaviors:
     * - Only components of type `JPanel` are processed.
     * - Within each panel, the first `JLabel` encountered (if present) is updated with the index.
     *
     * Side effects:
     * - Modifies the text of index labels in the UI for array items.
     *
     * Usage context:
     * - Typically invoked when the ordering of array items changes (e.g., after adding or removing an item),
     *   to ensure the labels reflect the correct indices.
     */
    private fun updateArrayIndexLabels() {
        arrayItemsContainer.components.forEachIndexed { index, comp ->
            if (comp is JPanel) {
                val label = comp.components.find { it is JLabel } as? JLabel
                label?.text = "[$index]"
            }
        }
    }

    /**
     * Prepares and displays the resource editing panel for creating a new resource.
     *
     * This method configures the interface for adding a new resource by performing
     * the following actions:
     *
     * 1. Sets the editing mode to `ADD` to signify a create operation.
     * 2. Resets the fields for resource key, type, and values to their default states.
     * 3. Updates the form's header title and description based on the selected resource type.
     * 4. Adjusts the visibility of specific form elements according to the resource type.
     * 5. Clears any existing array or plural value entries and, if applicable, initializes
     *    the first item for string arrays.
     * 6. Enables relevant form controls such as the resource type dropdown and key input field.
     * 7. Sets the action button's text and icon to indicate the "Add" operation.
     * 8. Triggers validation to update the form's state before display.
     * 9. Ensures focus is set to the key input field for immediate interaction.
     *
     * Usage context:
     * Typically called when transitioning the panel to a "new resource" creation state.
     */
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

        validateInput()

        isVisible = true
        keyField.requestFocusInWindow()
    }

    /**
     * Configures and displays the resource editing panel for updating an existing resource.
     *
     * This method prepares the UI for modifying the details of a given resource. The process includes:
     * - Setting the editing mode to `UPDATE` to signify an update operation.
     * - Pre-filling the form fields with the provided resource data.
     * - Adjusting the visibility and interactivity of UI elements based on the resource type.
     * - Ensuring the main action button reflects the update operation.
     * - Triggering input validation to update the form's state before display.
     *
     * @param resource The resource to be updated. This parameter provides the initial data
     *                 for populating the form fields, including the resource's type, key,
     *                 and localized values.
     */
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

        mainActionButton.text = KmpResourcesBundle.message("ui.panel.btn.update")
        mainActionButton.icon = AllIcons.Actions.Edit

        validateInput()

        isVisible = true
    }

    /**
     * Processes the submission of the resource editing form and triggers the save action if the input is valid.
     *
     * This method performs the following operations:
     * 1. Checks if the main action button is disabled and exits early if it is.
     * 2. Constructs a resource object from the user's input by invoking the `buildResourceFromInput` method on the
     *    `controller` instance. The resource is built based on the user-provided key, type, values, and other configurations.
     * 3. Invokes the save action callback `onSaveRequested` if the resource is successfully constructed.
     *
     * Core behaviors:
     * - Input fields are trimmed and validated to ensure proper formatting.
     * - Handles multiple types of resources, including string, plural, and string array resources.
     * - Filters out blank or invalid values from plural and array inputs.
     *
     * Preconditions:
     * - The `mainActionButton` should be enabled to allow processing.
     *
     * Side effects:
     * - Executes the save callback with the newly constructed resource.
     */
    private fun submit() {
        if (!mainActionButton.isEnabled) return

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