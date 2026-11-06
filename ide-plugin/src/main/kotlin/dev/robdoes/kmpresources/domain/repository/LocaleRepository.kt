package dev.robdoes.kmpresources.domain.repository

import dev.robdoes.kmpresources.domain.usecase.AddLocaleUseCase.LocaleContext

/**
 * Interface that defines a repository for managing and interacting with locale-related data
 * in a system. The repository is responsible for handling locale contexts, checking for the
 * existence of locale files, and creating locale structures.
 *
 * This repository is designed to operate asynchronously, enabling operations that may involve
 * IO or other time-consuming tasks to be executed in a non-blocking manner.
 */
internal interface LocaleRepository {

    /**
     * Retrieves a list of all default locale contexts available in the system.
     * These locale contexts define the paths to default values directories and default strings files.
     *
     * @return A list of [LocaleContext] objects representing the default locale contexts.
     */
    suspend fun findAllDefaultLocaleContexts(): List<LocaleContext>

    /**
     * Checks if a locale-specific file exists in the given context.
     *
     * @param context The locale context containing paths to the default values directory and default strings file.
     * @param localeTag The BCP 47 language tag identifying the locale (e.g., "en-US", "zh-Hant").
     * @return `true` if the locale-specific file exists in the specified context, otherwise `false`.
     */
    suspend fun localeFileExists(context: LocaleContext, localeTag: String): Boolean

    /**
     * Creates the directory structure and necessary files for a specific locale in the given context.
     *
     * @param context The locale context containing paths to the default values directory and default strings file.
     * @param localeTag The BCP 47 language tag identifying the locale (e.g., "en-US", "zh-Hant").
     */
    suspend fun createLocaleStructure(context: LocaleContext, localeTag: String)

}