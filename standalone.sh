#!/bin/sh

# warn about a newer published version (best-effort, 3s; prompt only when interactive)
published=$(curl -sL --max-time 3 https://pairgoth.jeudego.org/en | grep -oE 'pairgoth v[0-9][0-9.]*' | head -1 | sed 's/pairgoth v//')
current=$(grep -m1 '<version>' pom.xml | grep -oE '[0-9.]+')
if [ -n "$published" ] && [ "$published" != "$current" ] && [ -t 0 ]; then
  if [ "$(printf '%s\n%s' "$current" "$published" | sort -V | tail -1)" = "$published" ]; then
    echo "pairgoth v$published has been published (this checkout builds v$current): git pull to update"
    printf "Launch anyway? [Y/n] "
    read answer
    case "$answer" in [nN]*) exit 0;; esac
  fi
fi

# debug version
# mvn -DskipTests=true package && java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -Dpairgoth.mode=standalone -jar application/target/pairgoth-engine.jar

mvn -DskipTests=true package && java -Dpairgoth.mode=standalone -jar application/target/pairgoth-engine.jar
