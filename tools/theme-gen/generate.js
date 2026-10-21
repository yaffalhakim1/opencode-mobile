/**
 * Generates the app's M3 themes.
 *
 * Every colour role comes from Material 3's own colour utilities, so a theme is a
 * real M3 scheme generated from one seed colour rather than a hand-picked set of
 * hexes. That is what makes the roles agree with each other: `primary` and
 * `onPrimary` are a measured tonal pair, not two values that happened to look
 * fine side by side.
 *
 * Output, all under `app/src/main/java/dev/opencode/mobile/ui/theme/`:
 *   - `Themes.kt`            the `ThemeFamily` enum and `ThemeColors` holder
 *   - `Scheme<Name>.kt`      one file per theme, light and dark
 *
 * The output is committed as plain Kotlin. Nothing here ships in the APK — this
 * runs once, and the app reads static colours, so switching theme costs nothing
 * at runtime and there is no colour library on the classpath.
 *
 * Regenerate with:
 *   cd tools/theme-gen && npm install && npm run generate
 */
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import {
  Blend,
  Hct,
  MaterialDynamicColors,
  SchemeContent,
  SchemeExpressive,
  SchemeFidelity,
  SchemeMonochrome,
  SchemeNeutral,
  SchemeTonalSpot,
  SchemeVibrant,
  TonalPalette,
  argbFromHex,
} from "@material/material-color-utilities";

const here = dirname(fileURLToPath(import.meta.url));
const themeDir = join(here, "..", "..", "app", "src", "main", "java", "dev", "opencode", "mobile", "ui", "theme");

/** The M3 scheme variants, by the name the theme list refers to them by. */
const VARIANTS = {
  tonalSpot: SchemeTonalSpot,
  vibrant: SchemeVibrant,
  expressive: SchemeExpressive,
  content: SchemeContent,
  fidelity: SchemeFidelity,
  neutral: SchemeNeutral,
  monochrome: SchemeMonochrome,
};

/**
 * The themes offered in Settings.
 *
 * `seed` is the single colour the whole scheme is derived from, so it is the one
 * value to change to retune a theme. `variant` decides how far the scheme strays
 * from it: `tonalSpot` keeps surfaces calm, `vibrant` pushes the accent,
 * `neutral` drains the chroma, `monochrome` removes hue entirely.
 */
const THEMES = [
  {
    id: "OpenCode",
    label: "OpenCode",
    description: "Signal green, the app's own colour",
    seed: "#00DA7D",
    variant: "tonalSpot",
  },
  {
    id: "Midnight",
    label: "Midnight",
    description: "Deep indigo, quiet and cool",
    seed: "#4F5BD5",
    variant: "tonalSpot",
  },
  {
    id: "Tide",
    label: "Tide",
    description: "Teal, clear and calm",
    seed: "#0EA5A5",
    variant: "tonalSpot",
  },
  {
    id: "Plum",
    label: "Plum",
    description: "Muted purple",
    seed: "#9B4DCA",
    variant: "tonalSpot",
  },
  {
    id: "Ember",
    label: "Ember",
    description: "Warm orange, high energy",
    seed: "#FF6B35",
    variant: "vibrant",
  },
  {
    id: "Sand",
    label: "Sand",
    description: "Warm neutral, low chroma",
    seed: "#C8A165",
    variant: "neutral",
  },
  {
    id: "Mono",
    label: "Mono",
    description: "Greyscale, no hue at all",
    seed: "#808080",
    variant: "monochrome",
  },
];

/**
 * Every role `ColorScheme` declares, in its declared order.
 *
 * The names are the `ColorScheme` property names, so the generated Kotlin passes
 * them straight to `lightColorScheme`/`darkColorScheme`. All 48 are filled in:
 * leaving any to the factory default would quietly hand that role the baseline
 * M3 purple in a theme that is not purple.
 */
const ROLES = [
  "primary",
  "onPrimary",
  "primaryContainer",
  "onPrimaryContainer",
  "inversePrimary",
  "secondary",
  "onSecondary",
  "secondaryContainer",
  "onSecondaryContainer",
  "tertiary",
  "onTertiary",
  "tertiaryContainer",
  "onTertiaryContainer",
  "background",
  "onBackground",
  "surface",
  "onSurface",
  "surfaceVariant",
  "onSurfaceVariant",
  "surfaceTint",
  "inverseSurface",
  "inverseOnSurface",
  "error",
  "onError",
  "errorContainer",
  "onErrorContainer",
  "outline",
  "outlineVariant",
  "scrim",
  "surfaceBright",
  "surfaceContainer",
  "surfaceContainerHigh",
  "surfaceContainerHighest",
  "surfaceContainerLow",
  "surfaceContainerLowest",
  "surfaceDim",
  "primaryFixed",
  "primaryFixedDim",
  "onPrimaryFixed",
  "onPrimaryFixedVariant",
  "secondaryFixed",
  "secondaryFixedDim",
  "onSecondaryFixed",
  "onSecondaryFixedVariant",
  "tertiaryFixed",
  "tertiaryFixedDim",
  "onTertiaryFixed",
  "onTertiaryFixedVariant",
];

