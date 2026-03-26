package dev.robdoes.kmpresources.domain.model

inline fun <reified T : XmlResource> List<XmlResource>.findResource(key: String): T? {
    return this.find { it.key == key } as? T
}