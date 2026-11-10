# Fokus Wächter

Eine kleine Android-App, die **YouTube Shorts** und **Snapchat Spotlight** sperrt, direkt in den echten Apps.

- **YouTube:** Sobald du auf Shorts landest, wechselt der Wächter zurück auf «Home». Normale Videos, Suche und Abos bleiben.
- **Snapchat:** Sobald du auf Spotlight landest, wechselt der Wächter in den Chat. Chats und Stories von Freunden bleiben.
- **Optional:** die ganze Stories-Seite von Snapchat (mit Discover) sperren. Stories von Freunden siehst du dann weiter in der Chat-Liste (Profilbild mit Ring antippen).

**Download-Seite:** https://zubi02.github.io/fokus/

**Direkter Download der App:** [fokus-waechter.apk](https://zubi02.github.io/fokus/fokus-waechter.apk)

Nur für Android (ab Android 10). Für das iPhone gibt es keine Version, siehe unten.

## Installieren

### Weg 1: direkt auf dem Handy

1. Öffne https://zubi02.github.io/fokus/ in Chrome auf dem Handy und tippe auf «App herunterladen».
2. Öffne die Datei und tippe auf «Installieren». Android fragt, ob der Browser Apps installieren darf: erlauben.
3. Weiter bei «Einschalten».

Auf manchen Handys blockiert Google Play Protect diesen Weg mit der Meldung «App blocked to protect your device» und bietet nur «OK» an. Dann hilft Weg 2.

### Weg 2: über einen Computer (falls Play Protect blockiert)

Du brauchst einen Computer und entweder ein USB-Datenkabel oder dasselbe WLAN für Handy und Computer.

1. Lade auf dem Computer die [Android Platform-Tools](https://developer.android.com/tools/releases/platform-tools) von Google herunter und entpacke sie.
2. Lade [fokus-waechter.apk](https://zubi02.github.io/fokus/fokus-waechter.apk) auf den Computer.
3. Schalte auf dem Handy die Entwickleroptionen frei: Einstellungen → «Über das Telefon» → siebenmal auf «Build-Nummer» tippen.
4. Verbinde das Handy:
   - **Mit Kabel:** Entwickleroptionen → «USB-Debugging» einschalten, Handy anschliessen, die Abfrage auf dem Handy zulassen.
   - **Ohne Kabel:** Entwickleroptionen → «Debugging über WLAN» einschalten → «Gerät über einen Kopplungscode koppeln». Dann auf dem Computer im Ordner der Platform-Tools:
     ```
     adb pair IP:PORT CODE
     adb connect IP:PORT
     ```
     Für `adb pair` gelten Adresse und Code aus dem Kopplungsfenster, für `adb connect` die Adresse unter «IP-Adresse und Port» auf der Seite dahinter.
5. Installiere die App vom Computer aus:
   ```
   adb install fokus-waechter.apk
   ```
6. Schalte das Debugging danach wieder aus.

### Einschalten

1. Öffne «Fokus Wächter» und tippe auf «Bedienungshilfe einschalten».
2. Tippe in der Liste auf «Fokus Wächter» und schalte ihn ein.
3. Falls der Schalter grau ist: Einstellungen → Apps → Fokus Wächter → oben rechts ⋮ → «Eingeschränkte Einstellungen zulassen», dann nochmals Schritt 1.

In der App legst du mit drei Schaltern fest, was gesperrt wird.

## Wie es funktioniert

Der Wächter ist ein Android-Bedienungshilfe-Dienst. Er sieht, welche Seite in YouTube, Snapchat und Chrome gerade offen ist, und tippt bei einer gesperrten Seite für dich auf «Home» bzw. «Chat». Er speichert und sendet keine Daten und braucht keine Internet-Berechtigung.

Getestet auf einem OnePlus mit Android 17 und den App-Versionen vom November 2026. YouTube und Snapchat können ihre Apps jederzeit umbauen; dann muss die Erkennung angepasst werden. Dafür gibt es in der App den Knopf «Diagnose», der die technischen Namen der sichtbaren Bildschirmteile auflistet (keine Texte oder Nachrichten).

## Kein iPhone

Auf dem iPhone ist so eine App nicht möglich. iOS erlaubt keiner App, in eine andere App hineinzusehen oder dort etwas anzutippen. Die Bildschirmzeit von Apple kann nur ganze Apps oder Websites sperren oder zeitlich begrenzen, nicht einzelne Bereiche wie Shorts oder Spotlight.

## Quellcode

Der Quellcode liegt im Ordner [`quelle`](quelle). Gebaut wird ohne Gradle mit `build.sh` (JDK 17, Android-SDK-Plattform 34 und Build-Tools 34.0.0).
