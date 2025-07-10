package net.syncthing.java.core.utils

/**
 * Custom logger that provides a bridge between slf4j-style logging and platform-specific logging.
 * Uses android.util.Log when available (Android environment), falls back to println for JVM.
 */
class Logger private constructor(private val tag: String) {
    
    companion object {
        // Check if we're running on Android by looking for android.util.Log
        private val isAndroid: Boolean by lazy {
            try {
                Class.forName("android.util.Log")
                true
            } catch (e: ClassNotFoundException) {
                false
            }
        }
        
        /**
         * Creates a logger with the specified class as tag.
         * Mimics LoggerFactory.getLogger(Class) behavior.
         */
        fun getLogger(clazz: Class<*>): Logger {
            return Logger(clazz.simpleName)
        }
        
        fun getLogger(name: String): Logger {
            return Logger(name)
        }
    }
    
    /**
     * Logs a trace message.
     */
    fun trace(message: String, vararg args: Any?) {
        val formattedMessage = formatMessage(message, *args)
        if (isAndroid) {
            try {
                val logClass = Class.forName("android.util.Log")
                val verboseMethod = logClass.getMethod("v", String::class.java, String::class.java)
                verboseMethod.invoke(null, tag, formattedMessage)
            } catch (e: Exception) {
                fallbackLog("TRACE", formattedMessage)
            }
        } else {
            fallbackLog("TRACE", formattedMessage)
        }
    }
    
    /**
     * Logs a trace message with throwable.
     */
    fun trace(message: String, throwable: Throwable?, vararg args: Any?) {
        val formattedMessage = formatMessage(message, *args)
        if (isAndroid) {
            try {
                val logClass = Class.forName("android.util.Log")
                val verboseMethod = logClass.getMethod("v", String::class.java, String::class.java, Throwable::class.java)
                verboseMethod.invoke(null, tag, formattedMessage, throwable)
            } catch (e: Exception) {
                fallbackLog("TRACE", formattedMessage)
                throwable?.printStackTrace()
            }
        } else {
            fallbackLog("TRACE", formattedMessage)
            throwable?.printStackTrace()
        }
    }
    
    /**
     * Logs a debug message.
     */
    fun debug(message: String, vararg args: Any?) {
        val formattedMessage = formatMessage(message, *args)
        if (isAndroid) {
            try {
                val logClass = Class.forName("android.util.Log")
                val debugMethod = logClass.getMethod("d", String::class.java, String::class.java)
                debugMethod.invoke(null, tag, formattedMessage)
            } catch (e: Exception) {
                fallbackLog("DEBUG", formattedMessage)
            }
        } else {
            fallbackLog("DEBUG", formattedMessage)
        }
    }
    
    /**
     * Logs a debug message with throwable.
     */
    fun debug(message: String, throwable: Throwable?, vararg args: Any?) {
        val formattedMessage = formatMessage(message, *args)
        if (isAndroid) {
            try {
                val logClass = Class.forName("android.util.Log")
                val debugMethod = logClass.getMethod("d", String::class.java, String::class.java, Throwable::class.java)
                debugMethod.invoke(null, tag, formattedMessage, throwable)
            } catch (e: Exception) {
                fallbackLog("DEBUG", formattedMessage)
                throwable?.printStackTrace()
            }
        } else {
            fallbackLog("DEBUG", formattedMessage)
            throwable?.printStackTrace()
        }
    }
    
    /**
     * Logs an info message.
     */
    fun info(message: String, vararg args: Any?) {
        val formattedMessage = formatMessage(message, *args)
        if (isAndroid) {
            try {
                val logClass = Class.forName("android.util.Log")
                val infoMethod = logClass.getMethod("i", String::class.java, String::class.java)
                infoMethod.invoke(null, tag, formattedMessage)
            } catch (e: Exception) {
                fallbackLog("INFO", formattedMessage)
            }
        } else {
            fallbackLog("INFO", formattedMessage)
        }
    }
    
