#!/usr/bin/env python3
"""Gates do launcher Dark Souls, a partir do APK final:
offset real dos dados via headers locais, SHA do .so dentro do APK
(tem que ser o host com adrenotools), alinhamento 16 KB de
TODOS os .so, dex com a Activity, tema em assets/overlay/,
adrenotools 5 .so + 2 turnips presentes."""
import hashlib, os, struct, sys, zipfile

SO = "lib/arm64-v8a/libdmhost.so"
EXPECTED_SO_SHA = os.environ.get("EXPECTED_SO_SHA", "")
EXPECTED_SO_BYTES = int(os.environ.get("EXPECTED_SO_BYTES", "0") or 0)
REQUIRED_SO = ("lib/arm64-v8a/libadrenotools.so", "lib/arm64-v8a/libhook_impl.so", "lib/arm64-v8a/libmain_hook.so", "lib/arm64-v8a/libfile_redirect_hook.so", "lib/arm64-v8a/libgsl_alloc_hook.so")

def local_offsets(path):
    out = {}
    with open(path, "rb") as f:
        data = f.read()
    off = 0
    while off + 30 <= len(data):
        (sig,) = struct.unpack_from("<I", data, off)
        if sig != 0x04034B50:
            break
        (_sig, _ver, _flag, method, _t, _d, _crc, _csize, _usize,
         nlen, elen) = struct.unpack_from("<IHHHHHIIIHH", data, off)
        name = data[off + 30:off + 30 + nlen].decode()
        out[name] = (off, off + 30 + nlen + elen, method)
        off = off + 30 + nlen + elen + _csize
    return out

def main():
    path = sys.argv[1]
    offs = local_offsets(path)
    ok = True
    for name, (hoff, doff, method) in sorted(offs.items()):
        print("  %-45s header=%d data=%d method=%d" % (name, hoff, doff, method))
    z = zipfile.ZipFile(path)
    if SO not in offs:
        print("FAIL: %s ausente" % SO); return 1
    for soname in (SO,) + REQUIRED_SO:
        if soname not in offs:
            print("FAIL: .so ausente: " + soname); ok = False; continue
        doff = offs[soname][1]
        print("  %s data offset=%d mod 16384=%d" % (soname, doff, doff % 16384))
        if doff % 16384 != 0:
            print("FAIL: .so desalinhado: " + soname); ok = False
    so = z.read(SO)
    sha = hashlib.sha256(so).hexdigest()
    print("  .so bytes=%d sha=%s" % (len(so), sha))
    if EXPECTED_SO_SHA and (sha != EXPECTED_SO_SHA or (EXPECTED_SO_BYTES and len(so) != EXPECTED_SO_BYTES)):
        print("FAIL: .so dentro do APK nao e o host esperado"); ok = False
    for tzname in ("assets/turnip-a7xx.zip", "assets/turnip-a8xx.zip"):
        if tzname not in offs:
            print("FAIL: %s ausente como asset" % tzname); ok = False
        else:
            tz = z.read(tzname)
            print("  %s bytes=%d" % (tzname, len(tz)))
            if b"libvulkan_freedreno" not in tz:
                print("FAIL: turnip zip sem libvulkan_freedreno"); ok = False
    for asset in ("assets/overlay/theme.tsv", "assets/overlay/phone.png",
                  "assets/overlay/tablet.png"):
        if asset not in offs:
            print("FAIL: tema ausente: %s" % asset); ok = False
    names = z.namelist()
    if not any("launcher_art.png" in n for n in names):
        print("FAIL: launcher_art.png ausente"); ok = False
    if "classes.dex" not in offs:
        print("FAIL: classes.dex ausente"); ok = False
    elif offs["classes.dex"][1] % 4 != 0:
        print("FAIL: classes.dex desalinhado (mod 4)"); ok = False
    dex = z.read("classes.dex")
    for marker in (b"DarkSoulsLauncherActivity", b"DM_VK_DRIVER", b"turnip-a7xx", b"turnip-a8xx", b"gpu_drivers",
                   b"youtube.com/@emuucheck", b"EmberView", b"launcher_art"):
        if marker not in dex:
            print("FAIL: dex sem marcador %s" % marker); ok = False
    if b"NinjaGaiden" in dex or b"turnip-8g2" in dex:
        print("FAIL: dex com resto de outro jogo"); ok = False
    print("CHECK: %s" % ("OK" if ok else "FAIL"))
    return 0 if ok else 1

if __name__ == "__main__":
    sys.exit(main())
