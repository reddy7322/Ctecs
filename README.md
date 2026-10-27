# Ctecs — Highway Timestamp Camera Pro

Professional road-construction documentation and GPS timestamp camera app with automatic highway chainage detection from imported KMZ/KML alignments.

## Key Features

1. **Automatic Highway Chainage Matching**:
   - Parses `.kmz` (ZIP containing `doc.kml`) and `.kml` XML files.
   - Extracts 50m chainage placemarks (e.g. `130+000` to `209+883`) and road centerline alignment polylines.
   - Orthogonal projection onto alignment segments in local Euclidean metric space.
   - Bracketing marker interpolation for millimeter-accurate chainage along curved alignments.
   - Reports match confidence (`HIGH`, `MEDIUM`, `LOW`, `NO_MATCH`) and perpendicular offset distance.
   - Manual chainage override when GPS or route match is unavailable, with clear distinction.

2. **Exact Photo Overlay Rendering (Matching Reference Spec)**:
   - **Lower-Right Info Block**:
     - Line 1: `26 October 2026 5:32 pm` (Configurable timestamp format).
     - Line 2: `29.9183N 78.1064E` (Configurable precision from 4 to 7 decimals).
     - Line 3: `MH || MM || 196+040 RHS I/L DBM` (Structured project fields: Highway ID, Section, Chainage, Side LHS/RHS, Lane, Activity layer, and custom notes).
     - White, bold, high-contrast typography with black outline/shadow.
   - **Lower-Left Mini-Map Inset**:
     - Real-time offline Canvas vector map rendering active alignment polyline, nearby chainage markers, and live GPS location pin with heading arrow.
   - Resolution-independent permanent overlay burnt directly into saved full-resolution photos.
   - Standard EXIF metadata writing (`TAG_DATETIME`, `TAG_GPS_LATITUDE`, `TAG_GPS_LONGITUDE`, `TAG_GPS_ALTITUDE`).

3. **Field Engineering Controls**:
   - CameraX preview, pinch-to-zoom, tap-to-focus, flash toggle, and front/rear lens switching.
   - Quick one-tap selector chips for Road Side (RHS / LHS / MEDIAN), Lane (I/L, M/L, O/L, Shoulder), Activity presets (DBM, BC, GSB, WMM, Milling, etc.), and Chainage.
   - Project Profiles and customizable overlay templates (Highway Construction, GPS Only, Site Inspection, QC, Minimal).
   - Local persistence with Room database and DataStore preferences (100% offline-capable, no accounts or cloud keys required).

4. **Bundled NH-58 Muzaffarnagar-Haridwar Route**:
   - Pre-packaged with 1,600 chainage markers from `130+000` to `209+883` matching the reference highway section (including `196+040` at `29.9183N, 78.1064E`).
   - One-tap quick load or import custom project KMZ files via Android Storage Access Framework.

## Physical Device Testing Instructions

1. **Building the APK**:
   - Run `gradle assembleDebug` or export the project.
2. **Permissions**:
   - Grant `Camera` and `Fine Location` permissions when prompted.
3. **Route Loading**:
   - Switch to the **Routes** tab.
   - Tap **"Load NH-58 KMZ"** to load the bundled dataset (1,600 markers, `130+000` to `209+883`), or tap **"Import KMZ/KML"** to select your own alignment file.
4. **Camera & GPS Capture**:
   - On the **Camera** tab, verify live GPS acquisition in the top pill.
   - On the emulator, tap the **GPS Sim** button to set coordinates (e.g. `29.9183N, 78.1064E` matching `196+040`).
   - Tap the white shutter button to capture and permanently burn the timestamp and chainage overlay.
5. **Photo Inspection & Sharing**:
   - Tap the thumbnail or open the **Photos** tab.
   - View captured photos with their metadata and tap the Share button to export via Android Share sheet.