    /**
     * Logs an info message with throwable.
     */
    fun info(message: String, throwable: Throwable?, vararg args: Any?) {
        val formattedMessage = formatMessage(message, *args)
        if (isAndroid) {
            try {
                val logClass = Class.forName("android.util.Log")
                val infoMethod = logClass.getMethod("i", String::class.java, String::class.java, Throwable::class.java)
                infoMethod.invoke(null, tag, formattedMessage, throwable)
            } catch (e: Exception) {
                fallbackLog("INFO", formattedMessage)
                throwable?.printStackTrace()
            }
        } else {
            fallbackLog("INFO", formattedMessage)
            throwable?.printStackTrace()
        }
    }
    
    /**
     * Logs a warning message.
     */
    fun warn(message: String, vararg args: Any?) {
        val formattedMessage = formatMessage(message, *args)
        if (isAndroid) {
            try {
                val logClass = Class.forName("android.util.Log")
                val warnMethod = logClass.getMethod("w", String::class.java, String::class.java)
                warnMethod.invoke(null, tag, formattedMessage)
            } catch (e: Exception) {
                fallbackLog("WARN", formattedMessage)
            }
        } else {
            fallbackLog("WARN", formattedMessage)
        }
    }
    
    /**
     * Logs a warning message with throwable.
     */
    fun warn(message: String, throwable: Throwable?, vararg args: Any?) {
        val formattedMessage = formatMessage(message, *args)
        if (isAndroid) {
            try {
                val logClass = Class.forName("android.util.Log")
                val warnMethod = logClass.getMethod("w", String::class.java, String::class.java, Throwable::class.java)
                warnMethod.invoke(null, tag, formattedMessage, throwable)
            } catch (e: Exception) {
                fallbackLog("WARN", formattedMessage)
                throwable?.printStackTrace()
            }
        } else {
            fallbackLog("WARN", formattedMessage)
            throwable?.printStackTrace()
        }
    }
    
    /**
     * Logs an error message.
     */
    fun error(message: String, vararg args: Any?) {
        val formattedMessage = formatMessage(message, *args)
        if (isAndroid) {
            try {
                val logClass = Class.forName("android.util.Log")
                val errorMethod = logClass.getMethod("e", String::class.java, String::class.java)
                errorMethod.invoke(null, tag, formattedMessage)
            } catch (e: Exception) {
                fallbackLog("ERROR", formattedMessage)
            }
        } else {
            fallbackLog("ERROR", formattedMessage)
        }
    }
    
    /**
     * Logs an error message with throwable.
     */
    fun error(message: String, throwable: Throwable?, vararg args: Any?) {
        val formattedMessage = formatMessage(message, *args)
        if (isAndroid) {
            try {
                val logClass = Class.forName("android.util.Log")
                val errorMethod = logClass.getMethod("e", String::class.java, String::class.java, Throwable::class.java)
                errorMethod.invoke(null, tag, formattedMessage, throwable)
            } catch (e: Exception) {
                fallbackLog("ERROR", formattedMessage)
                throwable?.printStackTrace()
            }
        } else {
            fallbackLog("ERROR", formattedMessage)
            throwable?.printStackTrace()
        }
    }
    
    /**
     * Formats message with slf4j-style {} placeholders.
     */
    private fun formatMessage(message: String, vararg args: Any?): String {
        if (args.isEmpty()) return message
        
        var result = message
        for (arg in args) {
            val placeholder = result.indexOf("{}")
            if (placeholder != -1) {
                result = result.substring(0, placeholder) + arg.toString() + result.substring(placeholder + 2)
            } else {
                break
            }
        }
        return result
    }
    
    /**
     * Fallback logging using println when Android Log is not available.
     */
    private fun fallbackLog(level: String, message: String) {
        println("$level/$tag: $message")
    }
}