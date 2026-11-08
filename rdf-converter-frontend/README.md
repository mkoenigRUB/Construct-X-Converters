# Construct-X RDF Studio

Eigenständiges, statisches Web-Frontend für den Construct-X RDF Converter Service. Es kann unabhängig vom Backend gebaut und ausgeliefert werden.

## Lokal verwenden

Einen beliebigen statischen HTTP-Server im Verzeichnis `rdf-converter-frontend` starten. Das Frontend erwartet die API standardmäßig unter `/api/v1`. Für eine abweichende Adresse kann vor `app.js` in `index.html` beispielsweise `window.CONX_API_BASE = 'https://rdf.example.org/api/v1';` gesetzt werden.

## Container

Im Repository-Hauptverzeichnis ausführen:

```powershell
docker compose -f rdf-converter-compose.yml up --build
```

Frontend: <http://localhost:8080>. Nginx leitet `/api/` an den separaten RDF-Service weiter. Frontend und Backend werden dabei als getrennte Container gebaut und betrieben.
