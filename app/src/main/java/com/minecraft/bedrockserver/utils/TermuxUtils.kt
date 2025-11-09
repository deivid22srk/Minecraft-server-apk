package com.minecraft.bedrockserver.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import java.io.File

object TermuxUtils {
    private const val TAG = "TermuxUtils"
    private const val TERMUX_PACKAGE = "com.termux"
    
    fun isTermuxInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    fun getTermuxPhpPath(): String {
        return "/data/data/com.termux/files/usr/bin/php"
    }
    
    fun openTermux(context: Context) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
            if (intent != null) {
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao abrir Termux", e)
        }
    }
    
    fun openTermuxDownload(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://f-droid.org/packages/com.termux/")
        }
        context.startActivity(intent)
    }
    
    fun executeTermuxCommand(context: Context, command: String): Boolean {
        return try {
            if (!isTermuxInstalled(context)) {
                return false
            }
            
            val intent = Intent().apply {
                setClassName(TERMUX_PACKAGE, "$TERMUX_PACKAGE.app.TermuxActivity")
                action = "com.termux.RUN_COMMAND"
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao executar comando Termux", e)
            false
        }
    }
    
    fun installPhpInTermux(context: Context): Boolean {
        return executeTermuxCommand(context, "pkg install -y php && echo 'PHP instalado com sucesso!'")
    }
    
    fun checkPhpVersion(phpPath: String): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf(phpPath, "-v"))
            val reader = process.inputStream.bufferedReader()
            val output = reader.readLine()
            process.waitFor()
            output
        } catch (e: Exception) {
            null
        }
    }
}
