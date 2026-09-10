#!/bin/bash
#
# Creates a self-signed code-signing certificate in the login keychain, once.
#
# This is not about Gatekeeper: an unsigned or ad-hoc signed build gets a new code identity
# every time it is rebuilt, and the Keychain scopes saved credentials to the identity that
# stored them. So every rebuild made macOS think a different application was asking for
# someone else's password, and it prompted. A stable certificate means the app keeps its own
# credentials across rebuilds.
#
# Gatekeeper still refuses this on other machines. That needs a Developer ID and notarisation,
# which package.sh does when they exist.
set -euo pipefail

NAME="MyMemos Local Signing"
KEYCHAIN="$HOME/Library/Keychains/login.keychain-db"

if security find-certificate -c "$NAME" >/dev/null 2>&1; then
    echo "Already present: $NAME"
    exit 0
fi

echo "Creating a self-signed code-signing certificate: $NAME"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

# codeSigning is the extended key usage that makes codesign accept it as an identity.
cat > "$WORK/openssl.cnf" <<CONF
[ req ]
distinguished_name = dn
x509_extensions    = ext
prompt             = no

[ dn ]
CN = $NAME

[ ext ]
basicConstraints       = critical,CA:false
keyUsage               = critical,digitalSignature
extendedKeyUsage       = critical,codeSigning
subjectKeyIdentifier   = hash
CONF

openssl req -x509 -newkey rsa:2048 -sha256 -days 3650 -nodes \
    -keyout "$WORK/key.pem" -out "$WORK/cert.pem" \
    -config "$WORK/openssl.cnf" 2>/dev/null

# OpenSSL 3 defaults to algorithms the Security framework will not read, and an empty
# password makes its MAC check fail outright, so both are pinned to what macOS accepts. The
# password only protects a file that exists for the next two lines.
PASS=$(openssl rand -hex 16)
openssl pkcs12 -export -legacy \
    -keypbe PBE-SHA1-3DES -certpbe PBE-SHA1-3DES -macalg sha1 \
    -inkey "$WORK/key.pem" -in "$WORK/cert.pem" \
    -out "$WORK/identity.p12" -passout "pass:$PASS" 2>/dev/null

# -T lets codesign use the key without asking every single time.
security import "$WORK/identity.p12" -k "$KEYCHAIN" -P "$PASS" -T /usr/bin/codesign

# Without this the key still prompts on each signature, which defeats the point.
security set-key-partition-list -S apple-tool:,apple: -k "" "$KEYCHAIN" >/dev/null 2>&1 || \
    echo "  (could not set the partition list; codesign may ask for your password each time)"

echo "Done. Rebuilds will now keep the app's saved credentials."
echo "macOS will ask once more for the items stored under the old identity; choose Always Allow."
