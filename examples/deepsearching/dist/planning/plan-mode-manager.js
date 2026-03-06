"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.PlanModeManager = void 0;
const plan_parser_1 = require("./plan-parser");
const task_executor_1 = require("./task-executor");
const i18n_1 = require("../i18n");
const EnhancedAIService = Java.com.ai.assistance.operit.api.chat.EnhancedAIService;
const FunctionType = Java.com.ai.assistance.operit.data.model.FunctionType;
const Unit = Java.kotlin.Unit;
const Pair = Java.kotlin.Pair;
const Collections = Java.java.util.Collections;
const InputProcessingStateBase = "com.ai.assistance.operit.data.model.InputProcessingState$";
const TAG = "PlanModeManager";
const THINK_TAG = /<think(?:ing)?>[\s\S]*?(<\/think(?:ing)?>|\z)/gi;
const SEARCH_TAG = /<search>[\s\S]*?(<\/search>|\z)/gi;
function removeThinkingContent(raw) {
    return raw.replace(THINK_TAG, "").replace(SEARCH_TAG, "").trim();
}
function getI18n() {
    const locale = getLang();
    return (0, i18n_1.resolveDeepSearchI18n)(locale);
}
async function collectStreamToString(stream) {
    let buffer = "";
    const collector = {
        emit: function (value) {
            buffer += String(value !== null && value !== void 0 ? value : "");
            return Unit.INSTANCE;
        }
    };
    await stream.callSuspend("collect", collector);
    return buffer;
}
function toKotlinPairList(history) {
    const list = [];
    (history || []).forEach((item) => {
        var _d, _f;
        const role = item && item.length > 0 ? String((_d = item[0]) !== null && _d !== void 0 ? _d : "") : "";
        const content = item && item.length > 1 ? String((_f = item[1]) !== null && _f !== void 0 ? _f : "") : "";
        list.push(new Pair(role, content));
    });
    return list;
}
function newInputProcessingState(kind, message) {
    const base = InputProcessingStateBase;
    if (kind === "Idle") {
        const idleCls = Java.type(base + "Idle");
        return idleCls.INSTANCE;
    }
    if (kind === "Completed") {
        const completedCls = Java.type(base + "Completed");
        return completedCls.INSTANCE;
    }
    return Java.newInstance(base + kind, String(message !== null && message !== void 0 ? message : ""));
}
async function sendPlanningMessage(aiService, context, message, chatHistory) {
    const emptyModelParams = Collections.emptyList();
    const onTokensUpdated = (_a, _b, _c) => Unit.INSTANCE;
    const onNonFatalError = (_value) => Unit.INSTANCE;
    const stream = await aiService.callSuspend("sendMessage", context, message, toKotlinPairList(chatHistory), emptyModelParams, false, true, null, false, onTokensUpdated, onNonFatalError);
    return collectStreamToString(stream);
}
class PlanModeManager {
    constructor(context, enhancedAIService) {
        this.isCancelled = false;
        this.context = context;
        this.enhancedAIService = enhancedAIService;
        this.taskExecutor = new task_executor_1.TaskExecutor(context, enhancedAIService);
    }
    cancel() {
        this.isCancelled = true;
        this.taskExecutor.cancelAllTasks();
        try {
            this.enhancedAIService.cancelConversation();
        }
        catch (_e) { }
        console.log(`${TAG} cancel called`);
    }
    shouldUseDeepSearchMode(message) {
        const normalized = String(message || "").trim();
        if (!normalized)
            return false;
        const i18n = getI18n();
        const indicators = (i18n.complexityIndicators || [])
            .map(item => String(item || "").trim())
            .filter(Boolean);
        return indicators.some(ind => normalized.toLowerCase().indexOf(ind.toLowerCase()) >= 0);
    }
    async executeDeepSearchMode(userMessage, chatHistory, workspacePath, maxTokens, tokenUsageThreshold, onChunk) {
        this.isCancelled = false;
        let output = "";
        this.taskExecutor.setChunkEmitter(onChunk);
        const append = (chunk) => {
            output += chunk;
            if (onChunk) {
                try {
                    onChunk(chunk);
                }
                catch (_e) { }
            }
        };
        try {
            const i18n = getI18n();
            const processingState = newInputProcessingState("Processing", i18n.planModeExecutingDeepSearch);
            try {
                this.enhancedAIService
                    .setInputProcessingState(processingState);
            }
            finally {
                try {
                    if (processingState)
                        Java.release(processingState);
                }
                catch (_e) { }
            }
            const executionGraph = await this.generateExecutionPlan(userMessage, chatHistory, workspacePath, maxTokens, tokenUsageThreshold);
            if (this.isCancelled) {
                append(`<log>🟡 ${i18n.planModeTaskCancelled}</log>\n`);
                return output;
            }
            if (!executionGraph) {
                append(`<error>❌ ${i18n.planModeFailedToGeneratePlan}</error>\n`);
                const idleState = newInputProcessingState("Idle");
                try {
                    this.enhancedAIService
                        .setInputProcessingState(idleState);
                }
                finally {
                    try {
                        if (idleState)
                            Java.release(idleState);
                    }
                    catch (_e) { }
                }
                return output;
            }
            append(`<plan>\n`);
            append(`<graph><![CDATA[${JSON.stringify(executionGraph)}]]></graph>\n`);
            const executingState = newInputProcessingState("Processing", i18n.planModeExecutingSubtasks);
            try {
                this.enhancedAIService
                    .setInputProcessingState(executingState);
            }
            finally {
                try {
                    if (executingState)
                        Java.release(executingState);
                }
                catch (_e) { }
            }
            const executionOutput = await this.taskExecutor.executeSubtasks(executionGraph, userMessage, chatHistory, workspacePath, maxTokens, tokenUsageThreshold);
            output += executionOutput;
            if (this.isCancelled) {
                append(`<log>🟡 ${i18n.planModeCancelling}</log>\n`);
                append(`</plan>\n`);
                return output;
            }
            append(`<log>🎯 ${i18n.planModeAllTasksCompleted}</log>\n`);
            append(`</plan>\n`);
            const summaryState = newInputProcessingState("Processing", i18n.planModeSummarizingResults);
            try {
                this.enhancedAIService
                    .setInputProcessingState(summaryState);
            }
            finally {
                try {
                    if (summaryState)
                        Java.release(summaryState);
                }
                catch (_e) { }
            }
            const summary = await this.taskExecutor.summarize(executionGraph, userMessage, chatHistory, workspacePath, maxTokens, tokenUsageThreshold);
            output += summary;
            const completedState = newInputProcessingState("Completed");
            try {
                this.enhancedAIService
                    .setInputProcessingState(completedState);
            }
            finally {
                try {
                    if (completedState)
                        Java.release(completedState);
                }
                catch (_e) { }
            }
            return output;
        }
        catch (e) {
            if (this.isCancelled) {
                append(`<log>🟡 ${getI18n().planModeCancelled}</log>\n`);
            }
            else {
                append(`<error>❌ ${getI18n().planModeExecutionFailed}: ${String(e)}</error>\n`);
            }
            const idleState = newInputProcessingState("Idle");
            try {
                this.enhancedAIService
                    .setInputProcessingState(idleState);
            }
            finally {
                try {
                    if (idleState)
                        Java.release(idleState);
                }
                catch (_e) { }
            }
            return output;
        }
        finally {
            this.isCancelled = false;
            this.taskExecutor.setChunkEmitter(undefined);
        }
    }
    buildPlanningRequest(userMessage) {
        const i18n = getI18n();
        return `${i18n.planGenerationPrompt}\n\n${i18n.planGenerationUserRequestPrefix}${userMessage}`.trim();
    }
    async generateExecutionPlan(userMessage, chatHistory, _workspacePath, _maxTokens, _tokenUsageThreshold) {
        try {
            const planningRequest = this.buildPlanningRequest(userMessage);
            const planningHistory = [["system", planningRequest]];
            const aiService = await EnhancedAIService.callSuspend("getAIServiceForFunction", this.context, FunctionType.CHAT);
            try {
                const planResponseRaw = await sendPlanningMessage(aiService, this.context, getI18n().planGenerateDetailedPlan, planningHistory);
                const planResponse = removeThinkingContent(String(planResponseRaw !== null && planResponseRaw !== void 0 ? planResponseRaw : "").trim());
                console.log(`${TAG} plan response`, planResponse);
                const graph = (0, plan_parser_1.parseExecutionGraph)(planResponse);
                return graph;
            }
            finally {
                try {
                    if (aiService)
                        Java.release(aiService);
                }
                catch (_e) { }
            }
        }
        catch (e) {
            console.log(`${TAG} generate plan error`, String(e));
            return null;
        }
    }
}
exports.PlanModeManager = PlanModeManager;
