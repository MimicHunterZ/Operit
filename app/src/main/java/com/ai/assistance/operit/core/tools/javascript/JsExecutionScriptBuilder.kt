package com.ai.assistance.operit.core.tools.javascript

internal fun buildExecutionScript(
    callIdJson: String,
    paramsJson: String,
    scriptJson: String,
    functionNameJson: String,
    timeoutSec: Long,
    preTimeoutSeconds: Long
): String {
    val safeTimeoutSec = if (timeoutSec <= 0L) 1L else timeoutSec
    val safePreTimeoutSec = if (preTimeoutSeconds <= 0L) 1L else preTimeoutSeconds
    val preTimeoutMs = safePreTimeoutSec * 1000L

    return """
        (function() {
            var callId = $callIdJson;
            var params = $paramsJson;
            var targetFunctionName = $functionNameJson;

            function getCallState() {
                return typeof window.__operitGetCallState === 'function'
                    ? window.__operitGetCallState(callId)
                    : null;
            }

            function readStringParam(name) {
                try {
                    if (!params || params[name] === undefined || params[name] === null) {
                        return '';
                    }
                    return String(params[name]).trim();
                } catch (_e) {
                    return '';
                }
            }

            function setStage(value) {
                try {
                    var callState = getCallState();
                    if (callState) {
                        callState.lastExecStage = String(value || '');
                    }
                } catch (_e) {
                }
            }

            function setLastModulePath(value) {
                try {
                    var callState = getCallState();
                    if (callState) {
                        callState.lastModulePath = String(value || '');
                    }
                } catch (_e) {
                }
            }

            function setRequireTrace(request, fromPath, resolvedPath) {
                try {
                    var callState = getCallState();
                    if (callState) {
                        callState.lastRequireRequest = String(request || '');
                        callState.lastRequireFrom = String(fromPath || '');
                        callState.lastRequireResolved = String(resolvedPath || '');
                    }
                } catch (_e) {
                }
            }

            function clearExecutionTimeouts() {
                try {
                    var callState = getCallState();
                    if (!callState) {
                        return;
                    }
                    if (callState.safetyTimeout) {
                        clearTimeout(callState.safetyTimeout);
                    }
                    if (callState.safetyTimeoutFinal) {
                        clearTimeout(callState.safetyTimeoutFinal);
                    }
                    callState.safetyTimeout = null;
                    callState.safetyTimeoutFinal = null;
                } catch (_e) {
                }
            }

            function emitResult(payload) {
                var callState = getCallState();
                if (!callState || callState.completed) {
                    return;
                }
                callState.completed = true;
                clearExecutionTimeouts();
                NativeInterface.setCallResult(callId, payload);
                if (typeof window.__operitCleanupCallSession === 'function') {
                    window.__operitCleanupCallSession(callId);
                }
            }

            function emitError(message) {
                var callState = getCallState();
                if (!callState || callState.completed) {
                    return;
                }
                callState.completed = true;
                clearExecutionTimeouts();
                NativeInterface.setCallError(callId, String(message || 'Unknown error'));
                if (typeof window.__operitCleanupCallSession === 'function') {
                    window.__operitCleanupCallSession(callId);
                }
            }

            function normalizePath(rawPath) {
                var parts = String(rawPath || '').replace(/\\/g, '/').split('/');
                var stack = [];
                for (var i = 0; i < parts.length; i += 1) {
                    var part = parts[i];
                    if (!part || part === '.') {
                        continue;
                    }
                    if (part === '..') {
                        if (stack.length > 0) {
                            stack.pop();
                        }
                        continue;
                    }
                    stack.push(part);
                }
                return stack.join('/');
            }

            function dirname(pathValue) {
                var normalized = normalizePath(pathValue);
                if (!normalized) {
                    return '';
                }
                var idx = normalized.lastIndexOf('/');
                return idx < 0 ? '' : normalized.substring(0, idx);
            }

            function resolveModulePath(request, fromPath) {
                var normalizedRequest = String(request || '').replace(/\\/g, '/').trim();
                if (!normalizedRequest) {
                    return '';
                }
                if (!(normalizedRequest.startsWith('.') || normalizedRequest.startsWith('/'))) {
                    return normalizedRequest;
                }
                if (normalizedRequest.startsWith('/')) {
                    return normalizePath(normalizedRequest);
                }
                var baseDir = dirname(fromPath || '');
                return normalizePath(baseDir ? (baseDir + '/' + normalizedRequest) : normalizedRequest);
            }

            function buildCandidatePaths(modulePath) {
                var normalized = normalizePath(modulePath);
                if (!normalized) {
                    return [];
                }
                var candidates = [normalized];
                var hasExt = /\.[a-z0-9]+$/i.test(normalized);
                if (!hasExt) {
                    candidates.push(normalized + '.js');
                    candidates.push(normalized + '.json');
                    candidates.push(normalized + '/index.js');
                    candidates.push(normalized + '/index.json');
                }
                return candidates;
            }

            function readToolPkgModule(packageTarget, modulePath) {
                if (!packageTarget) {
                    return null;
                }
                if (
                    typeof NativeInterface === 'undefined' ||
                    !NativeInterface ||
                    typeof NativeInterface.readToolPkgTextResource !== 'function'
                ) {
                    return null;
                }
                var candidates = buildCandidatePaths(modulePath);
                for (var i = 0; i < candidates.length; i += 1) {
                    var candidatePath = candidates[i];
                    var moduleText = NativeInterface.readToolPkgTextResource(packageTarget, candidatePath);
                    if (typeof moduleText === 'string' && moduleText.length > 0) {
                        return {
                            path: candidatePath,
                            text: moduleText
                        };
                    }
                }
                return null;
            }

            function createRegistrationScreenPlaceholder(modulePath) {
                function ScreenPlaceholder() {
                    return null;
                }
                try {
                    ScreenPlaceholder.__operit_toolpkg_module_path = String(modulePath || '').trim();
                } catch (_e) {
                }
                return ScreenPlaceholder;
            }

            function createFactory(scriptText) {
                var runtimePrelude = [
                    "var sendIntermediateResult = __operit_call_runtime.sendIntermediateResult;",
                    "var emit = __operit_call_runtime.emit;",
                    "var delta = __operit_call_runtime.delta;",
                    "var log = __operit_call_runtime.log;",
                    "var update = __operit_call_runtime.update;",
                    "var done = __operit_call_runtime.done;",
                    "var complete = __operit_call_runtime.complete;",
                    "var getEnv = __operit_call_runtime.getEnv;",
                    "var getState = __operit_call_runtime.getState;",
                    "var getLang = __operit_call_runtime.getLang;",
                    "var getCallerName = __operit_call_runtime.getCallerName;",
                    "var getChatId = __operit_call_runtime.getChatId;",
                    "var getCallerCardId = __operit_call_runtime.getCallerCardId;",
                    "var __handleAsync = __operit_call_runtime.handleAsync;",
                    "var console = __operit_call_runtime.console;",
                    "var reportDetailedError = __operit_call_runtime.reportDetailedError;",
                    "var __operit_call_runtime_ref = __operit_call_runtime;"
                ].join('\n');
                return new Function('module', 'exports', 'require', '__operit_call_runtime', runtimePrelude + '\n' + scriptText);
            }

            try {
                var registerCallSession =
                    typeof window.__operitRegisterCallSession === 'function'
                        ? window.__operitRegisterCallSession
                        : null;
                if (typeof registerCallSession !== 'function') {
                    throw new Error('call session runtime unavailable');
                }
                var callState = registerCallSession(callId, params);

                setStage('bootstrap');
                    clearExecutionTimeouts();

                    callState.lastExecFunction = targetFunctionName;
                    callState.lastModulePath = '';
                    callState.lastRequireRequest = '';
                    callState.lastRequireFrom = '';
                    callState.lastRequireResolved = '';

                    var pluginId = readStringParam('__operit_plugin_id') || readStringParam('pluginId') || readStringParam('hookId');
                    if (pluginId) {
                        callState.activePluginId = pluginId;
                    } else {
                        try {
                            delete callState.activePluginId;
                        } catch (_deleteError) {
                        }
                    }

                    callState.safetyTimeout = setTimeout(function() {
                        var activeCallState = getCallState();
                        if (!activeCallState || activeCallState.completed) {
                            return;
                        }
                        activeCallState.safetyTimeoutFinal = setTimeout(function() {
                            emitError("Script execution timed out after $safeTimeoutSec seconds");
                        }, 5000);
                    }, $preTimeoutMs);

                    function stringifyArgs(argsLike) {
                        var textParts = [];
                        for (var i = 0; i < argsLike.length; i += 1) {
                            var value = argsLike[i];
                            if (typeof value === 'string') {
                                textParts.push(value);
                            } else {
                                try {
                                    textParts.push(JSON.stringify(value));
                                } catch (_e) {
                                    textParts.push(String(value));
                                }
                            }
                        }
                        return textParts.join(' ');
                    }

                    function safeSerializeForCall(value) {
                        try {
                            return JSON.stringify(value);
                        } catch (serializeError) {
                            return JSON.stringify({
                                error: 'Failed to serialize value',
                                message: String(serializeError && serializeError.message ? serializeError.message : serializeError),
                                value: String(value).substring(0, 1000)
                            });
                        }
                    }

                    function normalizeComposeDslResult(value) {
                        if (!value || typeof value !== 'object') {
                            return value;
                        }
                        var composeDsl = value.composeDsl;
                        if (!composeDsl || typeof composeDsl !== 'object') {
                            return value;
                        }
                        if (!Object.prototype.hasOwnProperty.call(composeDsl, 'screen')) {
                            return value;
                        }
                        var screenRef = composeDsl.screen;
                        var resolved = '';
                        if (typeof screenRef === 'function') {
                            var marker = screenRef.__operit_toolpkg_module_path;
                            if (typeof marker === 'string') {
                                resolved = marker.trim().replace(/\\\\/g, '/');
                            }
                        } else if (screenRef && typeof screenRef === 'object' && typeof screenRef.default === 'function') {
                            var defaultMarker = screenRef.default.__operit_toolpkg_module_path;
                            if (typeof defaultMarker === 'string') {
                                resolved = defaultMarker.trim().replace(/\\\\/g, '/');
                            }
                        } else if (typeof screenRef === 'string') {
                            throw new Error("composeDsl.screen must be a compose_dsl screen function, not a string path");
                        }
                        if (!resolved) {
                            throw new Error("composeDsl.screen is missing a toolpkg module path marker");
                        }
                        composeDsl.screen = resolved;
                        return value;
                    }

                    function resolveActiveCallState() {
                        var activeCallState = getCallState();
                        if (!activeCallState || activeCallState.completed) {
                            return null;
                        }
                        return activeCallState;
                    }

                    function emitIntermediatePayload(value) {
                        var activeCallState = resolveActiveCallState();
                        if (!activeCallState) {
                            return;
                        }
                        NativeInterface.sendCallIntermediateResult(callId, safeSerializeForCall(value));
                    }

                    function completeForCall(value) {
                        var activeCallState = resolveActiveCallState();
                        if (!activeCallState) {
                            return;
                        }
                        var normalizedResult = normalizeComposeDslResult(value);
                        emitResult(safeSerializeForCall(normalizedResult));
                    }

                    function readCallContextValue(key, fallbackValue) {
                        var activeCallState = getCallState();
                        var currentParams =
                            activeCallState && activeCallState.params && typeof activeCallState.params === 'object'
                                ? activeCallState.params
                                : null;
                        var value = currentParams ? currentParams[key] : undefined;
                        if (value === null || value === undefined || value === '') {
                            return fallbackValue;
                        }
                        return String(value);
                    }

                    function getEnvForCall(key) {
                        var name = String(key || '').trim();
                        if (!name) {
                            return undefined;
                        }
                        var value = NativeInterface.getEnvForCall(callId, name);
                        if (value === null || value === undefined || value === '') {
                            return undefined;
                        }
                        return String(value);
                    }

                    function handleAsyncForCall(possiblePromise) {
                        if (!possiblePromise || typeof possiblePromise.then !== 'function') {
                            return false;
                        }

                        Promise.resolve(possiblePromise)
                            .then(function(result) {
                                if (!resolveActiveCallState()) {
                                    return;
                                }
                                completeForCall(result);
                            })
                            .catch(function(error) {
                                if (!resolveActiveCallState()) {
                                    return;
                                }
                                var report = null;
                                try {
                                    report = callRuntime.reportDetailedError(error, 'Async Promise Rejection');
                                } catch (_reportError) {
                                }
                                if (report && typeof report === 'object') {
                                    emitError(
                                        JSON.stringify({
                                            error: 'Promise rejection',
                                            details: report.details,
                                            formatted: report.formatted
                                        })
                                    );
                                    return;
                                }
                                emitError(
                                    'Promise rejection: ' +
                                        String(error && error.stack ? error.stack : (error && error.message ? error.message : error))
                                );
                            });
                        return true;
                    }

                    var callRuntime = {
                        emit: emitIntermediatePayload,
                        delta: emitIntermediatePayload,
                        log: emitIntermediatePayload,
                        update: emitIntermediatePayload,
                        sendIntermediateResult: emitIntermediatePayload,
                        done: completeForCall,
                        complete: completeForCall,
                        getEnv: getEnvForCall,
                        getState: function() { return readCallContextValue('__operit_package_state', undefined); },
                        getLang: function() { return readCallContextValue('__operit_package_lang', 'en'); },
                        getCallerName: function() { return readCallContextValue('__operit_package_caller_name', undefined); },
                        getChatId: function() { return readCallContextValue('__operit_package_chat_id', undefined); },
                        getCallerCardId: function() { return readCallContextValue('__operit_package_caller_card_id', undefined); },
                        reportDetailedError: function(error, context) {
                            if (typeof window.__operitReportDetailedErrorForCall === 'function') {
                                return window.__operitReportDetailedErrorForCall(callId, error, context);
                            }
                            return {
                                formatted: String(context || 'unknown') + ': ' + String(error),
                                details: { message: String(error), stack: String(error), lineNumber: 0 }
                            };
                        },
                        console: {
                            log: function() {
                                NativeInterface.logInfoForCall(callId, stringifyArgs(arguments));
                            },
                            info: function() {
                                NativeInterface.logInfoForCall(callId, stringifyArgs(arguments));
                            },
                            warn: function() {
                                NativeInterface.logInfoForCall(callId, stringifyArgs(arguments));
                            },
                            error: function() {
                                NativeInterface.logErrorForCall(callId, stringifyArgs(arguments));
                            }
                        },
                        handleAsync: handleAsyncForCall
                    };

                    var sendIntermediateResult = callRuntime.sendIntermediateResult;
                    var emit = callRuntime.emit;
                    var delta = callRuntime.delta;
                    var log = callRuntime.log;
                    var update = callRuntime.update;
                    var done = callRuntime.done;
                    var complete = callRuntime.complete;
                    var getEnv = callRuntime.getEnv;
                    var getState = callRuntime.getState;
                    var getLang = callRuntime.getLang;
                    var getCallerName = callRuntime.getCallerName;
                    var getChatId = callRuntime.getChatId;
                    var getCallerCardId = callRuntime.getCallerCardId;
                    var __handleAsync = callRuntime.handleAsync;
                    var __operit_call_runtime = callRuntime;

                    var registrationMode = readStringParam('__operit_registration_mode').toLowerCase() === 'true';
                    var packageTarget = readStringParam('__operit_ui_package_name') || readStringParam('toolPkgId');
                    var screenPath = normalizePath(
                        readStringParam('__operit_script_screen') ||
                        (params && params.moduleSpec && params.moduleSpec.screen ? String(params.moduleSpec.screen) : '')
                    );

                    var moduleCache = {};

                function executeModule(modulePath, moduleText, requireInternal) {
                    if (moduleCache[modulePath]) {
                        return moduleCache[modulePath].exports;
                    }

                    setStage('execute_required_module');
                    setLastModulePath(modulePath);

                    var localModule = { exports: {} };
                    moduleCache[modulePath] = localModule;

                    if (/\.json$/i.test(modulePath)) {
                        localModule.exports = JSON.parse(moduleText);
                        return localModule.exports;
                    }

                    var localRequire = function(nextName) {
                        return requireInternal(nextName, modulePath);
                    };

                    var factory = createFactory(moduleText);
                    var previousActiveModule = window.__operitActiveModule;
                    var previousActiveModuleExports = window.__operitActiveModuleExports;
                    window.__operitActiveModule = localModule;
                    window.__operitActiveModuleExports = localModule.exports;
                    try {
                        factory(localModule, localModule.exports, localRequire, callRuntime);
                    } finally {
                        window.__operitActiveModule = previousActiveModule;
                        window.__operitActiveModuleExports = previousActiveModuleExports;
                    }

                    if (typeof localModule.exports === 'function') {
                        try {
                            localModule.exports.__operit_toolpkg_module_path = modulePath;
                        } catch (_assignError) {
                        }
                    }
                    if (localModule.exports && typeof localModule.exports === 'object') {
                        var exportKeys = Object.keys(localModule.exports);
                        for (var i = 0; i < exportKeys.length; i += 1) {
                            var exportName = exportKeys[i];
                            var exportValue = localModule.exports[exportName];
                            if (typeof exportValue === 'function') {
                                try {
                                    exportValue.__operit_toolpkg_module_path = modulePath;
                                } catch (_assignError2) {
                                }
                            }
                        }
                    }
                    return localModule.exports;
                }

                function requireInternal(moduleName, fromPath) {
                    if (moduleName === 'lodash') {
                        return _;
                    }
                    if (moduleName === 'uuid') {
                        return {
                            v4: function() {
                                return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
                                    var r = Math.random() * 16 | 0;
                                    var v = c === 'x' ? r : ((r & 0x3) | 0x8);
                                    return v.toString(16);
                                });
                            }
                        };
                    }
                    if (moduleName === 'axios') {
                        return {
                            get: function(url, config) {
                                var requestParams = config ? Object.assign({}, { url: url }, config) : { url: url };
                                return toolCall("http_request", requestParams);
                            },
                            post: function(url, data, config) {
                                var requestParams = config ? Object.assign({}, { url: url, data: data }, config) : { url: url, data: data };
                                return toolCall("http_request", requestParams);
                            }
                        };
                    }

                    var request = String(moduleName || '').trim();
                    if (!(request.startsWith('.') || request.startsWith('/'))) {
                        return {};
                    }

                    var resolvedModulePath = resolveModulePath(request, fromPath || screenPath);
                    setStage("require_module");
                    setRequireTrace(request, String(fromPath || screenPath || '<root>'), resolvedModulePath);
                    setLastModulePath(resolvedModulePath);

                    if (registrationMode && /(^|\/)ui\/.+\.ui\.js$/i.test(String(resolvedModulePath || ''))) {
                        return createRegistrationScreenPlaceholder(resolvedModulePath);
                    }

                    var loadedModule = readToolPkgModule(packageTarget, resolvedModulePath);
                    if (!loadedModule) {
                        throw new Error(
                            'Cannot resolve module "' + request + '" from "' + (fromPath || screenPath || '<root>') + '"'
                        );
                    }
                    return executeModule(loadedModule.path, loadedModule.text, requireInternal);
                }

                var module = { exports: {} };
                var exports = module.exports;
                var require = function(moduleName) {
                    setStage("require_request");
                    setRequireTrace(String(moduleName || ''), String(screenPath || '<root>'), '');
                    return requireInternal(moduleName, screenPath);
                };

                setStage('compile_main_script');
                var mainFactory = createFactory($scriptJson);
                setStage('execute_main_script');
                var previousActiveModule = window.__operitActiveModule;
                var previousActiveModuleExports = window.__operitActiveModuleExports;
                window.__operitActiveModule = module;
                window.__operitActiveModuleExports = exports;
                try {
                    mainFactory(module, exports, require, callRuntime);
                } finally {
                    window.__operitActiveModule = previousActiveModule;
                    window.__operitActiveModuleExports = previousActiveModuleExports;
                }

                var moduleResult = { module: module, exports: module.exports };

                var rootModule = moduleResult.module;
                var rootExports = moduleResult.exports || (rootModule ? rootModule.exports : {}) || {};

                var inlineFunctionName = readStringParam('__operit_inline_function_name');
                var inlineFunctionSource = readStringParam('__operit_inline_function_source');
                if (inlineFunctionName && inlineFunctionSource) {
                    setStage('evaluate_inline_hook_function');
                    var inlineFunction = eval("(" + inlineFunctionSource + ")");
                    if (typeof inlineFunction !== 'function') {
                        throw new Error("inline hook source did not evaluate to function");
                    }
                    rootExports[inlineFunctionName] = inlineFunction;
                    if (rootModule && rootModule.exports) {
                        rootModule.exports[inlineFunctionName] = inlineFunction;
                    }
                }

                setStage('resolve_target_function');
                var targetFunction = null;
                if (rootExports && typeof rootExports[targetFunctionName] === 'function') {
                    targetFunction = rootExports[targetFunctionName];
                } else if (rootModule && rootModule.exports && typeof rootModule.exports[targetFunctionName] === 'function') {
                    targetFunction = rootModule.exports[targetFunctionName];
                } else if (typeof window[targetFunctionName] === 'function') {
                    targetFunction = window[targetFunctionName];
                }

                if (typeof targetFunction !== 'function') {
                    var availableFunctions = [];
                    if (rootExports && typeof rootExports === 'object') {
                        Object.keys(rootExports).forEach(function(key) {
                            if (typeof rootExports[key] === 'function') {
                                availableFunctions.push(key);
                            }
                        });
                    }
                    if (rootModule && rootModule.exports && typeof rootModule.exports === 'object') {
                        Object.keys(rootModule.exports).forEach(function(key) {
                            if (typeof rootModule.exports[key] === 'function' && availableFunctions.indexOf(key) < 0) {
                                availableFunctions.push(key);
                            }
                        });
                    }
                    emitError(
                        "Function '" +
                            targetFunctionName +
                            "' not found in script. Available functions: " +
                            (availableFunctions.length > 0 ? availableFunctions.join(", ") : "none")
                    );
                    return;
                }

                setStage('invoke_target_function');
                var previousModule = callState.currentModule;
                var previousExports = callState.currentModuleExports;
                var previousActiveModule = window.__operitActiveModule;
                var previousActiveModuleExports = window.__operitActiveModuleExports;
                callState.currentModule = rootModule;
                callState.currentModuleExports = rootExports;
                window.__operitActiveModule = rootModule;
                window.__operitActiveModuleExports = rootExports;
                var functionResult = null;
                try {
                    functionResult = targetFunction(params);
                } finally {
                    callState.currentModule = previousModule;
                    callState.currentModuleExports = previousExports;
                    window.__operitActiveModule = previousActiveModule;
                    window.__operitActiveModuleExports = previousActiveModuleExports;
                }

                setStage('handle_function_result');
                var handledAsync = false;
                try {
                    handledAsync = callRuntime.handleAsync(functionResult);
                } catch (asyncError) {
                    throw asyncError;
                }

                if (!handledAsync) {
                    var normalizedResult = normalizeComposeDslResult(functionResult);
                    var serialized = safeSerializeForCall(normalizedResult);
                    emitResult(serialized);
                }
            } catch (error) {
                var runtimeContext =
                    typeof window.__operitBuildRuntimeContext === 'function'
                        ? String(window.__operitBuildRuntimeContext(callId) || '')
                        : '';
                var runtimeContextText = runtimeContext ? ("\nRuntime Context: " + runtimeContext) : '';
                var stackText = error && error.stack ? ("\nStack: " + String(error.stack)) : '';
                emitError("Script error: " + (error && error.message ? error.message : String(error)) + runtimeContextText + stackText);
            }
        })();
    """.trimIndent()
}
