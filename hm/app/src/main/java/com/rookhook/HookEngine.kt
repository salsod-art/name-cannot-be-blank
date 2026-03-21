package com.roothook

import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Method

object HookEngine {

    private const val TAG = "RootHook"

    // Maps type name strings to actual Class objects for param resolution
    private val TYPE_MAP = mapOf(
        "boolean"           to Boolean::class.javaPrimitiveType!!,
        "int"               to Int::class.javaPrimitiveType!!,
        "long"              to Long::class.javaPrimitiveType!!,
        "float"             to Float::class.javaPrimitiveType!!,
        "double"            to Double::class.javaPrimitiveType!!,
        "byte"              to Byte::class.javaPrimitiveType!!,
        "short"             to Short::class.javaPrimitiveType!!,
        "char"              to Char::class.javaPrimitiveType!!,
        "void"              to Void::class.javaPrimitiveType!!,
        "String"            to String::class.java,
        "java.lang.String"  to String::class.java,
        "Object"            to Any::class.java,
        "java.lang.Object"  to Any::class.java,
        "Context"           to android.content.Context::class.java,
        "android.content.Context" to android.content.Context::class.java
    )

    fun applyRules(rules: List<HookRule>, classLoader: ClassLoader) {
        for (rule in rules) {
            try {
                applyRule(rule, classLoader)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply rule ${rule.id} (${rule.className}.${rule.methodName}): ${e.message}")
                RuleLoader.writeLog(rule.targetPackage, rule.id, rule.methodName, "HOOK_FAILED", e.message ?: "unknown error")
            }
        }
    }

    private fun applyRule(rule: HookRule, classLoader: ClassLoader) {
        Log.i(TAG, "Applying rule ${rule.id}: ${rule.className}.${rule.methodName} [${rule.action}]")

        val clazz = XposedHelpers.findClass(rule.className, classLoader)

        // Resolve parameter types if provided
        val paramClasses: Array<Class<*>> = if (rule.paramTypes.isEmpty()) {
            // Hook all overloads if no params specified — hook each one
            hookAllOverloads(rule, clazz)
            return
        } else {
            rule.paramTypes.map { resolveType(it, classLoader) }.toTypedArray()
        }

        val hook = buildHook(rule)
        XposedHelpers.findAndHookMethod(clazz, rule.methodName, *paramClasses, hook)
        Log.i(TAG, "Hooked: ${rule.className}.${rule.methodName}")
    }

    private fun hookAllOverloads(rule: HookRule, clazz: Class<*>) {
        val methods = clazz.declaredMethods.filter { it.name == rule.methodName }
        if (methods.isEmpty()) {
            Log.w(TAG, "No methods named '${rule.methodName}' found in ${rule.className}")
            RuleLoader.writeLog(rule.targetPackage, rule.id, rule.methodName, "HOOK_FAILED", "method not found")
            return
        }
        val hook = buildHook(rule)
        for (method in methods) {
            de.robv.android.xposed.XposedBridge.hookMethod(method, hook)
            Log.i(TAG, "Hooked overload: ${rule.methodName}(${method.parameterTypes.joinToString { it.simpleName }})")
        }
    }

    private fun buildHook(rule: HookRule): XC_MethodHook {
        return object : XC_MethodHook() {

            override fun beforeHookedMethod(param: MethodHookParam) {
                if (rule.action != "before" && rule.action != "replace") return

                val argsStr = formatArgs(param.args)
                Log.d(TAG, "[BEFORE] ${rule.methodName} args=$argsStr")

                if (rule.action == "replace") {
                    // Set the return value and skip the original method
                    param.result = coerceReturnValue(rule.returnValue, param.method)
                    RuleLoader.writeLog(
                        pkg        = rule.targetPackage,
                        ruleId     = rule.id,
                        methodName = rule.methodName,
                        args       = argsStr,
                        result     = "REPLACED → ${rule.returnValue}"
                    )
                } else {
                    RuleLoader.writeLog(
                        pkg        = rule.targetPackage,
                        ruleId     = rule.id,
                        methodName = rule.methodName,
                        args       = argsStr,
                        result     = "BEFORE_HOOK"
                    )
                }
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                if (rule.action != "log" && rule.action != "after") return

                val argsStr  = formatArgs(param.args)
                val resultStr = param.result?.toString() ?: "null"

                Log.d(TAG, "[AFTER] ${rule.methodName} args=$argsStr result=$resultStr")

                RuleLoader.writeLog(
                    pkg        = rule.targetPackage,
                    ruleId     = rule.id,
                    methodName = rule.methodName,
                    args       = argsStr,
                    result     = resultStr
                )
            }
        }
    }

    private fun formatArgs(args: Array<Any?>?): String {
        if (args.isNullOrEmpty()) return "[]"
        return "[" + args.joinToString(", ") { arg ->
            when (arg) {
                null       -> "null"
                is String  -> "\"${arg.take(200)}\""
                is ByteArray -> "ByteArray[${arg.size}]"
                else       -> arg.toString().take(200)
            }
        } + "]"
    }

    /**
     * Try to coerce the string returnValue from the rule into the correct type
     * based on the method's actual return type.
     */
    private fun coerceReturnValue(value: String?, method: Any): Any? {
        if (value == null || value == "null") return null
        return try {
            val returnType = when (method) {
                is Method -> method.returnType
                else      -> null
            }
            when (returnType) {
                Boolean::class.javaPrimitiveType,
                java.lang.Boolean::class.java    -> value.trim().lowercase() == "true" || value.trim() == "1"
                Int::class.javaPrimitiveType,
                java.lang.Integer::class.java    -> value.trim().toInt()
                Long::class.javaPrimitiveType,
                java.lang.Long::class.java       -> value.trim().toLong()
                Float::class.javaPrimitiveType,
                java.lang.Float::class.java      -> value.trim().toFloat()
                Double::class.javaPrimitiveType,
                java.lang.Double::class.java     -> value.trim().toDouble()
                String::class.java               -> value
                else                             -> value
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not coerce return value '$value': ${e.message}")
            value
        }
    }

    private fun resolveType(typeName: String, classLoader: ClassLoader): Class<*> {
        return TYPE_MAP[typeName]
            ?: try { classLoader.loadClass(typeName) }
            catch (e: ClassNotFoundException) {
                Class.forName(typeName)
            }
    }
}
