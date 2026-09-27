# PrimeMoney

Fabric-Mod für Minecraft 26.2 / PrimeBlocks. Liest PrimeBlocks' eigene Job-Leiste mit und zeigt
mittig auf dem Bildschirm an, wie viel Geld und Job-XP du verdienst - inklusive Hochrechnung pro
Stunde, Summe der letzten 60 Minuten und der letzten 24 Stunden.

Das Overlay ist dauerhaft sichtbar, solange es aktiviert ist (F9) - es blendet sich nicht von
selbst aus, nur weil gerade nichts reinkommt.

Nur clientseitig, keine Interaktion mit dem Server außer dem Mitlesen dessen, was dir sowieso schon
angezeigt wird.

## Bauen

```
./gradlew build
```

Das fertige Jar liegt danach unter `build/libs/primemoney-26.2.jar`.

## Bedienung

- **F8**: Einstellungen. Ziehen zum Verschieben (in der Mitte loslassen = zentriert, am Rand
  loslassen = an diesen Rand geheftet). Dazu:
  - **Hintergrund / Größe / Layout** - Aussehen des Overlays.
  - **Gesamt** - den Block über alle Jobs zusammen ein-/ausblenden.
  - **Pro Job** - die Zeile je einzelnem Job ein-/ausblenden.
  - **Level-Zeit** - Restzeit bis zum Level-up ein-/ausblenden.
  - **Jobs wählen** - welche der 12 Jobs mitgezählt werden.
  - **Reset Sitzung / Reset 60 Min / Reset 24 Std** - die drei Zähler einzeln auf null, ohne
    Rejoin. Die drei Zeitfenster sind unabhängig: ein Reset der 60 Minuten lässt die 24 Stunden
    unangetastet und umgekehrt.
- **F9**: Overlay komplett an/aus.

Beides ist in Optionen -> Steuerung -> Sonstiges umbelegbar.

## CB1/CB2/CB3 getrennt

