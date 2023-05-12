#!/bin/bash

grep -r '^smtp\.host' webapp.properties | sed -r -e 's/smtp\.host/SMTP_HOST/' -e 's/ //g' > .env
grep -r '^smtp\.port' webapp.properties | sed -r -e 's/smtp\.port/SMTP_PORT/' -e 's/ //g' >> .env
grep -r '^smtp\.user' webapp.properties | sed -r -e 's/smtp\.user/SMTP_USER/' -e 's/ //g' >> .env
grep -r '^smtp\.password' webapp.properties | sed -r -e 's/smtp\.password/SMTP_PASSWORD/' -e 's/ //g' >> .env

mkdir -p data/jetty data/maven

APP_UID=$(id -u) APP_GID=$(id -g) docker compose up
