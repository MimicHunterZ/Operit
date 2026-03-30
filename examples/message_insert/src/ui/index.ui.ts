import type { ComposeDslContext, ComposeNode } from "../../../types/compose-dsl";
import {
  loadSettings,
  resolveExtraInfoI18n,
  saveSettings,
  type ExtraInfoInjectionSettings,
} from "../shared";

function useStateValue<T>(ctx: ComposeDslContext, key: string, initialValue: T): {
  value: T;
  set: (value: T) => void;
} {
  const pair = ctx.useState<T>(key, initialValue);
  return { value: pair[0], set: pair[1] };
}

function readSettings(): ExtraInfoInjectionSettings {
  return loadSettings();
}

function createSectionTitle(ctx: ComposeDslContext, icon: string, title: string): ComposeNode {
  return ctx.UI.Row({ verticalAlignment: "center" }, [
    ctx.UI.Icon({ name: icon, tint: "primary", size: 20 }),
    ctx.UI.Spacer({ width: 8 }),
    ctx.UI.Text({
      text: title,
      style: "titleMedium",
      fontWeight: "bold",
      color: "primary",
    }),
  ]);
}

function createToggleCard(
  ctx: ComposeDslContext,
  title: string,
  subtitle: string,
  checked: boolean,
  onCheckedChange: (checked: boolean) => void
): ComposeNode {
  return ctx.UI.Surface(toggleCardStyle, [
    createToggleRow(ctx, title, subtitle, checked, onCheckedChange),
  ]);
}

type ToggleCardItem = {
  key: string;
  title: string;
  subtitle: string;
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
};

const toggleCardStyle = {
  fillMaxWidth: true,
  shape: { cornerRadius: 8 },
  containerColor: "surfaceVariant",
  alpha: 0.38,
} as const;

function createToggleRow(
  ctx: ComposeDslContext,
  title: string,
  subtitle: string,
  checked: boolean,
  onCheckedChange: (checked: boolean) => void
): ComposeNode {
  return ctx.UI.Row(
    {
      fillMaxWidth: true,
      padding: { horizontal: 14, vertical: 12 },
      verticalAlignment: "center",
      horizontalArrangement: "spaceBetween",
    },
    [
      ctx.UI.Column({ weight: 1, spacing: 4 }, [
        ctx.UI.Text({
          text: title,
          style: "bodyMedium",
          fontWeight: "medium",
        }),
        ctx.UI.Text({
          text: subtitle,
          style: "bodySmall",
          color: "onSurfaceVariant",
        }),
      ]),
      ctx.UI.Spacer({ width: 12 }),
      ctx.UI.Switch({
        checked,
        onCheckedChange,
      }),
    ]
  );
}

function createToggleGroupCard(
  ctx: ComposeDslContext,
  items: ToggleCardItem[]
): ComposeNode {
  const children: ComposeNode[] = [];

  items.forEach((item, index) => {
    children.push(
      createToggleRow(
        ctx,
        item.title,
        item.subtitle,
        item.checked,
        item.onCheckedChange
      )
    );

    if (index < items.length - 1) {
      children.push(
        ctx.UI.HorizontalDivider({
          padding: { horizontal: 14 },
          color: "outlineVariant",
          thickness: 1,
        })
      );
    }
  });

  return ctx.UI.Surface(toggleCardStyle, [
    ctx.UI.Column({ fillMaxWidth: true }, children),
  ]);
}

