# ProductModal Debug-Anleitung

## Log-Tag
Alle Debug-Logs nutzen den Tag `ProductModalDebug`.

## Logcat filtern
```bash
adb logcat -s ProductModalDebug
```

Oder in Android Studio: Logcat öffnen → Filter: `ProductModalDebug`

## Erwartete Log-Sequenz bei Hotspot-Klick

| Schritt | Log | Bedeutung |
|--------|-----|-----------|
| 0 | `[0] HeroCarousel composed: productModalHandleState=true` | HeroCarousel hat den State erhalten |
| 1 | `[1] TAP detected offset=(x,y) size=(w,h)` | Touch wurde erkannt |
| 2a | `[2] HOTSPOT HIT: handle=... dist=...` | Treffer innerhalb touchRadius |
| 2b | `[2] Miss: dist=... > touchRadius=...` | Zu weit vom Hotspot entfernt |
| 2c | `[2] No hit found` | Kein Hotspot in Reichweite |
| 3 | `[3] onHotspotClick: handle=... productModalState=true` | Callback aufgerufen |
| 4 | `[4] Setting productModalHandleState.value = handle` | State wird gesetzt |
| 5 | `[5] ShopScreen: productModalHandleState changed to handle` | ShopScreen hat Änderung erhalten |
| 6 | `[6] ShopScreen: rendering modal block, modalHandle=handle` | Modal-Block wird gerendert |
| 7 | `[7] ShopScreen: composing ProductModal with handle=...` | ProductModal wird aufgerufen |
| 8 | `[8] ProductModal COMPOSING: handle=...` | Dialog wird tatsächlich gezeichnet |

## Toast
Bei erfolgreichem Hotspot-Klick erscheint ein Toast: **"Hotspot: [handle] → Modal öffnen"**

- **Toast sichtbar, aber kein Modal?** → Problem liegt bei Schritt 5–8 (State/Recomposition/Dialog)
- **Kein Toast?** → Problem bei Schritt 1–4 (Touch/Hit/Callback)

## Mögliche Fehlerbilder

### Keine Logs bei Klick
- `pointerInput` erhält keinen Touch (z.B. HorizontalPager oder LazyColumn fängt ab)
- Overlay nicht sichtbar/getroffen (z-Index, Größe)

### [1] aber kein [2] HOTSPOT HIT
- `imageSize` noch null (Bild nicht geladen) → Koordinaten falsch
- Touch außerhalb touchRadius (48dp/2 = 24dp um Hotspot-Zentrum)

### [2] HOTSPOT HIT, aber productModalState=false
- `productModalHandleState` wird nicht an HeroCarousel übergeben
- Prüfen: ProductCarouselSection erhält `productModalHandleState` von ShopScreen

### [4] aber kein [5]
- State-Update löst keine Recomposition aus (Snapshot/Compose-Problem)
- ShopScreen liest `productModalHandleState.value` nicht während Composition

### [7] aber kein [8]
- ProductModal wird aufgerufen, aber Dialog-Compose schlägt fehl

### [8] sichtbar, aber Modal nicht sichtbar
- Dialog wird gezeichnet, aber hinter anderem Content (z-Index)
- Dialog-Fenster außerhalb des sichtbaren Bereichs
