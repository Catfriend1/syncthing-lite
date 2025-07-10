package net.syncthing.java.core.utils

/**
 * Factory for creating Logger instances.
 * Drop-in replacement for slf4j LoggerFactory.
 */
object LoggerFactory {
    /**
     * Creates a logger for the specified class.
     * @param clazz the class for which to create a logger
     * @return a Logger instance
     */
    fun getLogger(clazz: Class<*>): Logger {
        return Logger.getLogger(clazz)
    }

    fun getLogger(name: String): Logger {
        return Logger.getLogger(name)
    }
}