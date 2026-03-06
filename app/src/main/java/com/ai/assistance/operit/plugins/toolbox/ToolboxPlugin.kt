package com.ai.assistance.operit.plugins.toolbox

import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.core.tools.packTool.TOOLPKG_RUNTIME_COMPOSE_DSL
import com.ai.assistance.operit.plugins.OperitPlugin
import com.ai.assistance.operit.plugins.lifecycle.AppLifecycleEvent
import com.ai.assistance.operit.plugins.lifecycle.AppLifecycleHookParams
import com.ai.assistance.operit.plugins.lifecycle.AppLifecycleHookPlugin
import com.ai.assistance.operit.plugins.lifecycle.AppLifecycleHookPluginRegistry
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object ToolPkgToolboxScriptPlugin : ToolboxScriptPlugin {
    override val id: String = "builtin.toolbox.toolpkg-compose-dsl"

    override suspend fun createDefinitions(
        params: ToolboxScriptHookParams
    ): List<ToolboxScriptDefinition> {
        val context = params.context
        val packageManager =
            PackageManager.getInstance(
                context,
                AIToolHandler.getInstance(context)
            )
        return packageManager
            .getToolPkgToolboxUiModules(
                runtime = TOOLPKG_RUNTIME_COMPOSE_DSL,
                resolveContext = context
            )
            .map { module ->
                ToolboxScriptDefinition(
                    containerPackageName = module.containerPackageName,
                    uiModuleId = module.uiModuleId,
                    runtime = module.runtime,
                    title = module.title,
                    description = module.description
                )
            }
    }
}

private object ToolPkgAppLifecycleHookPlugin : AppLifecycleHookPlugin {
    private const val TAG = "ToolboxPlugin"

    override val id: String = "builtin.toolbox.toolpkg-app-lifecycle"

    override suspend fun onEvent(
        event: AppLifecycleEvent,
        params: AppLifecycleHookParams
    ) {
        val context = params.context
        val packageManager =
            PackageManager.getInstance(
                context,
                AIToolHandler.getInstance(context)
            )
        val hooks =
            withContext(Dispatchers.IO) {
                packageManager.getToolPkgAppLifecycleHooks(event.wireName)
            }

        for (hook in hooks) {
            val result =
                withContext(Dispatchers.IO) {
                    packageManager.runToolPkgMainHook(
                        containerPackageName = hook.containerPackageName,
                        functionName = hook.functionName,
                        event = hook.event,
                        pluginId = hook.hookId,
                        eventPayload =
                            mapOf(
                                "extras" to params.extras
                            )
                    )
                }
            result.onFailure { error ->
                AppLogger.e(
                    TAG,
                    "ToolPkg app lifecycle hook failed: ${hook.containerPackageName}:${hook.hookId}",
                    error
                )
            }
        }
    }
}

object ToolboxPlugin : OperitPlugin {
    override val id: String = "builtin.toolbox"

    override fun register() {
        ToolboxScriptPluginRegistry.register(ToolPkgToolboxScriptPlugin)
        AppLifecycleHookPluginRegistry.register(ToolPkgAppLifecycleHookPlugin)
    }
}
