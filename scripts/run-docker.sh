#!/usr/bin/env sh
docker run -it --rm \
  --name whodat-service \
  -e MICRONAUT_ENVIRONMENTS=local \
  -e MICRONAUT_SERVER_HOST=0.0.0.0 \
  -p 9191:9191 \
  -v /Users/$USER/.config/gcloud/application_default_credentials.json:/home/app/application_default_credentials.json \
  -e GOOGLE_APPLICATION_CREDENTIALS=/home/app/application_default_credentials.json \
  whodat-service:latest