/** A green that reads as "done" regardless of the theme's own hue. */
const SUCCESS_SEED = "#00A86B";

/** `0xAARRGGBB` -> `0xFFRRGGBB` Kotlin literal. */
function hex(argb) {
  const rgb = (argb >>> 0) & 0xffffff;
  return `0xFF${rgb.toString(16).toUpperCase().padStart(6, "0")}`;
}

function rgb(argb) {
  return [(argb >> 16) & 0xff, (argb >> 8) & 0xff, argb & 0xff];
}

/** WCAG 2.x relative luminance. */
function luminance(argb) {
  const [r, g, b] = rgb(argb).map((channel) => {
    const v = channel / 255;
    return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

/** WCAG contrast ratio between two opaque colours. */
function contrast(a, b) {
  const la = luminance(a);
  const lb = luminance(b);
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
}

/**
 * The pairs the app actually renders, and the ratio each one needs.
 *
 * Text and icon-on-container pairs need 4.5:1. The brand and success colours are
 * indicators — a caret, a live dot, a status glyph — so they are held to the 3:1
 * non-text threshold instead.
 */
const PAIRS = [
  ["onPrimary", "primary", 4.5],
  ["onPrimaryContainer", "primaryContainer", 4.5],
  ["onSecondary", "secondary", 4.5],
  ["onSecondaryContainer", "secondaryContainer", 4.5],
  ["onTertiary", "tertiary", 4.5],
  ["onTertiaryContainer", "tertiaryContainer", 4.5],
  ["onError", "error", 4.5],
  ["onErrorContainer", "errorContainer", 4.5],
  ["onSurface", "surface", 4.5],
  ["onBackground", "background", 4.5],
  ["onSurfaceVariant", "surfaceVariant", 4.5],
  // The app paints its own code-block and card surfaces, so the text on them has
  // to be measured against those surfaces, not the page behind them.
  ["onSurface", "surfaceContainerHighest", 4.5],
  ["onSurfaceVariant", "surfaceContainerHighest", 4.5],
  ["onSurface", "surfaceContainerLow", 4.5],
  ["onSurface", "surfaceContainerHigh", 4.5],
  ["onSurfaceVariant", "surfaceContainerHigh", 4.5],
];

/** Builds one mode of one theme, plus the two colours that are not M3 roles. */
function build(theme, dark) {
  const seed = argbFromHex(theme.seed);
  const scheme = new VARIANTS[theme.variant](Hct.fromInt(seed), dark, 0);

  const roles = {};
  for (const role of ROLES) {
    const dynamicColor = MaterialDynamicColors[role];
    if (!dynamicColor) throw new Error(`no dynamic colour named ${role}`);
    roles[role] = dynamicColor.getArgb(scheme);
  }

  // The brand is the seed's own hue at a tone that carries on the surface it sits
  // on: bright in dark mode, deep enough to read in light mode. Using the seed's
  // palette rather than `primary` keeps the mark vivid instead of letting the
  // tonal mapping mute it.
  const brand = scheme.primaryPalette.tone(dark ? 80 : 45);

  // Success is harmonised towards the theme's hue, so a green tick does not read
  // as a foreign colour dropped into a purple app.
  const successPalette = TonalPalette.fromInt(Blend.harmonize(argbFromHex(SUCCESS_SEED), seed));
  const success = successPalette.tone(dark ? 80 : 40);

  return { roles, brand, success };
}

// ---- measure before writing -------------------------------------------------

const failures = [];
const generated = THEMES.map((theme) => {
  const light = build(theme, false);
  const dark = build(theme, true);

  for (const [mode, entry] of [["light", light], ["dark", dark]]) {
    for (const [fg, bg, minimum] of PAIRS) {
      const ratio = contrast(entry.roles[fg], entry.roles[bg]);
      if (ratio + 1e-9 < minimum) {
        failures.push(`${theme.id} ${mode}: ${fg} on ${bg} is ${ratio.toFixed(2)}:1, needs ${minimum}:1`);
      }
    }
    for (const [name, color] of [["brand", entry.brand], ["success", entry.success]]) {
      const ratio = contrast(color, entry.roles.surface);
      if (ratio < 3) {
        failures.push(`${theme.id} ${mode}: ${name} on surface is ${ratio.toFixed(2)}:1, needs 3:1`);
      }
    }
  }

  return { theme, light, dark };
});

if (failures.length > 0) {
  console.error("Contrast failures — nothing written:\n" + failures.map((f) => "  " + f).join("\n"));
  process.exit(1);
}

// ---- emit ------------------------------------------------------------------

const GENERATED_BANNER = `// GENERATED FILE — do not edit by hand.
//
// Colours come from Material 3's colour utilities: one seed colour expanded into
// a full tonal scheme by the M3 algorithm. To add or retune a theme, edit
// tools/theme-gen/generate.js and run it:
//
//   cd tools/theme-gen && npm install && npm run generate
`;

function roleArgs(entry, indent) {
  return ROLES.map((role) => `${indent}${role} = Color(${hex(entry.roles[role])}),`).join("\n");
}

function schemeFile({ theme, light, dark }) {
  return `${GENERATED_BANNER}
package dev.opencode.mobile.ui.theme

import androidx.compose.ui.graphics.Color

/** The ${theme.label} theme: ${theme.description}. */
internal val ${theme.id}Scheme = ThemeColors(
    light = schemeOf(
        isDark = false,
${roleArgs(light, "        ")}
    ),
    dark = schemeOf(
        isDark = true,
${roleArgs(dark, "        ")}
    ),
    lightBrand = Color(${hex(light.brand)}),
    darkBrand = Color(${hex(dark.brand)}),
    lightSuccess = Color(${hex(light.success)}),
    darkSuccess = Color(${hex(dark.success)}),
)
`;
}

const enumFile = `${GENERATED_BANNER}
package dev.opencode.mobile.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * A theme's two modes, plus the two colours that are not M3 roles.
 *
 * Both schemes are built up front: there are only a handful of themes and a
 * scheme is 48 immutable colours, so building one at the moment it is selected
 * would be work for no reason.
 */
internal class ThemeColors(
    val light: ColorScheme,
    val dark: ColorScheme,
    /** The vivid seed-hue mark used for the logo, the caret and live pulses. */
    val lightBrand: Color,
    val darkBrand: Color,
    /** "Done" green, harmonised towards the theme's own hue. */
    val lightSuccess: Color,
    val darkSuccess: Color,
)

/**
 * The themes offered in Settings.
 *
 * [label] and [description] are what the user sees. Adding a theme means adding
 * an entry to the generator and running it — nothing else in the app changes.
 */
enum class ThemeFamily(
    val label: String,
    val description: String,
    private val colors: ThemeColors,
) {
${generated.map(({ theme }) => `    ${theme.id}("${theme.label}", "${theme.description}", ${theme.id}Scheme),`).join("\n")}

    ;

    fun scheme(dark: Boolean): ColorScheme = if (dark) colors.dark else colors.light
    fun brand(dark: Boolean): Color = if (dark) colors.darkBrand else colors.lightBrand
    fun success(dark: Boolean): Color = if (dark) colors.darkSuccess else colors.lightSuccess

    companion object {
        /** The theme the app ships with, and where an unknown stored value lands. */
        val Default: ThemeFamily = ${THEMES[0].id}

        fun fromStored(stored: String?): ThemeFamily =
            entries.firstOrNull { it.name == stored } ?: Default
    }
}

/**
 * The full role list, so each theme file reads as a list of values rather than
 * repeating the factory argument names it was generated from.
 */
internal fun schemeOf(
    isDark: Boolean,
${ROLES.map((role) => `    ${role}: Color,`).join("\n")}
): ColorScheme = if (isDark) {
    darkColorScheme(
${ROLES.map((role) => `        ${role} = ${role},`).join("\n")}
    )
} else {
    lightColorScheme(
${ROLES.map((role) => `        ${role} = ${role},`).join("\n")}
    )
}
`;

mkdirSync(themeDir, { recursive: true });
for (const entry of generated) {
  writeFileSync(join(themeDir, `Scheme${entry.theme.id}.kt`), schemeFile(entry), "utf8");
}
writeFileSync(join(themeDir, "Themes.kt"), enumFile, "utf8");

console.log(`wrote Themes.kt and ${generated.length} Scheme*.kt files`);
console.log(`${THEMES.length} themes x 2 modes x ${ROLES.length} roles`);
for (const { theme, light, dark } of generated) {
  console.log(
    `  ${theme.label.padEnd(10)} light ${hex(light.roles.primary)}  dark ${hex(dark.roles.primary)}`,
  );
}
console.log("contrast: every measured pair passes");
