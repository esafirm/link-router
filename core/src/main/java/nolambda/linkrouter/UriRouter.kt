package nolambda.linkrouter

import nolambda.linkrouter.DeepLinkUri.Companion.toDeepLinkUri
import nolambda.linkrouter.matcher.DeepLinkEntryMatcher
import nolambda.linkrouter.matcher.UriMatcher
import kotlin.system.measureTimeMillis

typealias UriRouterHandler<T> = (DeepLinkUri, Map<String, String>) -> T

class EntryValue<T>(
    val handler: UriRouterHandler<T>,
    val matcher: UriMatcher
)

abstract class UriRouter<RES>(
    private val logger: ((String) -> Unit)? = null
) : Router<String, RES?> {

    internal var entries = linkedMapOf<DeepLinkEntry, EntryValue<RES>>()

    override fun clear() {
        entries.clear()
    }

    override fun resolve(route: String): RES? {
        val filteredMap = entries.filter { it.value.matcher.match(it.key, route) }
        if (filteredMap.isEmpty()) {
            logger?.invoke("Path not implemented $route")
            return null
        }
        val deepLinkEntry = filteredMap.keys.first()
        val value = filteredMap[deepLinkEntry]

        val deepLinkUri = route.toDeepLinkUri()
        val parameters = deepLinkEntry.getParameters(deepLinkUri)

        return value!!.handler.invoke(deepLinkUri, parameters)
    }
}

fun <T> UriRouter<T>.addEntry(
    vararg uri: String,
    matcher: UriMatcher = DeepLinkEntryMatcher,
    handler: UriRouterHandler<T>
) {
    val deepLinkEntries = uri.map { DeepLinkEntry.parse(it) }
    deepLinkEntries.forEach {
        entries[it] = EntryValue(handler, matcher)
    }
}
