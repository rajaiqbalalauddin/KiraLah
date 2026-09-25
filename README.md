# Buyless

An Android app that records your spending automatically. It reads payment notifications from bank and
e-wallet apps (MAE, Bank Islam, Touch 'n Go, plus any app you add), works out the amount and direction,
and keeps everything on your phone.

## Run it

1. Open this folder in Android Studio (Ladybug or newer).
2. Let Gradle sync. It downloads Gradle 8.11.1, AGP 8.7.3 and the libraries on the first run.
3. Plug in your phone with USB debugging on, then press Run.
4. In the app, turn on **Notification access**. If the switch is greyed out (Android 13+ with a sideloaded
   APK), open App info, tap the three-dot menu, then **Allow restricted settings**, and try again.

Run the parser tests with `./gradlew test`.

## How it works

```
Notification posted
   -> PaymentListenerService      (ignores anything not from a watched app, O(1) lookup)
   -> NotificationParser          (amount, in/out, merchant, category; drops OTPs and promos)
   -> trusted app + clear parse?  yes -> saved automatically
                                  no  -> Quick check queue
   -> TransactionRepository       (Room; de-duplication, transfer matching)
   -> Compose UI                  (Home, Activity, Apps, Settings, Quick check)
```

**Bank templates.** Bank Islam (BIMB) alerts are read with exact templates built from real alerts:
QR / FPX payments, QR transfers, card alerts in English and Malay, and DuitNow money received. These
are recorded straight away with the time printed in the alert (card alerts can arrive late). A card
charge in a foreign currency (USD etc.) goes to Quick check, because the ringgit amount is not known yet.
Promotions and updates are ignored. Templates live in `parser/BankProfiles.kt`.

**Notification samples (listen first).** MAE, TNG and other apps have no templates yet. Buyless keeps
the last 300 alerts from watched apps (never OTP / TAC) under Settings > Notification samples. Share the
ones marked "Not recognised" (long numbers are masked) and turn them into a new profile in `BankProfiles`.

**Learning.** Every app starts in learning mode. Its alerts go to Quick check with the parser's guess
pre-filled. After 3 guesses you save without changes, that app is recorded automatically. Change
`LEARNING_THRESHOLD` in `data/model/Models.kt` to adjust this.

**Split tab.** Scan a receipt (camera or gallery), check the items, then hold and drag each item block onto
the person who had it (or onto Everyone to share it). Tapping a block and then tapping people works too.
Service, SST, rounding and discounts are shared by how much each person ordered, and totals always add up
to the bill to the sen. The Totals screen shows a full-screen card per friend with their amount and your
saved payment QR (MAE, TNG, DuitNow...), with "Next person" to pass the phone round the table.
OCR runs on the phone with ML Kit; the photo is never uploaded.

**Transfers between your own apps.** Money going out of one watched app and into another with the same
amount within 10 minutes is paired and left out of totals. You can undo this on any transaction.

## Performance choices

| Where | What | Why |
| --- | --- | --- |
| Listener | Watched apps held in an in-memory HashMap | Most notifications are ignored after one lookup |
| Parser | Regexes compiled once | Compiling per notification is the usual hidden cost |
| Database | SUM / GROUP BY in SQL, indexes on time, app, dedup key | Only the totals leave the database |
| Database | Unique dedup key, `INSERT OR IGNORE` | Re-posted notifications never double count |
| Database | WAL journal mode | Listener writes do not block UI reads |
| Icons | LruCache of decoded bitmaps, capped at 1/32 of heap | No decoding twice while scrolling |
| App scan | PackageManager scan cached per process | The Apps screen opens instantly the second time |
| Money | Stored as `Long` sen | Exact totals, no floating-point drift |
| UI | `@Immutable` models, list keys, formatting in ViewModels | Compose skips unchanged rows |
| UI | `WhileSubscribed(5s)` + `collectAsStateWithLifecycle` | Queries stop when the app is in the background |
| Split | One ML Kit recognizer reused per process | The model loads once, later scans are faster |
| Split | Drag bounds in plain maps, only pointer is observable state | Dragging recomposes the ghost block, not the board |
| Split | QR images downscaled to 1200 px PNG on import, bitmaps cached | Instant QR switching, small memory use |
| Release | R8 minify and resource shrinking | Small APK, unused icons stripped |

## Tuning the parser

`parser/NotificationParser.kt` uses keywords, not exact templates, so wording changes do not break it
silently. When an alert is parsed wrongly, copy its text from Quick check into
`NotificationParserTest.kt` as a new test, adjust the keyword lists, and run the tests.

## Project layout

```
app/src/main/java/com/buyless/app/
  AppContainer.kt          manual dependency injection + Application class
  MainActivity.kt
  data/db/                 Room entities, DAOs, database
  data/repo/               TransactionRepository, AppsRepository (installed apps + icon cache)
  data/model/              enums and TransactionDraft
  parser/                  NotificationParser
  service/                 PaymentListenerService
  split/                   ReceiptParser, SplitMath (pure Kotlin, unit tested), ReceiptScanner (ML Kit)
  ui/                      theme, shared components, one package per screen, navigation
  util/                    Money and date formatting, known Malaysian apps, system settings shortcuts
```

## Not done yet

- Custom fonts. The design uses Bricolage Grotesque and Plus Jakarta Sans. Add the `.ttf` files to
  `res/font` and swap the two `FontFamily` values in `ui/theme/Theme.kt`.
- Dark mode, budgets, charts, and a home screen widget (listed as open in the design brief).
- Backup and export. Data is local only, and `allowBackup` is off on purpose.
