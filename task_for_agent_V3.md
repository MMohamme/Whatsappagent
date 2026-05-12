# Task: WA Agent v2.1 – Bugfixes, UI/UX-Erweiterungen & Stabilisierung

**Ausgangslage**  
Die App basiert auf `project_specification_deep_dive.md` und `BACKEND_INTEGRATION_GUIDE.md`.  
Die letzte Version (v2.1) wurde mit neuen Features ausgestattet, jedoch bestehen noch folgende **kritische Probleme und offene Wünsche**, die dieser Auftrag vollständig beheben soll.

**Wichtig**  
- Es darf **erst mit der Implementierung begonnen werden, wenn ich diesen Plan explizit genehmigt habe**.  
- Nach **jedem Schritt** sind die gesamte Codebasis (Worker, UI, Data) sowie die Schnittstellen zum Backend auf Konflikte zu prüfen.  
- Jede Datenbankänderung ist in `databank.md` detailliert zu dokumentieren (inkl. Migrationscode).  
- Erledigte Arbeiten werden fortlaufend in `app.md` protokolliert.

---

## 1. Vorrangige Fehlerbehebung (Bugs)

### 1.1 Queue-Inkonsistenz: Dashboard „10 pending“ vs. Queue leer

**Symptom**  
- Dashboard zeigt 10 ausstehende Nachrichten an,  
- die Queue-Ansicht (Screen) ist jedoch leer und die Dashboard-Statistik zeigt 0.

**Ursachenanalyse & Fix (erzwingen)**  
- Überprüfe die Datenquellen:  
  - `DashboardViewModel` – woher kommen die „10 pending“? (Vermutlich unsaubere Aggregation.)  
  - `QueueViewModel` – welche DAO-Methode wird verwendet? Filtert sie auf `status = REPLAY_PENDING` o.Ä. und zeigt nur `isSynced = false`?  
- Korrigiere die Abfragen so, dass Dashboard und Queue dieselbe Datenbasis nutzen.  
- Stelle sicher, dass `SyncWorker` bei Fehlern den Status korrekt auf `REPLY_FAILED` oder `SYNC_FAILED` setzt und `isSynced` auf `false` bleibt, damit Einträge in der Queue sichtbar bleiben.  
- **Dokumentiere die gefundene Ursache und die vorgenommenen Änderungen** sowohl in `app.md` als auch in den Code-Kommentaren.

### 1.2 Kontakte: Eigenschaften nicht bearbeitbar

**Symptom**  
- Bestehende Kontakte (auch solche, die automatisch beim ersten Schreiben mit Default‑Einstellungen angelegt wurden) können im Kontakt-Screen nur angezeigt und gelöscht, aber **nicht editiert** werden.

**Anforderung**  
- Implementiere eine Bearbeiten‑Funktion:  
  - Tipp auf Kontakt öffnet Detailansicht mit allen Feldern (`phoneNumber`, `contactName`, `category`, `isActive`, Delays).  
  - Felder müssen editierbar sein (Validierung wie bei Neuanlage).  
  - Änderungen werden in `ContactSettingsDao` gespeichert.  
  - Auch das Aktivieren/Deaktivieren (Agent‑Schalter) muss funktionieren.  
- Besonders wichtig: **Standard‑Eigenschaften von automatisch angelegten Kontakten** müssen nachträglich korrigiert werden können.

### 1.3 Event‑Formular: Zeitfeld verlangt manuelle Eingabe statt Kalender

**Symptom**  
- Obwohl in der Spezifikation ein Kalender‑Widget gefordert war, enthält das Event‑Formular ein Textfeld für das Datum.

**Fix**  
- Ersetze das Textfeld durch einen **Material DatePicker + TimePicker**.  
- Die ausgewählte Zeit wird als `Long` (Epoch Millis) an das Backend übergeben.  
- Kein manuelles Editieren des Datum‑Strings mehr zulassen.

### 1.4 Chat Screen nicht skalierbar

**Problem**  
- Die Nachrichten-Historie passt sich nicht an unterschiedliche Bildschirmgrößen an / die Darstellung ist fehlerhaft.

**Fix**  
- Überarbeite `ChatScreenNew` (und ggf. `ChatViewModel`) für responsives Layout:  
  - Verwendung von `LazyColumn` mit korrektem `key`.  
  - Nachrichten‑Bubbles mit `Modifier.widthIn(max = ...)`, konsistenter Abstand.  
  - Korrektes Scrollen zum neuesten Eintrag bei neuen Nachrichten.

### 1.5 Android System‑Navigation (Zurück) funktioniert nicht

**Problem**  
- Während der App‑Bedienung reagiert die systemeigene Zurück‑Geste / der Zurück‑Button nicht zuverlässig.

