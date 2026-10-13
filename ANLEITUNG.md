# PrimeMoney

Zeigt dir live an, wie viel Geld und Job-XP du gerade auf PrimeBlocks verdienst - inklusive einer
Hochrechnung, was du bei diesem Tempo in einer Stunde machen würdest.

## Installation

Jar-Datei in deinen `mods`-Ordner legen (Fabric Loader + Fabric API für Minecraft 26.3 werden
vorausgesetzt). Danach einfach normal auf PrimeBlocks spielen - der Mod meldet sich von selbst,
sobald du einen Job aktiv machst.

## Bedienung

- **F9** - Overlay komplett an oder aus.
- **F8** - Einstellungen öffnen. Dort kannst du:
  - das Overlay mit der Maus an eine andere Stelle ziehen,
  - Hintergrund und Größe anpassen,
  - einzelne Anzeigen ein-/ausschalten (**Gesamt**, **Pro Job**, **Level-Zeit**, **Schnitt**),
  - unter **Jobs wählen** festlegen, welche der 12 Jobs überhaupt mitgezählt werden,
  - über **Reset Sitzung / Reset 60 Min / Reset 24 Std** einzelne Zähler von Hand auf null setzen.

Beide Tasten lassen sich in den Minecraft-Optionen unter Steuerung umbelegen.

## Was die Zahlen bedeuten

- **Seit Login** - alles, was du seit dem Betreten des Servers verdient hast.
- **Hochrechnung** - was du bei deinem aktuellen Tempo in einer Stunde machen würdest. Passt sich
  laufend an, wenn du schneller oder langsamer wirst (z.B. andere Farm, Booster).
- **Letzte 60 Min / Letzte 24 Std** - was du in diesem Zeitraum wirklich verdient hast. Wird
  getrennt für CB1, CB2 und CB3 geführt - ein Wechsel der CB mischt die Zahlen nicht zusammen.
- **Zeile pro Job** (z.B. "Jäger: 300k Münzen · 1,2M/h · 250k XP/h") - falls du mehrere Jobs
  gleichzeitig laufen hast (z.B. an einer Spawnerfarm), siehst du jeden einzeln.
- **"Lvl in 5,2 min"** - Schätzung, wie lange es bis zum nächsten Level-up dauert.

## Gut zu wissen

- Das Overlay bleibt dauerhaft sichtbar, solange es aktiv ist - es blendet sich nicht von selbst
  aus, nur weil gerade keine Einnahme reinkommt. Nur **F9** schaltet es ab.
- Beim **Fischer**-Job wird nur XP gezählt, kein Geld - Fische sind Items, die du erst verkaufst,
  dafür gibt es keine laufende Auszahlung wie bei den anderen Jobs.
- Ab Level 200 (Maximum) zeigt der Mod keine Level-Restzeit mehr an - Geld und XP laufen aber ganz
  normal weiter.
- Der Mod ist speziell auf PrimeBlocks zugeschnitten und liest nur mit, was dir das Spiel ohnehin
  schon anzeigt - er verändert nichts am Spiel selbst.