"Letzte 60 Min" und "Letzte 24 Std" werden **pro CityBuild** geführt, nicht global. Erkannt wird
das genau wie in PrimeSlots: PrimeBlocks schreibt die aktuelle CB in den Tab-Fußtext ("Du befindest
dich derzeit auf: CityBuild-1 (Farmwelt-5)"), und der bleibt auch **in der Farmwelt** korrekt -
CB1a/b und alle zugehörigen Farm-/Plotwelten fallen dadurch automatisch in denselben Eimer, ganz
ohne Sonderfälle für Weltnamen. Alle paar Sekunden wird nachgeschaut (Tab-Liste, Scoreboard) und bei
einem Wechsel stumm auf den passenden Datensatz umgeschaltet - jede CB landet in ihrer eigenen Datei
(`primemoney-history-cb1.json` usw.), ein Reset auf CB1 lässt CB2 unangetastet.

Bewusst **nicht** aufgeteilt: die "Seit Login"-Zeile und die Hochrechnung. Die beantworten "was habe
ich verdient, seit ich eingeloggt bin" - wer zwischen CB1 und CB2 hin- und herwechselt, soll dabei
nicht seinen laufenden Sitzungs-Betrag verschwinden sehen. Nur die längerfristigen 60-Min-/24-Std-
Summen ergeben pro CB getrennt wirklich Sinn.

Kann der Mod die CB nicht bestimmen (anderer Server, oder die Erkennung greift mal nicht), landet
alles im alten, unbenannten Datensatz (`primemoney-history.json`) - genau wie vor diesem Feature,
ohne dass etwas migriert werden musste.

**Plotwelt-Falle:** Der Footer heißt in der Plotwelt z.B. `CityBuild-1A`, in den zugehörigen
Farmwelten dagegen `CityBuild-1 (Farmwelt-3)` - der Plot-Buchstabe klebt direkt an der Ziffer, ohne
Leerzeichen. Genau da funktioniert `\b` (Wortgrenze) in Regex nicht: eine Ziffer gefolgt von einem
Buchstaben ist keine Grenze, beide zählen als "Wortzeichen". Die ursprüngliche Regex ist an
`CityBuild-1A` deshalb komplett vorbeigelaufen, die Erkennung ist auf eine unzuverlässigere
Zweitquelle zurückgefallen, und Plot- und Farmwelt derselben CB landeten in zwei verschiedenen
Eimern. Der Plot-Buchstabe wird jetzt mitgematcht (aber nicht mit in die Nummer aufgenommen), damit
`CityBuild-1A` genauso wie `CityBuild-1 (Farmwelt-3)` als `cb1` erkannt wird.

## Woher die Zahlen kommen

Quelle ist die Leiste, die PrimeBlocks selbst über der Hotbar einblendet, solange du einen Job
aktiv machst:

```
Farmer » 340,896.16/365,000 🔮 | 8,378.14/10,000 🪙
```

Getrackt werden nur die Zahlen **vor** dem Schrägstrich, und zwar wie weit sie sich seit dem
letzten Update bewegt haben. Die Zahlen dahinter sind nur die XP-Anforderung des aktuellen Levels
und deine eingestellte Auszahlungsschwelle - beide ändern sich und sagen nichts über den Verdienst
aus. Diese Leiste aktualisiert sich mehrmals pro Sekunde, deshalb ist sie als Quelle deutlich
feiner als die Chat-Auszahlung alle paar Sekunden.

Technisch nicht offensichtlich: Minecraft hat **zwei** getrennte Wege, Text an diese Stelle zu
schicken - eine Chat-Nachricht mit gesetztem Overlay-Flag, und ein eigenes Netzwerk-Paket
(`/title actionbar`). PrimeBlocks nutzt den zweiten, der komplett an Fabrics Chat-Events
vorbeigeht. Deshalb greift der Mod per Mixin direkt an der Stelle ab, wo beide Wege zusammenlaufen
(`Hud#setOverlayMessage`).

Beide Zähler springen zwischendurch zurück: Geld bei jeder Auszahlung, XP bei jedem Level-Up.
Statt zu versuchen, jeden einzelnen Sprung zu messen, führt der Mod pro Job eine **laufende
Gesamtsumme** ("was war dieser Zähler jemals wert") und schreibt nur gut, was seit dem letzten Mal
dazugekommen ist. Das macht ihn robust gegen die zwei Dinge, die in der Praxis wirklich schiefgehen:

- **Übersprungene Messwerte** - die Gesamtsumme holt beim nächsten Wert automatisch auf.
- **Geld, das über zwei Kanäle gemeldet wird** - die `JOBS »`-Chatnachricht meldet dieselben
  Münzen, die der Zähler gerade angesammelt hatte. Beide landen in derselben Gesamtsumme, können
  also gar nicht doppelt gezählt werden. (Vorher passierte genau das, wenn die Leiste kurz nicht
  sichtbar war.)

Die Chatnachricht ist trotzdem wichtig: sie nennt den Betrag **exakt**. Auszahlungen überschreiten
die Schwelle nämlich leicht - 10.053,74 statt 10.000 - und dieser Überschuss ist im Zähler allein
nicht sichtbar. Und sie kommt auch für Jobs an, deren Leiste gerade nicht auf dem Bildschirm ist.

Was beim ersten Sehen eines Zählers schon draufsteht, wird bewusst **nicht** gutgeschrieben - das
war Fortschritt von vor dem Login.

Wie groß eine zu Ende gegangene Runde war, wird immer aus der **vorherigen** Messung genommen, nie
aus der, die den Rücksprung meldet. Beim Level-up steht auf derselben Zeile schon die Anforderung
des *nächsten* Levels:

```
… 364997,86/365000   ← Level fast voll
… 5,04/515000        ← Level-up: neuer Zähler, aber auch neues Ziel
```

Nimmt man die 515.000 als Größe der gerade beendeten Runde (die 365.000 groß war), erfindet man
die Differenz von 150.000 XP aus dem Nichts. Richtig ist `(365.000 − 364.997,86) + 5,04 = 7,18`.

## Hochrechnung

Die naive Rechnung (alles Verdiente geteilt durch die vergangene Zeit) wird falsch, sobald du
pausierst: die Leerlaufzeit zieht den Schnitt runter, obwohl dein tatsächliches Farmtempo sich nie
geändert hat. Stattdessen wird **aktive Zeit** gemessen - aber nur *echter* Leerlauf fliegt raus:
Eine Lücke zwischen zwei Einnahmen zählt **voll**, bis zu `ratePauseSeconds` (Standard 120 s).
Alles darüber gilt als Pause und zählt gar nicht.

Der Wert muss großzügig sein, weil Einnahmen nicht gleichmäßig eintrudeln: eine Auszahlung kommt
nur alle 20-40 Sekunden, und wenn sich mehrere Jobs die Leiste teilen, können die Meldungen eines
einzelnen Jobs Minuten auseinanderliegen. Eine frühere Version hat stattdessen *jede* Lücke auf
15 s gedeckelt - dadurch wurde durch ein Drittel der echten Zeit geteilt und die Hochrechnung war
gemessen an echten Log-Daten **2,4x bis 2,7x zu hoch**.

Zusätzlich zählt jede Job-Leiste als **Lebenszeichen**, auch wenn sie gerade keine neuen Einnahmen
zeigt. Der Server blendet sie nur ein, während man tatsächlich arbeitet - so bleibt die Zeitachse
zwischen zwei Auszahlungen durchgehend und stille Strecken werden nicht als Pause missverstanden.

Auch die Zeit seit der letzten Einnahme zählt mit, bis zur Pausengrenze. Sonst bliebe die
Hochrechnung nach dem Aufhören exakt stehen, und eine Zahl von vor zehn Minuten läse sich wie eine
aktuelle. So altert sie sichtbar und bleibt dann stehen.

### Zwei Fenster statt einem

Die Fensterlänge ist ein echter Zielkonflikt: kurz reagiert schnell auf einen Tempowechsel, hat
aber wenige Datenpunkte. Der Schätzfehler einer solchen Summe läuft mit ~1/√n, und so kommen die
Einnahmen wirklich rein:

| Fenster | Leiste sichtbar (~14/min) | nur Auszahlungen (~1,7/min) |
|---------|---------------------------|------------------------------|
| 3 Min   | 42 Messungen → ±15 %      | 5 → ±44 %                    |
| 5 Min   | 70 → ±12 %                | 9 → ±34 %                    |
| 20 Min  | 280 → ±6 %                | 34 → ±17 %                   |
| 60 Min  | 840 → ±3 %                | 102 → ±10 %                  |

Ein Fenster fest zu wählen heißt, mit seiner Schwäche zu leben. Deshalb misst der Mod **beide** und
lässt die Daten entscheiden, welchem er glaubt.

Die eigentliche Frage ist nämlich nicht "welches Fenster", sondern: **hat sich mein Tempo wirklich
geändert?** Jedes Fenster hat einen bekannten Rauschpegel (`Wert / √Messungen`). Genau in diesen
Einheiten wird der Abstand der beiden gemessen:

```
z = |kurz − lang| / √(rausch_kurz² + rausch_lang²)
```

- **z klein** → die beiden weichen nicht stärker ab als Zufall erklärt. Das Tempo ist stabil, und
  das lange Fenster ist einfach die genauere Messung davon.
- **z groß** → der Unterschied ist zu groß für Zufall. Es hat sich echt was geändert, das lange
  Fenster mittelt veraltete Daten mit ein, das kurze hat recht.

Das Gewicht folgt dieser Evidenz stufenlos (`z² / (k + z²)`), damit die Anzeige nicht zwischen zwei
Antworten hin und her springt. Gegen typische Situationen durchgerechnet:

| Situation | Gewicht kurzes Fenster |
|---|---|
| gleichmäßig gefarmt | 2 % → nimmt den präzisen Stundenwert |
| leichtes Rauschen | 20 % → bleibt ruhig |
| auf bessere Farm gewechselt | **94 % → folgt sofort** |
| Tempo halbiert | **95 % → folgt sofort** |

Zusätzlich gilt weiterhin: hat das kurze Fenster weniger als `rateMinEvents` (Standard 30)
Messungen mit Einnahmen, wird es gar nicht erst herangezogen.

Weil die Gewichtung **linear** ist, bleiben die Pro-Job-Zeilen eine exakte Aufteilung der
Gesamtzahl - dieselben Fenster, dieselbe aktive Zeit, dasselbe Gewicht.

Angezeigt wird schlicht `Hochrechnung: 7,9M Münzen/h`. Bewusst **ohne** Fensterlänge im Text: die
Zahl ist immer ein Stundenwert, und ein "(5 Min)" davor liest sich, als ginge es um den Verdienst
in fünf Minuten.

### Selbstkontrolle

Der Mod benotet seine eigenen Vorhersagen. Alle 30 Sekunden legt er die aktuelle Hochrechnung
beiseite; sobald `accuracyHorizonMinutes` (Standard 10) Farm-Zeit vergangen sind, misst er
denselben Zeitraum echt nach und vergleicht. Im Debug-Log steht dann etwa:

```
Treffsicherheit über 12 Prüfungen: Münzen ±7% · XP ±9%
```

Das ist die **gemessene** Trefferquote auf deinem tatsächlichen Farmen, keine Theorie.

Bewusst nur Messung, ohne Rückkopplung in die Schätzung: eine Hochrechnung, die sich selbst anhand
ihres eigenen Fehlers nachjustiert, jagt ihrem Schwanz hinterher - die Korrektur ändert die
Vorhersage, das ändert den nächsten Fehler, das ändert die Korrektur. Tempowechsel werden dort
behandelt, wo sie hingehören: in der Gewichtung oben.

Wer den längeren Durchschnitt zum Vergleich sehen will, schaltet **Schnitt** im F8-Menü ein.
Standardmäßig aus.

Gemittelt wird jeweils über das ganze Fenster, damit einzelne Schwankungen - andere Erträge pro
Block, aktive Booster, Glückstreffer - sich ausgleichen und die Zahl ruhig steht.

Beträge werden kurz dargestellt: `100k`, `6,5M`, `1,2Mrd`.

## Level-up-Restzeit

Die Job-Leiste liefert `aktuelle XP / XP für dieses Level`, also weiß der Mod, wie viel noch fehlt.
Zusammen mit der XP-Hochrechnung **desselben Jobs** ergibt das die Restzeit: fehlen 10k XP und
dieser Job macht 110k XP/h, sind das noch `5,5 min`. Angezeigt wird in Sekunden, Minuten oder
Stunden - je nachdem was passt, und mit nur einer Nachkommastelle, weil die Zahl selbst eine
Schätzung ist.

## Mehrere Jobs gleichzeitig

An einer Spawnerfarm laufen z.B. Jäger und Taucher parallel, und ihre Leisten wechseln sich im
Sekundentakt ab. Deshalb hat **jeder Job seine eigenen Zähler**: eigene Baseline für Geld und XP,
eigene Hochrechnung, eigene Level-Restzeit. Mit einem gemeinsamen Satz Zähler würde jede Zeile wie
ein Job-Wechsel aussehen, jedes Delta verworfen, und die Level-Restzeit ständig vom jeweils anderen
Job überschrieben.

Auch die Auszahlungs-Nachrichten werden pro Job zugeordnet - sonst würde eine Jäger-Auszahlung als
"zählt die Leiste ja schon" verworfen, obwohl die Leiste gerade Taucher zeigt.

Angezeigt wird beides, jeweils einzeln abschaltbar (F8):

- **Gesamt** - alle Jobs zusammen: Sitzung, Hochrechnung, 60 Min, 24 Std.
- **Pro Job** - eine Zeile je Job, der gerade verdient, mit eigenem Verdienst, eigener
  Hochrechnung und eigener Level-Restzeit:

```
Jäger: 312k Münzen · 2,4M/h · 88k XP/h · Lvl in 4,2 min
Taucher: 190k Münzen · 1,1M/h · 40k XP/h · Lvl in 12,8 min
```

Ein Job verschwindet aus der Liste, wenn er `perJobActiveSeconds` (Standard 120) nichts mehr
verdient hat.

Wichtig dabei: die Pro-Job-Hochrechnungen teilen sich **dieselbe aktive Zeit** wie die
Gesamt-Hochrechnung. Würde man jeden Job nur an seinen eigenen aktiven Phasen messen, käme Unsinn
raus - während ein Job auszahlt, ist der andere gerade still, also teilte man durch zu wenig Zeit
und jeder einzelne Job sähe schneller aus als er ist. Mit gemeinsamem Nenner ist jede Job-Zeile
ein **Anteil** an der Gesamtzahl, und die Einzelwerte addieren sich sauber zur Gesamt-Hochrechnung
auf, statt ihr zu widersprechen.

## Fischer

Der Fischer-Job funktioniert anders als alle anderen: Fische sind **Items**. Geld gibt es erst beim
Verkauf an den Fischhändler, und der Job-Level bringt dort nur einen Bonus-Prozentsatz obendrauf.
Entsprechend hat der Job keine Auszahlungsschwelle, und seine Leiste zeigt **nur XP**:

```
Fischer » 12500/50000 🔮
```

Der Geld-Teil im `hudRegex` ist deshalb optional. Weil `Name » Zahl/Zahl` ohne Geld-Teil aber ein
recht generisches Muster ist (`Clan » 5/10 Mitglieder online` würde sonst matchen), gilt dafür eine
Zusatzregel: **ohne** Geld-Teil wird nur akzeptiert, was einem der 12 bekannten Job-Namen
entspricht. **Mit** Geld-Teil ist die Zeile eindeutig genug, da wird auch ein unbekannter Job
gezählt.

Für einen Job ohne Geld-Zähler lässt die Pro-Job-Zeile die Münz-Angaben ganz weg, statt eine Reihe
Nullen zu zeigen, die wie ein Fehler aussähe.

Fischverkäufe werden bewusst **nicht** als Job-Einnahme gezählt - das sind Verkaufstransaktionen,
und sie von normalen Shop-Verkäufen oder Fischbeutel-Upgrades zu trennen wäre Raterei.

## Nichts darf den Client vom Server werfen

Der Mixin, der die Job-Leiste abgreift, hängt in `ClientPacketListener.setActionBarText` - also
mitten in der Paketverarbeitung. Eine Exception dort ist kein verlorener Messwert, sondern ein
"Packet handling error", der den Spieler **disconnected**. Genau das ist einmal passiert: beim
Angeln kam eine Leiste ohne Geld-Teil, und eine übersehene Stelle hat den fehlenden Wert entpackt.

Deshalb fangen jetzt beide Eingänge vom Server - `OverlayMessageBus.publish` und die beiden
Chat-Handler - **jede** Exception ab und lassen sie dort enden. Eine Zahl von einer Statuszeile
abzulesen ist nie einen Disconnect wert. Fehler landen im Log (gedrosselt, damit ein kaputter
Parser es bei zehn Messungen pro Sekunde nicht flutet), das Tracking läuft weiter.

## Max-Level

Ab Level 200 ist ein Job ausgereizt. Der Server meldet dann als XP-Ziel schlicht **0**, während der
XP-Zähler selbst weiter in die Millionen läuft:

```
Jäger » 2331852,51/0 🔮 | 6964,66/10000 🪙
```

Für so einen Job zeigt der Mod **keine** Level-Restzeit an - Geld und XP werden ganz normal
weitergezählt. Außerdem wird ein Rücksprung eines Zählers ohne bekannte Zyklusgröße bewusst *nicht*
als volle Runde verbucht, sondern nur neu angesetzt: bei einem Zählerstand von 2,3 Mio. würde eine
einzige seltsame Messung sonst Millionen XP aus dem Nichts erfinden.

Über **Jobs wählen** (F8) kannst du einzelne Jobs komplett aus der Zählung nehmen. Ein Job, dessen
Name der Mod nicht kennt, wird immer mitgezählt - lieber ein Job zu viel als stillschweigend
verlorenes Geld.

## Persistenz

- `config/primemoney.json` - Einstellungen, wird bei jedem Start neu geschrieben, damit neue
  Optionen nach einem Mod-Update automatisch mit ihren Standardwerten auftauchen. Eigene Änderungen
  an bestehenden Werten bleiben erhalten.
- `config/primemoney-history.json` - Verdienst der letzten 24 Stunden für die 60min-/24h-Anzeige,
  in 30-Sekunden-Blöcken (`historyBucketSeconds`), wird automatisch bereinigt.
- `config/primemoney-debug.log` - Diagnose, wird bei jedem Start neu angelegt. Siehe unten.

## Debug-Log

Mit `debugLogTracking` (Standard `an`) schreibt der Mod mit, was er tatsächlich tut - nicht nur
was am Ende auf dem Bildschirm steht. Das ist der einzige Weg, eine Zahl die *falsch aussieht* von
einer zu unterscheiden, die *falsch ist*.

```
[LIVE] Taucher +676,12 Münzen +11,66 XP  (Anzeige 8378,14/10000 · 340896,16/365000)
[AUSZAHLUNG] Taucher +440,88 Münzen  (gemeldet 10053,74, Rest kam schon über die Leiste)
[STAND] aktive Zeit 182 s · Gesamt 1621415 Münzen/h · 415203 XP/h
         Taucher: 901234 Münzen/h · 250100 XP/h · Sitzung 45600 Münzen
                  · Quelle: Leiste 41200 (312 Messungen) / Auszahlung 4400
```

Die entscheidende Zeile ist **Quelle**. Steht dort bei einem Job "Leiste 0 (0 Messungen)", kommen
seine Einnahmen ausschließlich über Auszahlungen - also in groben 10k-Klumpen alle 20-40 Sekunden
statt fein aufgelöst. Jede Hochrechnung daraus beruht dann auf sehr wenigen Datenpunkten, und man
sieht sofort, dass das Problem an der Datenlage liegt und nicht an der Rechnung.

`[STAND]` kommt alle `debugSnapshotSeconds` (Standard 30) und zeigt die Hochrechnung **mitsamt der
aktiven Zeit**, aus der sie entstanden ist - damit lässt sich jede angezeigte Zahl von Hand
nachrechnen.

Zusätzlich schreibt `debugLogAll` (Standard `aus`) jede Chat-/Overlay-Nachricht roh mit
(Sonderzeichen als `\uXXXX`), falls der Server sein Format ändert und die Muster in
`primemoney.json` nachgezogen werden müssen.
