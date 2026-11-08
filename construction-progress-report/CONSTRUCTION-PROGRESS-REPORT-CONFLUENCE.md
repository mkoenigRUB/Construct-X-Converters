# Construction Progress Report Ontologie (Draft)

**Namespace:** `https://www.construct-x.org/ontology/construction-progress-report#`  
**Version:** `0.1.0`  
**Prefix:** `conx-cpr`

Die Ontologie beschreibt eine punktuelle Baufortschrittsmeldung für einen Bauvorgang. Sie speichert den zum Erfassungszeitpunkt gemeldeten Status und – soweit vorhanden – den Fertigstellungsgrad sowie tatsächliche und prognostizierte Termine.

Die Verknüpfung zu `Activity` ist bewusst **nicht Bestandteil der Core-Ontologie**, sondern wird entsprechend der Construct-X-Linking-Rule in `schedule-progress-report-link` definiert.

## Klasse

| Klasse | Deutsch | Fachliche Bedeutung |
|---|---|---|
| `ConstructionProgressReport` | Baufortschrittsmeldung | Zeitpunktbezogene Meldung des Fortschrittsstands eines Bauvorgangs. |

## Eigenschaften von ConstructionProgressReport

| Property | Wertebereich | Kardinalität | Fachliche Bedeutung |
|---|---|---:|---|
| `identifier` | `xsd:string` | **1..1** | Verpflichtende fachliche Kennung der Meldung. |
| `recordedAt` | `xsd:dateTime` | **1..1** | Erfassungsdatum bzw. Erfassungszeitpunkt der Meldung. |
| `activityStatus` | `skos:Concept` | **1..1** | Status des Vorgangs zum Erfassungszeitpunkt. |
| `completionPercentage` | `xsd:decimal` | 0..1 | Optionaler Fertigstellungsgrad in Prozent von 0 bis 100. |
| `actualStart` | `xsd:dateTime` | 0..1 | Tatsächlicher Anfangstermin des Vorgangs. |
| `actualFinish` | `xsd:dateTime` | 0..1 | Tatsächlicher Endtermin des Vorgangs, falls beendet. |
| `forecastFinish` | `xsd:dateTime` | 0..1 | Prognostizierter Endtermin, solange noch kein tatsächlicher Endtermin vorliegt. |

Die geplanten Termine `plannedStart` und `plannedFinish` werden **nicht dupliziert**. Sie verbleiben in der `Construction Schedule Ontology` bei `Activity`. Die Baufortschrittsmeldung dokumentiert den Ist-/Prognosestand zu einem bestimmten Erfassungszeitpunkt.

## Status-Vokabular

`activityStatus` hat im Core den Wertebereich `skos:Concept`. Über ein optionales SHACL-Profil kann das bestehende Construct-X Activity Status Vocabulary verwendet werden:

- `Planned`
- `InProgress`
- `Completed`
- `Cancelled`

Dadurch kann dieselbe Statussemantik sowohl für den aktuellen Status einer `Activity` als auch für den historischen Status einer Baufortschrittsmeldung verwendet werden.

## Core-SHACL-Regeln

- `identifier`, `recordedAt` und `activityStatus` sind verpflichtend.
- `completionPercentage` liegt, falls vorhanden, zwischen 0 und 100.
- `actualFinish` setzt `actualStart` voraus.
- `actualStart <= actualFinish`.
- `actualStart` und `actualFinish` dürfen nicht nach `recordedAt` liegen.
- `forecastFinish` darf nicht gleichzeitig mit `actualFinish` vorhanden sein.
- `forecastFinish` darf nicht vor `actualStart` liegen.

## Externe Verknüpfung zu Activity

**Link Ontology:** `https://www.construct-x.org/ontology/schedule-progress-report-link#`

| Quelle | Property | Ziel | Bedeutung |
|---|---|---|---|
| `ConstructionProgressReport` | `reportsOnActivity` | `Activity` | Vorgang, auf den sich die Baufortschrittsmeldung bezieht. |

Das Link-SHACL-Profil verlangt genau eine referenzierte `Activity`.

## Beispiel

```turtle
ex:progress-report-2026-10-04-A100
    a conx-cpr:ConstructionProgressReport ;
    conx-cpr:identifier "PR-A100-2026-10-04" ;
    conx-cpr:recordedAt "2026-10-04T15:30:00+02:00"^^xsd:dateTime ;
    conx-cpr:activityStatus conx-status:InProgress ;
    conx-cpr:completionPercentage "65"^^xsd:decimal ;
    conx-cpr:actualStart "2026-09-28T07:00:00+02:00"^^xsd:dateTime ;
    conx-cpr:forecastFinish "2026-10-08T16:00:00+02:00"^^xsd:dateTime ;
    conx-sprl:reportsOnActivity ex:activity-A100 .
```

## Umgang mit Änderungen

Eine Baufortschrittsmeldung ist grundsätzlich ein **zeitpunktbezogener, unveränderlicher Datensatz**. Ein neuer Baufortschritt wird daher als neue Meldung mit neuem `recordedAt` erzeugt. Wird eine bereits veröffentlichte Meldung fachlich korrigiert, kann die korrigierte Ressource über `prov:wasRevisionOf` mit der vorherigen Meldung verknüpft werden.
