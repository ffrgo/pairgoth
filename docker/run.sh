#!/bin/bash

mkdir -p data/jetty

APP_UID=$(id -u) APP_GID=$(id -g) docker compose up
