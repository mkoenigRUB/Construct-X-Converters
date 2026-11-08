FROM nginx:1.27-alpine
# Gemeinsame Design-Schicht (GUIDELINE.md §6.2): kopieren statt mounten,
# weil das rdf-frontend über Dockerfile gebaut wird (kein nginx-Volume).
COPY design/conx.css /usr/share/nginx/html/design/conx.css
COPY design/tokens.css /usr/share/nginx/html/design/tokens.css
COPY design/logo.svg /usr/share/nginx/html/design/logo.svg
COPY design/logo-rub.svg /usr/share/nginx/html/design/logo-rub.svg
COPY design/fonts/ /usr/share/nginx/html/design/fonts/
COPY rdf-converter-frontend/index.html /usr/share/nginx/html/index.html
COPY rdf-converter-frontend/styles.css /usr/share/nginx/html/styles.css
COPY rdf-converter-frontend/app.js /usr/share/nginx/html/app.js
COPY rdf-converter-frontend/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
