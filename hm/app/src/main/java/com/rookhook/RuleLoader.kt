package com.roothook

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object RuleLoader {

    private const val TAG       = "RootHook"
    const val BASE_DIR          = "/data/media/0/RootHook"
    const val RULES_FILE        = "$BASE_DIR/rules.json"
    const val LOG_DIR           = "$BASE_DIR/logs"
    const val ACTIVE_PKG_FILE   = "$BASE_DIR/active_package"

    fun ensureDirs() {
        File(BASE_DIR).mkdirs()
        File(LOG_DIR).mkdirs()
    }

    /** Load all rules for a specific package from rules.json */
    fun loadRulesForPackage(pkg: String): List<HookRule> {
        return try {
            val file = File(RULES_FILE)
            if (!file.exists()) return emptyList()

            val json  = JSONArray(file.readText().trim())
            val rules = mutableListOf<HookRule>()

            for (i in 0 until json.length()) {
                try {
                    val rule = HookRule.fromJson(json.getJSONObject(i))
                    if (rule.enabled && rule.targetPackage == pkg) {
                        rules.add(rule)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping malformed rule at index $i: ${e.message}")
                }
            }

            Log.i(TAG, "Loaded ${rules.size} rules for $pkg")
            rules
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load rules: ${e.message}")
            emptyList()
        }
    }

    /** Load all unique package names that have at least one enabled rule */
    fun loadTargetPackages(): Set<String> {
        return try {
            val file = File(RULES_FILE)
            if (!file.exists()) return emptySet()

            val json = JSONArray(file.readText().trim())
            val pkgs = mutableSetOf<String>()
            for (i in 0 until json.length()) {
                val obj = json.optJSONObject(i) ?: continue
                if (obj.optBoolean("enabled", true)) {
                    val pkg = obj.optString("targetPackage", "")
                    if (pkg.isNotBlank()) pkgs.add(pkg)
                }
            }
            pkgs
        } catch (e: Exception) {
            emptySet()
        }
    }

    /** Write a log entry to the per-package log file */
    fun writeLog(pkg: String, ruleId: String, methodName: String, args: String, result: String) {
        try {
            val logFile = File("$LOG_DIR/$pkg.log")
            val timestamp = System.currentTimeMillis()
            val line = """{"t":$timestamp,"rule":"$ruleId","method":"$methodName","args":${escapeForJson(args)},"result":${escapeForJson(result)}}""" + "\n"
            logFile.appendText(line)

            // Keep log file under 200KB — trim old entries
            if (logFile.length() > 200_000) {
                val lines = logFile.readLines()
                logFile.writeText(lines.takeLast(500).joinToString("\n") + "\n")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Log write failed: ${e.message}")
        }
    }

    private fun escapeForJson(s: String): String {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "").take(500) + "\""
    }
}
