# Push2find

אפליקציה לאנדרואיד ול-iOS: לחיצה על הכפתור בצמיד ← הטלפון מצלצל בקול רם, גם במצב שקט, עם מסך נעול וכשהאפליקציה סגורה.

```
Push2find/
├── PROTOCOL.md      ← הצעת פרוטוקול BLE לצוות החומרה (UUIDs, פורמט הודעות, פרמטרים)
├── android/         ← Kotlin + Jetpack Compose (Android 8.0 ומעלה)
└── ios/             ← Swift + SwiftUI (iOS 16 ומעלה)
```

שתי האפליקציות native, כי כל דרישות הליבה (רקע, מסך נעול, עקיפת מצב שקט, חיבור מחדש) תלויות ב-API ספציפי של כל מערכת הפעלה.

---

## מה מומש מול האפיון

| דרישה | אנדרואיד | iOS |
|---|---|---|
| BLE מול הצמיד | `WristbandLink.kt` | `BLEManager.swift` |
| צלצול אוטומטי בלחיצה | Foreground Service מאזין להתראות GATT | מצב רקע `bluetooth-central` |
| צלצול במצב שקט | השמעה בערוץ ה-**Alarm** + העלאת עוצמתו למקסימום, החזרה לערך המקורי בסיום | קטגוריית אודיו `.playback`, שמתעלמת ממתג השקט |
| עבודה ברקע / מסך נעול | Foreground Service מסוג `connectedDevice` + מסך "STOP" מעל מסך הנעילה | `bluetooth-central` + `audio` + State Restoration |
| חיבור מחדש אוטומטי | `autoConnect=true`: הבקר ממתין לצמיד ברמת החומרה. הפעלה מחדש גם אחרי אתחול טלפון ואחרי כיבוי והדלקה של Bluetooth | `connect()` ממתין ללא הגבלת זמן, ו-iOS מתחבר מעצמו כשהצמיד חוזר לטווח |
| זמן תגובה | פחות מחצי שנייה כשיש חיבור (תלוי ב-connection interval, ראו PROTOCOL.md) | כנ"ל |
| צריכת סוללה | סריקה רק במסך הצימוד; בשאר הזמן חיבור פסיבי בלבד, בלי polling | כנ"ל |
| ממשק | סטטוס חיבור, סוללת צמיד, צימוד, צלצול, משך, רטט, בדיקה ועצירה | זהה |
| בלי חשבונות או ענן | ✓ | ✓ |

לחיצה על הצמיד בזמן שהטלפון מצלצל עוצרת את הצלצול. אפשר לעצור גם מההתראה או מכפתור STOP על מסך הנעילה.

---

## בנייה – אנדרואיד

1. פותחים את התיקייה `android/` ב-Android Studio (Ladybug ומעלה). הסטודיו יוריד Gradle והתלויות.
2. מריצים על **מכשיר אמיתי**. ל-Emulator אין BLE.
3. בהפעלה הראשונה מאשרים "מכשירים בקרבת מקום" והתראות. במסך הבית יופיעו כרטיסים לתיקון הגדרות שעלולות לשבור את פעולת הרקע: אופטימיזציית סוללה, התראות במסך מלא ו-Bluetooth כבוי.

> בסביבה שבה כתבתי את הקוד אין Android SDK ואין Xcode, ולכן לא קומפלתי. הקוד נבדק בקריאה, אבל צפוי מחזור קצר של תיקוני קומפילציה בפתיחה הראשונה.

## בנייה – iOS

```bash
cd ios
brew install xcodegen      # פעם אחת
xcodegen generate          # יוצר Push2find.xcodeproj מתוך project.yml
open Push2find.xcodeproj
```
ב-Xcode: Signing & Capabilities ← בוחרים Team, ומריצים על אייפון אמיתי (בסימולטור אין Bluetooth).

---

## בדיקה בלי הצמיד

**הדרך הקלה: המחשב כצמיד.** בתיקייה `simulator/` יש סקריפט שהופך את המחשב לצמיד עם כפתור על המסך (`pip install bless && python wristband_sim.py`). הפרטים ב-`simulator/README.md`.

**חלופה: טלפון שני.**

אפשר לדמות את הצמיד עם טלפון אנדרואיד שני ואפליקציית **nRF Connect for Mobile**:
1. Configure GATT server ← מוסיפים Service עם ה-UUID `7A1E0001-…`, ובתוכו Characteristic `7A1E0002-…` עם Notify, ו-Battery Service `180F`.
2. Advertiser ← יוצרים פרסום עם השם `P2F-TEST` וה-Service UUID.
3. בטלפון הנבדק מצמידים ל-`P2F-TEST`, ואז שולחים Notify עם הערך `01 01` (ובכל לחיצה מעלים את הבייט השני).

אם אב-טיפוס החומרה עוד לא מפרסם את ה-UUID, יש במסך הצימוד מתג **"Show all Bluetooth devices"**.

---

## מגבלות מערכת הפעלה שחשוב להכיר

**iOS**
- **עוצמה:** iOS לא מאפשר לאפליקציה לשנות את עוצמת המדיה. הצלצול עוקף את מתג השקט, אבל אם עוצמת המדיה על 0 הוא לא יישמע. הפתרון המלא הוא **Critical Alerts**: התראה שמנגנת בעוצמה מלאה גם במצב שקט וב-Do Not Disturb. זה דורש [אישור מ-Apple](https://developer.apple.com/contact/request/notifications-critical-alerts-entitlement/). הקוד כבר מוכן לזה: אחרי האישור מבטלים את ההערה ב-`Push2find.entitlements` ומגדירים `AppConfig.criticalAlertsEnabled = true`. **מומלץ להגיש בקשה כבר עכשיו.**
- **סגירה ידנית:** אם המשתמש מחליק את האפליקציה מחוץ ל-App Switcher, iOS מפסיק להעיר אותה עד הפתיחה הבאה. זו מדיניות Apple, ואין לה עקיפה. במסך הבית יש הסבר על כך.
- **אוזניות:** אם מחוברות אוזניות Bluetooth, הצליל יוצא דרכן.
- **App Review:** מצב הרקע `audio` נבדק ב-Review. כדאי לנמק אותו בשליחה ("alarm playback triggered by a paired BLE accessory").

**אנדרואיד**
- יצרנים מסוימים (Xiaomi, Huawei, OnePlus ועוד) סוגרים שירותי רקע באגרסיביות. האישור "Allow background use" מטפל ברוב המקרים. במכשירי Xiaomi צריך לפעמים להפעיל גם Autostart ידנית.
- הצלצול נשמע ב-Do Not Disturb כל עוד ההגדרה "Alarms" מאושרת ב-DND, וזו ברירת המחדל.
- ב-Android 14 ומעלה מסך ה-STOP על מסך הנעילה דורש אישור "Full-screen alerts". יש לכך כרטיס במסך הבית.

---

## להשלמה מול צוות החומרה
ראו סעיף 7 ב-`PROTOCOL.md`: UUIDs סופיים, נורית או רטט לפקודת Identify, מדיניות דיווח סוללה, וסוגי לחיצה נתמכים. כל השינויים מרוכזים בקובץ אחד בכל פלטפורמה (`P2FProtocol.kt` / `P2FProtocol.swift`).
