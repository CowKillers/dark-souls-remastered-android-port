# Dark Souls Remastered launcher (nx2android).
# Package org.nx2a.t01004ab00a260000. Build pipeline: aapt2 + javac + d8,
# injection of dex/.so/theme, realign.py (16 KB .so alignment) and apksigner.
# Sign with YOUR OWN keystore (KEYSTORE env); never commit it or its passwords.
# Host: the nxnative tree in ../host, with the adrenotools patch only (plus the
# alpha-test work described in README.md). versionCode only ever increases.
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
JDK="${JDK:-/usr/lib/jvm/java-17-openjdk-amd64}"
[ -x "$JDK/bin/javac" ] || JDK="$HOME/jdk-dl/jdk-17.0.20.1+1"
SDK="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
BT="$SDK/build-tools/36.1.0"
ANDROID_JAR="$SDK/platforms/android-36/android.jar"
THEME="$ROOT/theme"
HOST_SO="$ROOT/../host/out/libdmhost.so"
EXPECTED_HOST_SHA="${EXPECTED_HOST_SHA:-}"
ADRENO_DIR="${ADRENO_DIR:-}"
[ -n "$ADRENO_DIR" ] && [ -d "$ADRENO_DIR" ] || { echo "ADRENO_DIR nao definido ou inexistente (veja BUILD.md)"; exit 1; }
export PATH="$JDK/bin:$PATH"

BUILD="$ROOT/build"
rm -rf "$BUILD" && mkdir -p "$BUILD/classes" "$BUILD/dex"

echo "[0/6] conferir host com adrenotools e seus marcadores"
cp "$HOST_SO" "$BUILD/libdmhost.so"
HOST_SHA="$(sha256sum "$BUILD/libdmhost.so" | cut -d' ' -f1)"
echo "host sha=$HOST_SHA bytes=$(stat -c%s "$BUILD/libdmhost.so")"
if [ -n "$EXPECTED_HOST_SHA" ]; then [ "$HOST_SHA" = "$EXPECTED_HOST_SHA" ] || { echo "FAIL: host nao e o host esperado"; exit 1; }; fi
strings "$BUILD/libdmhost.so" | grep -q "custom GPU driver via adrenotools" || { echo "FAIL: host sem marcador adrenotools"; exit 1; }
strings "$BUILD/libdmhost.so" | grep -q "active driver" || { echo "FAIL: host sem marcador active driver"; exit 1; }

echo "[1/6] aapt2 compile res"
"$BT/aapt2" compile --dir "$ROOT/android/res" -o "$BUILD/compiled_res.zip"

echo "[2/6] javac + d8"
"$JDK/bin/javac" -encoding UTF-8 --release 8 -cp "$ANDROID_JAR" -d "$BUILD/classes" \
  "$ROOT/android/src/org/nx2a/launcher/DarkSoulsLauncherActivity.java"
"$BT/d8" --min-api 29 --lib "$ANDROID_JAR" --output "$BUILD/dex" \
  "$BUILD"/classes/org/nx2a/launcher/*.class

echo "[3/6] aapt2 link"
"$BT/aapt2" link -I "$ANDROID_JAR" --manifest "$ROOT/android/AndroidManifest.xml" \
  -o "$BUILD/base.apk" "$BUILD/compiled_res.zip"

echo "[4/6] inject dex + host adrenotools + 5 adrenotools .so + 2 turnips + tema original"
python3 - "$BUILD/base.apk" "$BUILD/injected.apk" "$BUILD/libdmhost.so" "$THEME" "$BUILD/dex/classes.dex" "$ADRENO_DIR" "$ROOT/assets/turnip-a7xx.zip" "$ROOT/assets/turnip-a8xx.zip" << 'PY'
import sys, zipfile
base, out, host_so, theme, dex_path, at_dir, turnip_a7, turnip_a8 = sys.argv[1:9]
zin = zipfile.ZipFile(base)
entries = [(i.filename, zin.read(i.filename), i.compress_type) for i in zin.infolist()]
zin.close()
with zipfile.ZipFile(out, "w") as z:
    for name, data, method in entries:
        z.writestr(name, data, compress_type=method)
    with open(dex_path, "rb") as f:
        z.writestr("classes.dex", f.read(), compress_type=zipfile.ZIP_STORED)  # STORED: apksigner aligns stored entries to 4 bytes, but not deflated ones
    with open(host_so, "rb") as f:
        z.writestr("lib/arm64-v8a/libdmhost.so", f.read(), compress_type=zipfile.ZIP_STORED)
    for at in ("libadrenotools.so", "libhook_impl.so", "libmain_hook.so", "libfile_redirect_hook.so", "libgsl_alloc_hook.so"):
        src = at_dir + ("/" + at if at == "libadrenotools.so" else "/src/hook/" + at)
        with open(src, "rb") as f:
            z.writestr("lib/arm64-v8a/" + at, f.read(), compress_type=zipfile.ZIP_STORED)
    with open(turnip_a7, "rb") as f:
        z.writestr("assets/turnip-a7xx.zip", f.read(), compress_type=zipfile.ZIP_STORED)
    with open(turnip_a8, "rb") as f:
        z.writestr("assets/turnip-a8xx.zip", f.read(), compress_type=zipfile.ZIP_STORED)
    for asset in ("theme.tsv", "phone.png", "tablet.png"):
        with open(theme + "/" + asset, "rb") as f:
            z.writestr("assets/overlay/" + asset, f.read(), compress_type=zipfile.ZIP_STORED)
PY

echo "[5/6] realign (16 KB para os .so, 4 bytes para o dex)"
python3 "$ROOT/realign.py" "$BUILD/injected.apk" "$BUILD/aligned.apk"

echo "[6/6] sign (apksigner, keystore propria) + gates"
[ -n "$KEYSTORE" ] || { echo "KEYSTORE nao definido (veja BUILD.md)"; exit 1; }
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-key-alias "${KEY_ALIAS:-androiddebugkey}" --ks-pass "pass:${KEYSTORE_PASS:-android}" --key-pass "pass:${KEYSTORE_PASS:-android}" --out "$ROOT/DarkSoulsRemastered-launcher-v7.apk" "$BUILD/aligned.apk"
python3 "$ROOT/check.py" "$ROOT/DarkSoulsRemastered-launcher-v7.apk"
"$BT/aapt2" dump badging "$ROOT/DarkSoulsRemastered-launcher-v7.apk" | grep -E "package:|launchable-activity:|native-code:|uses-permission"
"$BT/apksigner" verify --print-certs "$ROOT/DarkSoulsRemastered-launcher-v7.apk"
ls -l "$ROOT/DarkSoulsRemastered-launcher-v7.apk"
sha256sum "$ROOT/DarkSoulsRemastered-launcher-v7.apk"
