# Liquid Glass icon assets

The new primary play and pause symbols are official Google Material Icons Rounded Android Vector Drawables, used as supplied and tinted white.

- Repository: https://github.com/google/material-design-icons
- Style: Material Icons Rounded
- Licence: Apache License 2.0
- Licence: https://github.com/google/material-design-icons/blob/master/LICENSE

| Resource | Source |
|---|---|
| `lg_play_arrow` | https://raw.githubusercontent.com/google/material-design-icons/master/android/av/play_arrow/materialiconsround/black/res/drawable/round_play_arrow_24.xml |
| `lg_pause` | https://raw.githubusercontent.com/google/material-design-icons/master/android/av/pause/materialiconsround/black/res/drawable/round_pause_24.xml |

The app logo and launcher foreground reuse the official Google `play_arrow` vector. Existing baseline player symbols are retained without drawing or modifying their icon geometry.


## Launcher glass treatment

The launcher and splash artwork reuse the same white Google Material play-arrow vector mark; the translucent circular glass surface is an Android shape drawable (ic_launcher_glass_surface.xml), not a separately invented symbol. The monochrome adaptive-icon layer remains a plain white play mark so Android can apply its themed-icon tint.
