# AURA UI Design — Liquid Glass & Spring Motion

## 1. Visual Identity & Adaptive Themes (`ui/theme/Theme.kt`)
- **Dark Mode (Liquid Night):** `#05060F` deep navy-black canvas with `#0A1030` optical glass base and luminous artwork-derived ambient fields.
- **Light Mode (Crystal Day):** `#EEF2FA` pearl-silver canvas (modeled after the bright Liquid Glass reference sheets) with crystal-white translucent glass, crisp specular rims, and soft pastel ambient fields.
- **Theme Selection:** Supports `System` (follows Android's Dark Theme quick-setting tile automatically), `Dark`, and `Light` in **Settings ▸ Appearance ▸ Theme**.
- **Accents:** `Aurora` (Purple / Blue / Cyan), `Ember` (Coral / Magenta / Violet), `Emerald` (Mint / Teal / Blue), and `Mono` (Silver / Slate).

## 2. Liquid Glass Material Engine (`ui/glass/Glass.kt`)

### Preserved Real Backdrop Blur
`Modifier.blur()` in Compose only blurs a node's own content. AURA preserves its two-stage `GraphicsLayer` backdrop pipeline:
1. **Scene Recording:** `AuraRoot` records `AmbientBackground` + `ScreenHost` + `NowPlayingLayer` once per frame into `backdropLayer`.
2. **Backdrop Sampling:** Floating chrome (`FloatingNav`, `MiniPlayer`, `OverlayHost` menus/dropdowns/dialogs/sheets/toasts) re-draws `backdropLayer` offset by `-origin.x, -origin.y` into a per-surface `GraphicsLayer` with `BlurEffect(radius, radius, TileMode.Clamp)` (18 dp Balanced / 30 dp High) and a saturation `ColorFilter` (`1.48x` dark / `1.32x` light) on Android 12+ (API 31+).

### Multi-Layer Optical Treatment (Cached in `drawWithCache`)
Every `Modifier.glass(shape, level, tint, borderAlpha)` surface caches its geometry and brushes per size and renders:
1. **Floating Drop Shadow:** Downward-shifted translucent shadow path giving floating panels and pills physical elevation.
2. **Multi-Stop Translucent Body:** Vertical 3-stop gradient with higher upper-meniscus opacity, an optically clearer center window, and a grounded lower curve.
3. **Top & Bottom Internal Lens Crescents:** Luminous top-curve and bottom-curve internal reflections modeled after the 3D glass capsules and active navigation lens in the reference images.
4. **Upper-Left Grazing Specular Bloom & Dual Chromatic Caustics:** Soft radial highlight from the upper-left plus cyan/purple internal caustic bounce light in the lower corners.
5. **Inner Refraction Bevel Ring (`PathOperation.Difference`):** An inset ring (1.6 dp to 4.2 dp depending on `GlassLevel`) painted with a 4-stop refraction gradient (bright top-left internal bevel, subtle dark bottom wall thickness, and chromatic cyan/tint dispersion).
6. **Directional Specular Rim (`Stroke`):** 5-stop diagonal stroke brightest at the top-left (`0.78 * borderAlpha`) and bottom-right (`0.48 * borderAlpha`) with a subtle prismatic mid-glint.

### Material Levels (`GlassLevel`)
| Level | Visual Character | Where Used |
|---|---|---|
| `Subtle` | Recessed translucent well, thin 1.6 dp bevel | Track metadata cushion, search recent card, segmented track well, inactive toggles |
| `Regular` | Standard floating card, 2.4 dp bevel, soft shadow | Album/Track/Playlist cards, Settings groups, Quick tiles, Soft buttons, Dropdown pills |
| `Strong` | High-presence floating chrome, 3.2 dp bevel, deep shadow | Floating Nav container, Mini Player capsule, Popup menus, Dropdowns, Dialogs, Bottom sheets |
| `Lens` | 3D liquid droplet/pill with strong top/bottom crescents & 4.2 dp bevel | Active Nav tab lens, Toggle thumb droplet, Primary Play/Pause lens, Featured play button, Active chips |

## 3. Centralized Spring Motion System (`ui/glass/Motion.kt`)
All animations route through `Spr` presets (`spr()` / `sprRaw()`), which automatically collapse to `snap()` when reduced motion is enabled:
- **Navigation (`Spr.Navigation` — 0.70 / 360):** Drives the `FloatingNav` lens indicator (`stretchAxes` stretches X and compresses Y while sliding between tabs), `GlassSegmented` indicator, `ScreenHost`, and `LibraryScreen` page transitions.
- **Buttons (`Spr.ButtonPress` 0.90 / 760 & `Spr.ButtonRelease` 0.46 / 360):** Asymmetric touch-down compression and elastic release bounce with anisotropic X/Y deformation in `Modifier.pressable`.
- **Dropdowns & Popup Menus (`Spr.Dropdown` 0.62 / 410 & `Spr.Popup` 0.64 / 440):** Anchored menus expand with anisotropic vertical/horizontal stretch (`scaleX` / `scaleY` from `TransformOrigin(ox, oy)`) and staggered item settling; `GlassDropdownPill` rotates its chevron with `Spr.Dropdown`.
- **Bottom Sheets (`Spr.BottomSheet` 0.74 / 350):** Velocity-aware drag dismissal plus upward `rubberBand` stretching anchored at `TransformOrigin(0.5f, 1f)`.
- **Mini Player & Now Playing (`Spr.MiniPlayer` 0.74 / 330 & `Spr.Artwork` 0.58 / 310):** Gesture-driven sheet expansion converts spring overshoot (`progress > 1f`) into vertical elastic stretch; artwork scales with play/pause state and stretches/tilts during horizontal swipe-to-skip; seek bar thickens and stretches horizontally with `rubberBand` at the ends.
- **Favorite Heart (`Spr.Favorite` 0.42 / 430):** Expressive two-axis squash-and-stretch heartbeat pop on favorite and crisp spring contraction on unfavorite.
