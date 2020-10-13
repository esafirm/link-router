@file:Suppress("UNCHECKED_CAST")

package nolambda.linkrouter.android

import nolambda.linkrouter.SimpleRouter
import nolambda.linkrouter.UriRouter
import nolambda.linkrouter.addEntry

typealias RouteHandler<P, R> = (RouteParam<P>) -> R
typealias RouteProcessor<T> = (T) -> Unit

class RouteParam<T>(val param: T? = null)

object Router {
    private object AndroidSimpleRouter : SimpleRouter<RouteHandler<*, *>>()
    private object AndroidUriRouter : UriRouter<Unit>()

    private val simpleRouter = AndroidSimpleRouter
    private val uriRouter = AndroidUriRouter

    private val EMPTY_PARAM = RouteParam<Unit>()

    private val middlewares = linkedSetOf<Middleware>()
    private val processors = linkedSetOf<Pair<Class<*>, RouteProcessor<in Any>>>()

    /* --------------------------------------------------- */
    /* > Component setup */
    /* --------------------------------------------------- */

    fun cleanRouter() {
        simpleRouter.clear()
        uriRouter.clear()
        processors.clear()
    }

    fun removeProcessor(processor: RouteProcessor<*>) {
        processors.removeAll { it.second == processor }
    }

    fun <T> addProcessor(clazz: Class<T>, processor: RouteProcessor<T>) {
        processors.add(clazz to processor as RouteProcessor<in Any>)
    }

    inline fun <reified T> addProcessor(noinline processor: RouteProcessor<T>) {
        addProcessor(T::class.java, processor)
    }

    fun addMiddleware(middleware: Middleware) {
        middlewares.add(middleware)
    }

    fun removeMiddleware(middleware: Middleware) {
        middlewares.remove(middleware)
    }

    /* --------------------------------------------------- */
    /* > Processing */
    /* --------------------------------------------------- */

    fun <P, R> register(route: BaseRoute<P>, handler: RouteHandler<P, R>) {
        val paths = route.routePaths

        // Handle non path
        simpleRouter.addEntry(route) {
            @Suppress("UNCHECKED_CAST")
            handler as RouteHandler<*, *>
        }

        // Handle the path
        if (paths.isEmpty()) return

        paths.forEach { path ->
            if (path.isBlank()) return@forEach

            uriRouter.addEntry(path) {
                val result = if (route is RouteWithParam<P>) {
                    handler.invoke(RouteParam(route.mapParameter(it)))
                } else {
                    handler.invoke(RouteParam())
                }
                invokeProcessor(result)
            }
        }
    }

    fun goTo(uri: String) {
        uriRouter.resolve(uri)
    }

    fun <P> push(route: BaseRoute<P>, param: P? = null) {
        applyMiddleware(route, param)
        val result = when (param == null) {
            true -> EMPTY_PARAM
            else -> RouteParam(param)
        }
        invokeProcessor(simpleResolve(route, result))
    }

    private fun applyMiddleware(route: BaseRoute<*>, param: Any?) {
        middlewares.forEach {
            it.onRouting(route, param)
        }
    }

    private fun simpleResolve(route: BaseRoute<*>, param: RouteParam<*>) =
        simpleRouter.resolve(route).invoke(param)

    private fun invokeProcessor(result: Any?) {
        if (result == null) return
        if (processors.isEmpty()) return

        val clazz = result.javaClass
        processors.forEach { (c, processor) ->
            if (c.isAssignableFrom(clazz)) {
                processor.invoke(result)
            }
        }
    }
}