#!/bin/bash

read -rp 'email: ' EMAIL
read -rp 'password: ' PASSWORD

ENCPASS=$(echo -n $PASSWORD | sha256sum)
ENCPASS=${ENCPASS%  -}

sqlite3 pairgoth.db "INSERT INTO cred (email, password) VALUES ('$EMAIL', '$ENCPASS')"
