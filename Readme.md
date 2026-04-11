## Mantinventory (Android)

Mantinventory is an Android inventory management app organized around **physical boxes**.

Each box gets a generated **QR label**. When a label is scanned (or deep link opened), the app opens directly to that box and shows what is inside.

### Core features implemented

- Create boxes with:
  - Name
  - Location
  - Description
- Auto-generate unique box label code (`MANT-BOX-...`)
- Generate and store QR label image (PNG) for each box
- Deep-link format for labels: `mantinventory://box/open?code=<BOX_CODE>`
- Scan box labels and item barcodes/QR codes with CameraX + ML Kit
- View box details and full item list
- Add items to boxes with:
  - Name
  - Description
  - Quantity
  - Barcode/QR value
  - Minimum stock threshold
- Search items by:
  - Item name
  - Description
  - Barcode/QR value
- Inventory dashboard:
  - Total boxes
  - Total items
  - Low-stock item count
- Share/print box label image via Android share sheet

### Architecture

- **Kotlin + Jetpack Compose** UI
- **Room** local database
- **MVVM** with `AppViewModel`
- **CameraX + ML Kit** for scanning
- **ZXing** for QR generation

### Project structure

- `app/src/main/java/com/manticore/mantinventory/data`
  - Room entities, DAO, repository, label generator
- `app/src/main/java/com/manticore/mantinventory/ui`
  - ViewModel and state
- `app/src/main/java/com/manticore/mantinventory/ui/screens`
  - Compose screens and navigation
- `app/src/main/java/com/manticore/mantinventory/MainActivity.kt`
  - App entry point + deep link handling

### Build notes

- Requires Android SDK configured in your environment (`ANDROID_HOME` or `local.properties`).
- Build command:
  - `./gradlew :app:assembleDebug`

### Product plan (next useful features)

1. **Bulk import/export**
   - CSV import for boxes/items
   - Backup/restore JSON export
2. **Cloud sync + multi-device**
   - Sync data to backend (Firebase or custom API)
   - User accounts and permissions
3. **Advanced item operations**
   - Move item between boxes
   - Quantity adjust history / audit log
4. **Label tooling**
   - Printable templates (A4/Letter label sheets)
   - Batch label generation
5. **Smart alerts**
   - Low stock notifications
   - “Missing expected item” box checklist
6. **Media attachments**
   - Add item photos/manuals/receipts
7. **Offline-first conflict resolution**
   - Queue edits and resolve on reconnect
8. **Analytics**
   - Most accessed boxes
   - Consumption trends and reorder forecasts
