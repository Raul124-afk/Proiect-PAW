#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
if [ -x /usr/libexec/java_home ]; then
  JAVA_HOME=$(/usr/libexec/java_home -v 21)
  export JAVA_HOME
  export PATH="$JAVA_HOME/bin:$PATH"
fi
export DB_URL="${DB_URL:-jdbc:postgresql://localhost:5432/medical_db}"
export DB_USERNAME="${DB_USERNAME:-postgres}"
export PORT="${PORT:-18080}"
if command -v pg_isready >/dev/null && ! pg_isready -h localhost -p 5432 >/dev/null; then
  echo 'PostgreSQL local nu răspunde. Pornește serviciul PostgreSQL înaintea aplicației.'
  exit 1
fi
jar_file=target/monitorizare-medicala-0.0.1-SNAPSHOT.jar
if [ ! -f "$jar_file" ]; then
  if [ -f ../monitorizare-medicala.jar ]; then
    jar_file=../monitorizare-medicala.jar
  else
    if command -v mvn >/dev/null; then mvn package; else ./mvnw package; fi
  fi
fi
echo "Aplicația folosește PostgreSQL. Deschide http://localhost:$PORT"
echo 'Oprire: Ctrl+C. Dacă portul este ocupat, oprește mai întâi instanța deja pornită.'
exec java -jar "$jar_file" --server.address=127.0.0.1
