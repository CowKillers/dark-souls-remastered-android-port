#!/usr/bin/env python3
"""Reescreve um APK garantindo alinhamento dos dados de cada entrada.

Regra (a que os launchers do Sigma 2 quebraram): libdmhost.so TEM que
ter offset de dados multiplo de 16384, e classes.dex multiplo de 4.
Nem zipalign -p -f 4, nem calcular o offset a mao, nem iterar pads que
aparecem/somem garantiram isso (um pad que some tira 41 bytes do header
e desloca tudo depois dele, oscilando para sempre). Por isso:
- UM pad e SEMPRE inserido antes de cada entrada alinhada (mesmo com
  0 bytes de dados): a existencia do pad nunca muda, so o tamanho.
- O tamanho e calculado numa UNICA passada para frente, a partir da
  posicao real do arquivo (fp.tell() apos cada writestr), e nao do
  tamanho descomprimido: entradas DEFLATED ocupam o tamanho comprimido
  no zip, e e esse que conta para o offset seguinte.
Depois de rodar, rode check.py para provar o alinhamento a partir dos
headers locais reais.
"""
import sys
import zipfile

ALIGN = {
    "lib/arm64-v8a/libdmhost.so": 16384,
    "lib/arm64-v8a/libadrenotools.so": 16384,
    "lib/arm64-v8a/libhook_impl.so": 16384,
    "lib/arm64-v8a/libmain_hook.so": 16384,
    "lib/arm64-v8a/libfile_redirect_hook.so": 16384,
    "lib/arm64-v8a/libgsl_alloc_hook.so": 16384,
    "classes.dex": 4,
    "AndroidManifest.xml": 4,
    "resources.arsc": 4,
}


def main():
    src, dst = sys.argv[1], sys.argv[2]
    zin = zipfile.ZipFile(src)
    entries = [(i.filename, zin.read(i.filename), i.compress_type) for i in zin.infolist()]
    zin.close()

    pad_n = 0
    with open(dst, "wb") as fp:
        with zipfile.ZipFile(fp, "w") as zout:
            for name, data, method in entries:
                if name in ALIGN:
                    pad_name = "assets/pad%d" % pad_n
                    pad_n += 1
                    pos = fp.tell()
                    # Offset em que os dados desta entrada ficariam:
                    # inicio do pad + header do pad + dados do pad +
                    # header da entrada + nome da entrada.
                    pad_size = (-(pos + 30 + len(pad_name.encode())
                                  + 30 + len(name.encode()))) % ALIGN[name]
                    zout.writestr(pad_name, b"\0" * pad_size,
                                  compress_type=zipfile.ZIP_STORED)
                zout.writestr(name, data, compress_type=method)
    print("realign: %s -> %s (%d pads)" % (src, dst, pad_n))


if __name__ == "__main__":
    main()
