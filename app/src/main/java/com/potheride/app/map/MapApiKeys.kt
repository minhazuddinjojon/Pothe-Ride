package com.potheride.app.map

/**
 * Where the routing API keys come from.
 *
 * Keys live in `local.properties` (git-ignored) and are surfaced through `BuildConfig` by
 * the Gradle change recorded in `docs/upgrade/deps-map.md`. A missing key is *not* an
 * error: OpenRouteService and GraphHopper are both optional links in the chain, and the
 * app must run for a contributor who has cloned the repo and created no accounts at all.
 * That is exactly why the chain ends in a straight line.
 */
interface MapApiKeys {
    /** OpenRouteService key, or `null` when none is configured. */
    val openRouteService: String?

    /** GraphHopper key, or `null` when none is configured. */
    val graphHopper: String?
}

/**
 * Reads the keys from the generated `BuildConfig`.
 *
 * ### Why reflection
 * The `buildConfigField` declarations are added by whoever owns `build.gradle.kts`, and
 * this package must compile before that happens. A direct `BuildConfig.ORS_API_KEY`
 * reference would make the whole map package fail to compile until the build file catches
 * up, and would then fail again for anyone who reverts it. Reading the field reflectively
 * makes the dependency a runtime one that degrades to "no key configured" — which is
 * already a fully supported state.
 *
 * The cost is that a typo in the field name is not a compile error, so the names are
 * constants here and are quoted verbatim in `docs/upgrade/deps-map.md`.
 */
class BuildConfigMapApiKeys(
    private val buildConfigClassName: String = "com.potheride.app.BuildConfig"
) : MapApiKeys {

    override val openRouteService: String? by lazy { readField(FIELD_ORS) }
    override val graphHopper: String? by lazy { readField(FIELD_GRAPHHOPPER) }

    private fun readField(name: String): String? = try {
        val value = Class.forName(buildConfigClassName).getField(name).get(null) as? String
        // An unset key comes through as the empty string, because `buildConfigField` needs
        // *some* literal. Treat blank as absent so `NotConfigured` is reported rather than
        // a 403 from sending an empty Authorization header.
        value?.takeIf { it.isNotBlank() }
    } catch (e: ReflectiveOperationException) {
        null
    } catch (e: LinkageError) {
        null
    }

    companion object {
        const val FIELD_ORS = "ORS_API_KEY"
        const val FIELD_GRAPHHOPPER = "GRAPHHOPPER_API_KEY"
    }
}

/** Fixed keys, for tests and previews. */
data class StaticMapApiKeys(
    override val openRouteService: String? = null,
    override val graphHopper: String? = null
) : MapApiKeys