**Fix**  
- Überprüfe das `BackHandler` in allen `@Composable` Screens.  
- Stelle sicher, dass die Navigation (Enum‑basiert mit `AnimatedContent`) konsistent mit dem `BackStack` arbeitet und `MainActivity.onBackPressed()` richtig delegiert.  
- Testen auf verschiedenen Android‑Versionen (ab API 24).

---

## 2. Feature‑Erweiterungen und Verbesserungen

### 2.1 Automatische Default‑Eigenschaften für unbekannte Kontakte

- Wenn ein neuer Absender erstmals erkannt wird und noch kein Eintrag in `ContactSettingsDao` existiert, **muss sofort ein Datensatz mit Standardwerten** (`isActive = true`, `category = UNKNOWN`, Standard‑Delays) angelegt werden.  
- Diese Logik ist in der `WhatsAppListener` (Capture Pipeline) zu verankern, **bevor** eine Nachricht gespeichert wird.  
- Die UI muss diese Kontakte korrekt anzeigen (z. B. als „Neu – bitte konfigurieren“).

### 2.2 UI/UX für Kontakt‑Formular und Event‑Formular verbessern

- **Kontakt‑Formular** (neu & bearbeiten):  
  - Kategorie‑Auswahl als Chips oder Dropdown, nicht nur Text.  
  - Telefonnummer‑Feld mit Flaggen‑Länderauswahl (optional).  
  - Speichern‑Button deaktiviert, solange Pflichtfelder leer sind.  
  - Erfolgsmeldung als Snackbar.  
- **Event‑Formular**:  
  - Aufgeräumtes Layout, Modus‑Switcher (Einzelperson/Kategorie) prominenter.  
  - Vorschau der ausgewählten Kontakte bei Kategorie‑Modus.  
  - Mit dem neuen DatePicker (siehe 1.3) kombinieren.

### 2.3 Zeitlich begrenzte Notizen um TimePicker erweitern

- Das bereits existierende Feature für `expiresAtMillis` soll **nicht nur ein Datum, sondern auch eine Uhrzeit** unterstützen.  
- DatePicker **und** TimePicker nacheinander öffnen, Wert zusammenführen.  
- UI‑Darstellung der ablaufenden Notizen mit verbleibender Zeit („in 3 Stunden“).

### 2.4 Event‑Broadcast‑Logik sicherstellen und dokumentieren

- Der Kategorie‑Modus muss für **jeden aktiven Kontakt** der gewählten Kategorie ein separates Event über `POST /contacts/{contact_name}/events` anlegen.  
- Die App muss mit `429 Too Many Requests` umgehen können und ggf. eine kurze Verzögerung einbauen.  
- Dokumentiere in `app.md`, wie der `SyncWorker` diese Events später abholt und die Reply‑Nachrichten versendet.

---

## 3. Qualitätssicherung und Dokumentation (für jeden Schritt)

**Nach jedem Einzelschritt (Bugfix oder Feature) sind folgende Punkte verpflichtend:**

1. **Konflikt‑Check**  
   - Kompilierung sicherstellen.  
   - Prüfen, ob Änderungen Auswirkungen auf `NotificationListenerService`, `SyncWorker` oder `RetryReplyWorker` haben.  
   - UI‑Tests (mindestens manuell) für Dark/Light/AMOLED durchführen.  
   - Schnittstellen zum Backend (Retrofit‑Models) auf Bruchstellen prüfen und ggf. in `ApiModels.kt` anpassen.

2. **Datenbank‑Prüfung**  
   - Wird eine Schema‑Änderung benötigt?  
     - Falls ja: Migration erstellen, in `databank.md` dokumentieren (SQL‑Code / Room‑Migration‑Klasse).  
     - Falls nein: expliziten Vermerk „Keine DB‑Änderung nötig“ in den Log.

3. **Protokollierung in `app.md`**  
   - Was wurde geändert?  
   - Welche Dateien sind betroffen?  
   - Warum wurde die Änderung vorgenommen?  
   - Bei Bugfixes: **Ursache dokumentieren**.

---

## 4. Abnahme & Freigabe

- Wenn alle Schritte umgesetzt und dokumentiert sind, wirst du den Code in einem Pull-Request / Patch bereitstellen.  
- Ich prüfe die Funktionalität, die Dokumentation und insbesondere die Queue-Konsistenz sowie die Kontaktbearbeitung.  
- Erst nach meiner Freigabe gilt der Auftrag als erledigt.

---

**Erinnerung**  
Beginnt **nicht** mit der Implementierung, bevor ich diesen Plan explizit bestätigt habe.