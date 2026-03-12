#/bin/bash
docker run -it --rm --name pdpot-tools-compile -v "$(pwd)":/src -w /src maven:3.9.12-eclipse-temurin-21-alpine \
mvn clean package $@
