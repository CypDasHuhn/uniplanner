package dev.cypdashuhn.uniplanner.core

import dev.cypdashuhn.uniplanner.handler.Handler
import java.util.ServiceLoader

object HandlerLoader {
    fun discover(classLoader: ClassLoader = Handler::class.java.classLoader): List<Handler> =
        ServiceLoader.load(Handler::class.java, classLoader).toList()
}
