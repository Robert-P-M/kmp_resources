package dev.robdoes.kmpresources.domain.repository

/**
 * Factory interface for creating instances of [LocaleRepository].
 *
 * This factory provides a method to instantiate and configure a
 * [LocaleRepository], enabling interaction with locale-related
 * data within the system, such as managing locale contexts,
 * verifying the existence of locale-specific files, and creating
 * locale directory structures.
 */
internal interface LocaleRepositoryFactory {
    /**
     * Creates an instance of [LocaleRepository] for managing locale-related data.
     *
     * The created repository facilitates operations such as retrieving all default
     * locale contexts, checking for the existence of locale-specific files, and
     * creating directory structures for new locales.
     *
     * @return A new instance of [LocaleRepository] to interact with locale data.
     */
    fun createLocaleRepository(): LocaleRepository
}