export default function Screen(ctx: ComposeDslContext): ComposeNode {
  const text = resolveExtraInfoI18n();
  const initial = readSettings();

  const masterEnabledState = useStateValue(ctx, "masterEnabled", initial.masterEnabled);
  const injectTimeState = useStateValue(ctx, "injectTime", initial.injectTime);
  const injectBatteryState = useStateValue(ctx, "injectBattery", initial.injectBattery);
  const injectWeatherState = useStateValue(ctx, "injectWeather", initial.injectWeather);
  const injectLocationState = useStateValue(ctx, "injectLocation", initial.injectLocation);
  const injectNotificationsState = useStateValue(
    ctx,
    "injectNotifications",
    initial.injectNotifications
  );
  const successMessageState = useStateValue(ctx, "successMessage", "");
  const errorMessageState = useStateValue(ctx, "errorMessage", "");
  const hasInitializedState = useStateValue(ctx, "hasInitialized", false);

  const syncSettings = (next: ExtraInfoInjectionSettings): void => {
    masterEnabledState.set(next.masterEnabled);
    injectTimeState.set(next.injectTime);
    injectBatteryState.set(next.injectBattery);
    injectWeatherState.set(next.injectWeather);
    injectLocationState.set(next.injectLocation);
    injectNotificationsState.set(next.injectNotifications);
  };

  const persistSettings = (patch: Partial<ExtraInfoInjectionSettings>, successMessage = ""): void => {
    try {
      const next = saveSettings(patch);
      syncSettings(next);
      errorMessageState.set("");
      successMessageState.set(successMessage);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error || "unknown");
      successMessageState.set("");
      errorMessageState.set(`${text.saveErrorPrefix}${message}`);
    }
  };

  const summaryLines = [
    masterEnabledState.value ? text.summaryMasterEnabled : text.summaryMasterDisabled,
    injectTimeState.value ? text.summaryTimeEnabled : text.summaryTimeDisabled,
    injectBatteryState.value ? text.summaryBatteryEnabled : text.summaryBatteryDisabled,
    injectWeatherState.value ? text.summaryWeatherEnabled : text.summaryWeatherDisabled,
    injectLocationState.value ? text.summaryLocationEnabled : text.summaryLocationDisabled,
    injectNotificationsState.value
      ? text.summaryNotificationsEnabled
      : text.summaryNotificationsDisabled,
    text.summaryRulesHint,
  ];

  const rootChildren: ComposeNode[] = [
    ctx.UI.Row({ verticalAlignment: "center" }, [
      ctx.UI.Icon({ name: "settings", tint: "primary", size: 24 }),
      ctx.UI.Spacer({ width: 8 }),
      ctx.UI.Text({
        text: text.toolboxTitle,
        style: "headlineSmall",
        fontWeight: "bold",
      }),
    ]),
    ctx.UI.Text({
      text: text.toolboxSubtitle,
      style: "bodyMedium",
      color: "onSurfaceVariant",
    }),
    ctx.UI.Surface({
      fillMaxWidth: true,
      shape: { cornerRadius: 12 },
      containerColor: "secondaryContainer",
    }, [
      ctx.UI.Row({ padding: { horizontal: 14, vertical: 12 }, verticalAlignment: "center" }, [
        ctx.UI.Icon({ name: "info", tint: "onSecondaryContainer", size: 18 }),
        ctx.UI.Spacer({ width: 8 }),
        ctx.UI.Text({
          text: text.toolboxBanner,
          style: "bodySmall",
          color: "onSecondaryContainer",
        }),
      ]),
    ]),

    createSectionTitle(ctx, "settings", text.masterSectionTitle),
    createToggleCard(
      ctx,
      text.masterToggleTitle,
      text.masterToggleDescription,
      masterEnabledState.value,
      checked => {
        persistSettings({ masterEnabled: checked });
      }
    ),

    createSectionTitle(ctx, "bolt", text.itemsSectionTitle),
    createToggleGroupCard(ctx, [
      {
        key: "time",
        title: text.timeToggleTitle,
        subtitle: text.timeToggleDescription,
        checked: injectTimeState.value,
        onCheckedChange: checked => {
          persistSettings({ injectTime: checked });
        },
      },
      {
        key: "battery",
        title: text.batteryToggleTitle,
        subtitle: text.batteryToggleDescription,
        checked: injectBatteryState.value,
        onCheckedChange: checked => {
          persistSettings({ injectBattery: checked });
        },
      },
      {
        key: "weather",
        title: text.weatherToggleTitle,
        subtitle: text.weatherToggleDescription,
        checked: injectWeatherState.value,
        onCheckedChange: checked => {
          persistSettings({ injectWeather: checked });
        },
      },
      {
        key: "location",
        title: text.locationToggleTitle,
        subtitle: text.locationToggleDescription,
        checked: injectLocationState.value,
        onCheckedChange: checked => {
          persistSettings({ injectLocation: checked });
        },
      },
      {
        key: "notifications",
        title: text.notificationsToggleTitle,
        subtitle: text.notificationsToggleDescription,
        checked: injectNotificationsState.value,
        onCheckedChange: checked => {
          persistSettings({ injectNotifications: checked });
        },
      },
    ]),

    createSectionTitle(ctx, "checkCircle", text.summarySectionTitle),
    ctx.UI.Card({
      fillMaxWidth: true,
      shape: { cornerRadius: 12 },
      containerColor: "primaryContainer",
      elevation: 1,
    }, [
      ctx.UI.Column({ padding: 16, spacing: 8 }, summaryLines.map((line, index) =>
        ctx.UI.Text({
          key: `summary-${index}`,
          text: line,
          style: index === summaryLines.length - 1 ? "bodySmall" : "bodyMedium",
          color: "onPrimaryContainer",
        })
      )),
    ]),
  ];

  if (successMessageState.value.trim()) {
    rootChildren.push(
      ctx.UI.Card({ containerColor: "primaryContainer", fillMaxWidth: true }, [
        ctx.UI.Row({ padding: { horizontal: 14, vertical: 12 }, verticalAlignment: "center" }, [
          ctx.UI.Icon({ name: "checkCircle", tint: "onPrimaryContainer" }),
          ctx.UI.Spacer({ width: 8 }),
          ctx.UI.Text({
            text: successMessageState.value,
            style: "bodyMedium",
            color: "onPrimaryContainer",
          }),
        ]),
      ])
    );
  }

  if (errorMessageState.value.trim()) {
    rootChildren.push(
      ctx.UI.Card({ containerColor: "errorContainer", fillMaxWidth: true }, [
        ctx.UI.Row({ padding: { horizontal: 14, vertical: 12 }, verticalAlignment: "center" }, [
          ctx.UI.Icon({ name: "error", tint: "onErrorContainer" }),
          ctx.UI.Spacer({ width: 8 }),
          ctx.UI.Text({
            text: errorMessageState.value,
            style: "bodyMedium",
            color: "onErrorContainer",
          }),
        ]),
      ])
    );
  }

  return ctx.UI.LazyColumn(
    {
      fillMaxSize: true,
      padding: 16,
      spacing: 16,
      onLoad: async () => {
        if (!hasInitializedState.value) {
          hasInitializedState.set(true);
          syncSettings(readSettings());
        }
      },
    },
    rootChildren
  );
}
