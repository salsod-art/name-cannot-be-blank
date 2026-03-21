package com.roothook

import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        private const val TAG = "RootHook"
        private val SKIP = setOf("android", "com.android.systemui", "com.roothook", "org.lsposed.manager")
    }

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        Log.i(TAG, "RootHook loaded in Zygote")
        RuleLoader.ensureDirs()
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName
        if (pkg in SKIP) return

        try {
            val rules = RuleLoader.loadRulesForPackage(pkg)
            if (rules.isEmpty()) return

            Log.i(TAG, "Injecting ${rules.size} hook(s) into $pkg")
            HookEngine.applyRules(rules, lpparam.classLoader)
        } catch (e: Exception) {
            Log.e(TAG, "Hook injection failed for $pkg: ${e.message}")
        }
    }
}
