#!/usr/bin/env bash
set -euo pipefail
shopt -s extglob
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$SCRIPT_DIR/../build"

if [ ! -d "$BUILD_DIR" ]; then
    echo "ERROR: $BUILD_DIR not found. Run: ./gradlew build from the project root."
    exit 1
fi

copy_jar() {
    local src="$(echo $BUILD_DIR/$1)"
    local dst="$2"
    if [ ! -f "$src" ]; then
        echo "  WARNING: $src not found, skipping"
        return
    fi
    mkdir -p "$(dirname "$dst")"
    cp -f "$src" "$dst"
    echo "  $1 -> $dst"
}

download_jdbc() {
    local dst="$1"
    if [ ! -f "$dst" ]; then
       curl -sLo "$dst" 'https://dlm.mariadb.com/4639714/Connectors/java/connector-java-3.5.8/mariadb-java-client-3.5.8.jar'
    fi
}

echo "==> paper1"
copy_jar "Authentication-Spigot-*.jar" "$SCRIPT_DIR/paper1/plugins/Authentication-Spigot.jar"
copy_jar "Authorization-Spigot-*.jar" "$SCRIPT_DIR/paper1/plugins/Authorization-Spigot.jar"
copy_jar "BlueMap-Auth-Spigot-*.jar" "$SCRIPT_DIR/paper1/plugins/BlueMap-Auth-Spigot.jar"
copy_jar "BlueMap-Chat-Spigot-*.jar" "$SCRIPT_DIR/paper1/plugins/BlueMap-Chat-Spigot.jar"
copy_jar "BlueMap-MarkerEditor-Spigot-*.jar" "$SCRIPT_DIR/paper1/plugins/BlueMap-MarkerEditor-Spigot.jar"
copy_jar "BlueMap-PrivateLocation-Spigot-*.jar" "$SCRIPT_DIR/paper1/plugins/BlueMap-PrivateLocation-Spigot.jar"
copy_jar "BlueMap-PrivateMaps-Spigot-*.jar" "$SCRIPT_DIR/paper1/plugins/BlueMap-PrivateMaps-Spigot.jar"
copy_jar "BlueMap-Demo-*.jar" "$SCRIPT_DIR/paper1/plugins/BlueMap-Demo.jar"

echo "==> paper2"
copy_jar "Authentication-Spigot-*.jar" "$SCRIPT_DIR/paper2/plugins/Authentication-Spigot.jar"
copy_jar "BlueMap-Auth-Spigot-*.jar" "$SCRIPT_DIR/paper2/plugins/BlueMap-Auth-Spigot.jar"
copy_jar "BlueMap-Chat-Spigot-*.jar" "$SCRIPT_DIR/paper2/plugins/BlueMap-Chat-Spigot.jar"
copy_jar "BlueMap-MarkerEditor-Spigot-*.jar" "$SCRIPT_DIR/paper2/plugins/BlueMap-MarkerEditor-Spigot.jar"
copy_jar "BlueMap-PrivateLocation-Spigot-*.jar" "$SCRIPT_DIR/paper2/plugins/BlueMap-PrivateLocation-Spigot.jar"
copy_jar "BlueMap-Demo-*.jar" "$SCRIPT_DIR/paper2/plugins/BlueMap-Demo.jar"

echo "==> fabric"
copy_jar "Authentication-Fabric-*.jar" "$SCRIPT_DIR/fabric/mods/Authentication-Fabric.jar"
copy_jar "Authorization-Fabric-*.jar" "$SCRIPT_DIR/fabric/mods/Authorization-Fabric.jar"
copy_jar "BlueMap-Auth-Fabric-*.jar" "$SCRIPT_DIR/fabric/mods/BlueMap-Auth-Fabric.jar"
copy_jar "BlueMap-Chat-Fabric-*.jar" "$SCRIPT_DIR/fabric/mods/BlueMap-Chat-Fabric.jar"
copy_jar "BlueMap-MarkerEditor-Fabric-*.jar" "$SCRIPT_DIR/fabric/mods/BlueMap-MarkerEditor-Fabric.jar"
copy_jar "BlueMap-PrivateLocation-Fabric-*.jar" "$SCRIPT_DIR/fabric/mods/BlueMap-PrivateLocation-Fabric.jar"
copy_jar "BlueMap-PrivateMaps-Fabric-*.jar" "$SCRIPT_DIR/fabric/mods/BlueMap-PrivateMaps-Fabric.jar"
download_jdbc "$SCRIPT_DIR/fabric/mariadb-jdbc.jar"

echo ""
echo "Done. Restart servers: docker compose restart paper1 paper2 fabric"
