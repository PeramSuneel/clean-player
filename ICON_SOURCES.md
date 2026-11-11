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

## Reused player and library symbols

The following pre-existing white Android Vector Drawables are retained and reused for player actions and the library's video/audio placeholders. Their icon geometry follows the corresponding Google Material Icons. The source catalogue and upstream repository are linked here; the icon files are kept as Android vector resources and tinted white by the app.

| Android resource | Material icon name |
|---|---|
| pl_ic_arrow_back | arrow_back |
| pl_ic_aspect_ratio | aspect_ratio |
| pl_ic_audiotrack | audiotrack |
| pl_ic_cast | cast |
| pl_ic_closed_caption | closed_caption |
| pl_ic_fast_forward | fast_forward |
| pl_ic_fast_rewind | fast_rewind |
| pl_ic_flip | flip |
| pl_ic_lock | lock |
| pl_ic_lock_open | lock_open |
| pl_ic_lock_outline | lock_outline |
| pl_ic_pause | pause |
| pl_ic_picture_in_picture_alt | picture_in_picture_alt |
| pl_ic_play_arrow | play_arrow |
| pl_ic_playlist_play | playlist_play |
| pl_ic_repeat | repeat |
| pl_ic_repeat_one | repeat_one |
| pl_ic_replay | replay |
| pl_ic_screen_rotation | screen_rotation |
| pl_ic_skip_next | skip_next |
| pl_ic_skip_previous | skip_previous |
| pl_ic_speed | speed |
| pl_ic_volume_off | volume_off |
| pl_ic_volume_up | volume_up |

- Icon catalogue: https://fonts.google.com/icons
- Upstream Android vector resources: https://github.com/google/material-design-icons
- Licence: Apache License 2.0: https://github.com/google/material-design-icons/blob/master/LICENSE

These are inherited assets; this application-wide UI update does not redraw or alter their vector path geometry. The new glass treatment is applied through Android backgrounds, strokes, opacity and tint.
