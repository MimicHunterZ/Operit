import type { ToolParams } from './core';
import type { ComposeDslScreen } from './compose-dsl';

export namespace ToolPkg {
    export type LocalizedText = string | { [lang: string]: string };

    export type JsonPrimitive = string | number | boolean | null;

    export type JsonValue = JsonPrimitive | JsonValue[] | { [key: string]: JsonValue };

    export interface JsonObject {
        [key: string]: JsonValue;
    }

    export type AppLifecycleEvent =
        | "application_on_create"
        | "application_on_foreground"
        | "application_on_background"
        | "application_on_low_memory"
        | "application_on_trim_memory"
        | "application_on_terminate"
        | "activity_on_create"
        | "activity_on_start"
        | "activity_on_resume"
        | "activity_on_pause"
        | "activity_on_stop"
        | "activity_on_destroy";

    export type HookEventName =
        | AppLifecycleEvent
        | "message_processing"
        | "xml_render"
        | "input_menu_toggle";

    export type HookReturn = JsonValue | void | Promise<JsonValue | void>;

    export type HookHandler<TEvent> = (event: TEvent) => HookReturn;

    export type AppLifecycleHookReturn =
        | JsonValue
        | void
        | Promise<JsonValue | void>;

    export interface MessageProcessingHookObjectResult extends JsonObject {
        matched?: boolean;
        text?: string;
        content?: string;
        chunks?: string[];
    }

    export type MessageProcessingHookReturnValue =
        | boolean
        | string
        | MessageProcessingHookObjectResult
        | null
        | void;

    export type MessageProcessingHookReturn =
        | MessageProcessingHookReturnValue
        | Promise<MessageProcessingHookReturnValue>;

    export interface XmlRenderHookObjectResult {
        handled?: boolean;
        text?: string;
        content?: string;
        composeDsl?: {
            screen: ComposeDslScreen;
            state?: JsonObject;
            memo?: JsonObject;
            moduleSpec?: JsonObject;
        };
    }

    export type XmlRenderHookReturn =
        | string
        | XmlRenderHookObjectResult
        | null
        | void
        | Promise<string | XmlRenderHookObjectResult | null | void>;

    export interface InputMenuToggleDefinitionResult extends JsonObject {
        id: string;
        title: string;
        description?: string;
        isChecked?: boolean;
    }

    export interface InputMenuToggleObjectResult extends JsonObject {
        toggles?: InputMenuToggleDefinitionResult[];
    }

    export type InputMenuToggleHookReturn =
        | InputMenuToggleDefinitionResult[]
        | InputMenuToggleObjectResult
        | null
        | void
        | Promise<InputMenuToggleDefinitionResult[] | InputMenuToggleObjectResult | null | void>;

    export type AppLifecycleHookHandler =
        (event: AppLifecycleHookEvent) => AppLifecycleHookReturn;

    export type MessageProcessingHookHandler =
        (event: MessageProcessingHookEvent) =>
            MessageProcessingHookReturnValue
            | Promise<MessageProcessingHookReturnValue>;

    export type XmlRenderHookHandler =
        (event: XmlRenderHookEvent) => XmlRenderHookReturn;

    export type InputMenuToggleHookHandler =
        (event: InputMenuToggleHookEvent) => InputMenuToggleHookReturn;

    export interface HookEventBase<
        TEventName extends string,
        TPayload extends JsonObject = JsonObject
    > {
        event: TEventName;
        eventName: TEventName;
        eventPayload: TPayload;
        toolPkgId?: string;
        containerPackageName?: string;
        functionName?: string;
        pluginId?: string;
        hookId?: string;
        timestampMs?: number;
    }

    export interface AppLifecycleEventPayload extends JsonObject {
        extras?: JsonObject;
    }

    export interface MessageProcessingEventPayload extends JsonObject {
        messageContent?: string;
        chatHistory?: Array<[string, string]>;
        workspacePath?: string;
        maxTokens?: number;
        tokenUsageThreshold?: number;
        probeOnly?: boolean;
        executionId?: string;
    }

    export interface XmlRenderEventPayload extends JsonObject {
        xmlContent?: string;
        tagName?: string;
    }

    export interface InputMenuToggleEventPayload extends JsonObject {
        action?: "create" | "toggle" | string;
        toggleId?: string;
    }

    export interface AppLifecycleHookEvent
        extends HookEventBase<AppLifecycleEvent, AppLifecycleEventPayload> {}

    export interface MessageProcessingHookEvent
        extends HookEventBase<"message_processing", MessageProcessingEventPayload> {}

    export interface XmlRenderHookEvent
        extends HookEventBase<"xml_render", XmlRenderEventPayload> {}

    export interface InputMenuToggleHookEvent
        extends HookEventBase<"input_menu_toggle", InputMenuToggleEventPayload> {}

    export interface ToolboxUiModuleRegistration {
        id: string;
        runtime?: string;
        screen: ComposeDslScreen;
        params?: ToolParams;
        title?: LocalizedText;
    }

    export interface AppLifecycleHookRegistration {
        id: string;
        event: AppLifecycleEvent;
        function: AppLifecycleHookHandler;
    }

    export interface MessageProcessingPluginRegistration {
        id: string;
        function: MessageProcessingHookHandler;
    }

    export interface XmlRenderPluginRegistration {
        id: string;
        tag: string;
        function: XmlRenderHookHandler;
    }

    export interface InputMenuTogglePluginRegistration {
        id: string;
        function: InputMenuToggleHookHandler;
    }

    export interface Registry {
        registerToolboxUiModule(definition: ToolboxUiModuleRegistration): void;
        registerAppLifecycleHook(definition: AppLifecycleHookRegistration): void;
        registerMessageProcessingPlugin(definition: MessageProcessingPluginRegistration): void;
        registerXmlRenderPlugin(definition: XmlRenderPluginRegistration): void;
        registerInputMenuTogglePlugin(definition: InputMenuTogglePluginRegistration): void;
    }
}

declare global {
    function registerToolPkgToolboxUiModule(definition: ToolPkg.ToolboxUiModuleRegistration): void;

    function registerToolPkgAppLifecycleHook(definition: ToolPkg.AppLifecycleHookRegistration): void;

    function registerToolPkgMessageProcessingPlugin(definition: ToolPkg.MessageProcessingPluginRegistration): void;

    function registerToolPkgXmlRenderPlugin(definition: ToolPkg.XmlRenderPluginRegistration): void;

    function registerToolPkgInputMenuTogglePlugin(definition: ToolPkg.InputMenuTogglePluginRegistration): void;

    const ToolPkg: ToolPkg.Registry;
}
