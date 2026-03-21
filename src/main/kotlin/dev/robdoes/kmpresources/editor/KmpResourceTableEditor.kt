package dev.robdoes.kmpresources.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import dev.robdoes.kmpresources.KmpResourcesBundle
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.beans.PropertyChangeListener
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.table.DefaultTableModel
import javax.xml.parsers.DocumentBuilderFactory

class KmpResourceTableEditor(
    private val project: Project,
    private val file: VirtualFile
) : UserDataHolderBase(), FileEditor {

    private val panel = JPanel(BorderLayout())
    private val logger = Logger.getInstance(KmpResourceTableEditor::class.java)

    init {
        val columnNames = arrayOf(
            KmpResourcesBundle.message("table.column.key"),
            KmpResourcesBundle.message("table.column.usage"),
            KmpResourcesBundle.message("table.column.untranslatable"),
            KmpResourcesBundle.message("table.column.type"),
            KmpResourcesBundle.message("table.column.default.value")
        )

        val tableModel = object : DefaultTableModel(columnNames, 0) {
            override fun getColumnClass(columnIndex: Int): Class<*> {
                return when (columnIndex) {
                    1 -> Icon::class.java
                    2 -> Boolean::class.javaObjectType
                    else -> String::class.java
                }
            }

            override fun isCellEditable(row: Int, column: Int): Boolean {
                if (column == 2) {
                    return getValueAt(row, column) != null
                }
                return false
            }
        }

        loadXmlData(file, tableModel)

        val table = JBTable(tableModel)

        table.columnModel.getColumn(0).preferredWidth = 350
        table.columnModel.getColumn(0).minWidth = 200
        table.columnModel.getColumn(1).maxWidth = 50
        table.columnModel.getColumn(2).maxWidth = 100

        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val row = table.rowAtPoint(e.point)
                val col = table.columnAtPoint(e.point)

                if (col == 1 && row >= 0) {
                    val keyName = tableModel.getValueAt(row, 0) as String
                    if (keyName.isNotBlank()) {
                        println("Usage Search clicked for Item: $keyName in file: ${file.path}")
                    }
                }
            }
        })

        panel.add(JBScrollPane(table), BorderLayout.CENTER)
    }

    private fun loadXmlData(file: VirtualFile, tableModel: DefaultTableModel) {
        try {
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file.inputStream)
            document.documentElement.normalize()

            val nodeList = document.documentElement.childNodes

            for (i in 0 until nodeList.length) {
                val node = nodeList.item(i)
                if (node.nodeType == Node.ELEMENT_NODE) {
                    val element = node as Element
                    val name = element.getAttribute("name")

                    val translatableAttr = element.getAttribute("translatable")
                    val isUntranslatable = translatableAttr == "false"

                    when (element.tagName) {
                        "string" -> {
                            val value = element.textContent
                            tableModel.addRow(arrayOf(name, AllIcons.Actions.Search, isUntranslatable, "string", value))
                        }

                        "plurals" -> {
                            tableModel.addRow(arrayOf(name, AllIcons.Actions.Search, isUntranslatable, "plurals", ""))

                            val items = element.getElementsByTagName("item")
                            for (j in 0 until items.length) {
                                val itemNode = items.item(j)
                                if (itemNode.nodeType == Node.ELEMENT_NODE) {
                                    val itemElement = itemNode as Element
                                    val quantity = itemElement.getAttribute("quantity")
                                    val itemValue = itemElement.textContent

                                    tableModel.addRow(arrayOf("", null, null, quantity, itemValue))
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Fehler beim Parsen der string.xml: ${file.path}", e)
        }
    }

    override fun getComponent(): JComponent = panel
    override fun getPreferredFocusedComponent(): JComponent = panel
    override fun getName(): String = KmpResourcesBundle.message("editor.tab.name")
    override fun setState(state: FileEditorState) {}
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = true
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun getCurrentLocation(): FileEditorLocation? = null
    override fun dispose() {}
    override fun getFile(): VirtualFile = file
}