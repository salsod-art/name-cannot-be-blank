package com.roothook

/**
 * Represents a single hook rule written by the user in the WebUI.
 *
 * JSON format:
 * {
 *   "id": "rule_1234",
 *   "enabled": true,
 *   "targetPackage": "com.example.app",
 *   "className": "com.example.app.SomeClass",
 *   "methodName": "someMethod",
 *   "action": "log",          // "log" | "replace" | "before" | "after"
 *   "returnValue": "true",    // used when action = "replace"
 *   "paramTypes": [],         // optional array of param type strings
 *   "notes": "user notes"
 * }
 */
data class HookRule(
    val id: String,
    val enabled: Boolean,
    val targetPackage: String,
    val className: String,
    val methodName: String,
    val action: String,         // log / replace / before / after
    val returnValue: String?,   // raw string, we try to coerce to correct type
    val paramTypes: List<String>,
    val notes: String
) {
    companion object {
        fun fromJson(obj: org.json.JSONObject): HookRule {
            val paramArray = obj.optJSONArray("paramTypes")
            val params = mutableListOf<String>()
            if (paramArray != null) {
                for (i in 0 until paramArray.length()) params.add(paramArray.getString(i))
            }
            return HookRule(
                id            = obj.optString("id", "rule_unknown"),
                enabled       = obj.optBoolean("enabled", true),
                targetPackage = obj.optString("targetPackage", ""),
                className     = obj.optString("className", ""),
                methodName    = obj.optString("methodName", ""),
                action        = obj.optString("action", "log"),
                returnValue   = obj.optString("returnValue", null),
                paramTypes    = params,
                notes         = obj.optString("notes", "")
            )
        }
    }
}
