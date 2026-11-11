# Clean Player

A minimal, responsive video-player web preview. The project is being developed in stages: web preview first, then a native Android app for device media-library access.

## Current web preview

- Play direct HTTP(S) media URLs when the source allows browser playback.
- Select multiple local video files from the device and play them as a temporary playlist.
- Search/filtering of the selected playlist will be added in the next iteration.
- Previous, 10-second rewind, play/pause, 10-second forward, and next controls.
- Press and hold the central play/pause control while playing for temporary 2x speed.
- Horizontal swipe on the video seeks by 10 seconds; double-tap left/right also seeks.
- Load a subtitle file; WebVTT (.vtt) is the most widely supported browser format.
- Playback speed, fit/fill/stretch, and aspect-ratio settings.

## Important browser limitations

A static website cannot silently scan every folder on an Android device. The web preview uses the browser's file picker and only sees files the user selects. The native Android app will use Android's media APIs and/or Storage Access Framework for user-authorized library and folder access.

A URL is playable only if it points to media the browser can decode and the server permits playback (including appropriate CORS behavior). Arbitrary webpage links, DRM-protected streams, and services requiring special authentication or extraction are not guaranteed to work. Resolution and audio-track selection are source/browser dependent.

## Preview

Enable GitHub Pages for this repository from **Settings → Pages**, selecting **Deploy from a branch**, branch **main**, folder **/(root)**. The preview will then be available at:

https://peramsuneel.github.io/clean-player/

## Development checks

Before calling a build complete, verify the deployed page on desktop and a real mobile viewport. Test direct MP4 playback, unsupported/blocked URL errors, local file selection, playlist navigation, seek controls, press-and-hold 2x, subtitle loading, settings, and safe-area layout. A successful GitHub commit or Pages deployment alone is not a substitute for playback testing.
