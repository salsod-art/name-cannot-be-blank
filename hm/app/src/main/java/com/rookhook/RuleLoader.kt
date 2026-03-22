package com.roothook

import android.util.Log
import org.json.JSONObject
import java.io.File

object RuleLoader {
    private const val TAG     = "RootHook"
    const val BASE_DIR        = "/data/media/0/RootHook"
    const val SCRIPTS_FILE    = "$BASE_DIR/scripts.json"
    const val LOG_DIR         = "$BASE_DIR/logs"

    fun ensureDirs() {
        File(BASE_DIR).mkdirs()
        File(LOG_DIR).mkdirs()
    }

    fun loadRulesForPackage(pkg: String): List<HookRule> {
        return try {
            val file = File(SCRIPTS_FILE)
            if (!file.exists()) return emptyList()
            val json    = org.json.JSONObject(file.readText().trim())
            val scripts = json.optJSONArray(pkg) ?: return emptyList()
            val rules   = mutableListOf<HookRule>()
            for (i in 0 until scripts.length()) {
                val script = scripts.getJSONObject(i)
                if (!script.optBoolean("enabled", true)) continue
                val code = script.optString("code", "")
                rules.addAll(parseScript(pkg, code))
            }
            Log.i(TAG, "Loaded ${rules.size} hooks for $pkg")
            rules
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load scripts: ${e.message}")
            emptyList()
        }
    }

    private fun parseScript(pkg: String, code: String): List<HookRule> {
        val rules = mutableListOf<HookRule>()
        // Find all hook({...}) blocks in the code
        val regex = Regex("""hook\s*\(\s*\{([^}]+)\}\s*\)""", RegexOption.DOT_MATCHES_ALL)
        regex.findAll(code).forEach { match ->
            try {
                val block = match.groupValues[1]
                fun field(name: String): String? {
                    val r = Regex("""$name\s*:\s*["']?([^"',\n\]]+)["']?""")
                    return r.find(block)?.groupValues?.get(1)?.trim()
                }
                val cls    = field("class")    ?: return@forEach
                val method = field("method")   ?: return@forEach
                val action = field("action")   ?: "log"
                val retVal = field("returnValue")
                val params = Regex("""params\s*:\s*\[([^\]]*)]""")
                    .find(block)?.groupValues?.get(1)
                    ?.split(",")?.map { it.trim().trim('"').trim('\'') }
                    ?.filter { it.isNotEmpty() } ?: emptyList()
                rules.add(HookRule(
                    id            = "hook_${rules.size}",
                    enabled       = true,
                    targetPackage = pkg,
                    className     = cls,
                    methodName    = method,
                    action        = action,
                    returnValue   = retVal,
                    paramTypes    = params,
                    notes         = ""
                ))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse hook block: ${e.message}")
            }
        }
        return rules
    }

    fun writeLog(pkg: String, ruleId: String, methodName: String, args: String, result: String) {
        try {
            val logFile   = File("$LOG_DIR/$pkg.log")
            val timestamp = System.currentTimeMillis()
            val line      = """{"t":$timestamp,"rule":"$ruleId","method":"$methodName","args":${esc(args)},"result":${esc(result)}}""" + "\n"
            logFile.appendText(line)
            if (logFile.length() > 200_000) {
                val lines = logFile.readLines()
                logFile.writeText(lines.takeLast(500).joinToString("\n") + "\n")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Log write failed: ${e.message}")
        }
    }

    private fun esc(s: String) = "\"" + s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").take(500) + "\""
}
