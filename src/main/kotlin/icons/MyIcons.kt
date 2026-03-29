package icons

import com.intellij.openapi.util.IconLoader

object MyIcons {
    @JvmField
    val ToolbarAppAction = IconLoader.getIcon("/icons/pluginIcon.svg", javaClass)

    @JvmField
    val ToolWindowIcon = IconLoader.getIcon("/icons/pluginIcon.svg", javaClass)
}