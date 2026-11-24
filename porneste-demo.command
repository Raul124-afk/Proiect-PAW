#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
if [ -x /usr/libexec/java_home ]; then
  JAVA_HOME=$(/usr/libexec/java_home -v 21)
  export JAVA_HOME
  export PATH="$JAVA_HOME/bin:$PATH"
fi
echo "Deschide http://localhost:${PORT:-18080} în Safari. Oprire: Ctrl+C."
exec ./mvnw -pl backend spring-boot:run -Dspring-boot.run.profiles=demo "-Dspring-boot.run.arguments=--server.port=${PORT:-18080}"
