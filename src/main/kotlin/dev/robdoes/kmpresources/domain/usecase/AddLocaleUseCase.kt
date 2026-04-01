package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.domain.repository.LocaleRepository
import java.util.*

/**
 * Use case responsible for adding support for a new locale in the system. This involves creating the necessary
 * directory structure and files for the specified locale within all available default locale contexts.
 *
 * @property localeRepository A repository responsible for managing locale-related data and operations, such as
 * determining existing locale files, retrieving default locale contexts, and creating locale structures.
 */
internal class AddLocaleUseCase(
    private val localeRepository: LocaleRepository,
) {

    /**
     * Represents the context information required for managing default locale configurations.
     * This includes paths to the directory containing default values files and the path
     * to the default strings file for the locale.
     *
     * @property defaultValuesDirPath The filesystem path to the directory containing the default
     * values files for the locale.
     * @property defaultStringsFilePath The filesystem path to the default strings file for the locale.
     */
    data class LocaleContext(
        val defaultValuesDirPath: String,
        val defaultStringsFilePath: String
    )

    /**
     * Adds support for a specific locale to the system by creating the necessary directory
     * structure and files for the given locale in all default locale contexts.
     *
     * @param localeTag The BCP 47 language tag identifying the locale to be added (e.g., "en-US").
     */
    suspend operator fun invoke(localeTag: String) {
        val contexts = localeRepository.findAllDefaultLocaleContexts()

        for (context in contexts) {
            if (localeRepository.localeFileExists(context, localeTag)) continue

            localeRepository.createLocaleStructure(context, localeTag)
        }
    }
}
