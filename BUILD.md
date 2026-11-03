# Como compilar

## Pré-requisitos

- Android SDK com NDK `28.2.13676358`, build-tools `36.1.0` e
  plataforma `android-36`.
- JDK 17 (`javac`, `keytool`, `apksigner` do build-tools).
- Python 3.
- Bibliotecas adrenotools (veja abaixo).

Variáveis usadas pelo `launcher/build.sh` (todas com padrão sensato,
exceto `ADRENO_DIR` e `KEYSTORE`, que você precisa apontar):

| Variável | Padrão | Uso |
| --- | --- | --- |
| `ANDROID_SDK_ROOT` | `$HOME/Android/Sdk` | SDK/NDK/build-tools |
| `JDK` | `/usr/lib/jvm/java-17-openjdk-amd64` | JDK 17 |
| `ADRENO_DIR` | — | libs adrenotools (obrigatório) |
| `KEYSTORE` | — | sua keystore (obrigatório) |
| `KEY_ALIAS` | `androiddebugkey` | alias da chave |
| `KEYSTORE_PASS` | `android` | senha da keystore/chave |
| `EXPECTED_HOST_SHA` | — | se definido, o host é conferido |

## 1. Host

```sh
cd host
python3 dm/android/build.py --game 01004AB00A260000 \
  --ndk "$ANDROID_SDK_ROOT/ndk/28.2.13676358" \
  --release --out "$PWD/out"
```

Isso gera `host/out/libdmhost.so` (139 fontes, 0 falhas no último build
verificado). O `build.py` usa o perfil em
`host/dm/games/01004AB00A260000/`.

## 2. Adrenotools

O launcher injeta 5 `.so` do adrenotools no APK. Consiga-os compilando
o adrenotools (ou copiando de um checkout funcional) e aponte:

```sh
export ADRENO_DIR=/caminho/do/adrenotools-build
# esperado: $ADRENO_DIR/libadrenotools.so
#           $ADRENO_DIR/src/hook/{libhook_impl,libmain_hook,libfile_redirect_hook,libgsl_alloc_hook}.so
```

## 3. Assinatura

Crie sua própria keystore uma vez (nunca commite ela nem suas senhas):

```sh
keytool -genkeypair -keystore ~/ds-debug.keystore \
  -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000
```

## 4. Launcher

```sh
cd launcher
export ADRENO_DIR=/caminho/do/adrenotools-build
export KEYSTORE=~/ds-debug.keystore
bash build.sh
```

O script compila os recursos (`aapt2`), o Java (`javac`/`d8`), injeta
dex, host, adrenotools, Turnips e tema, realinha os `.so` para 16 KB,
assina com `apksigner` e roda `check.py` (gates: `.so` alinhados, dex
com a Activity, tema e Turnips presentes; com `EXPECTED_HOST_SHA`
definido, o SHA do host também é conferido).

## Regras de versão e assinatura

- `versionCode` só aumenta. Últimos usados: v1 = 2, diag = 3, fix = 4,
  diag v5 = 5. Este repo marca 6.
- APK assinado com outra chave NÃO instala por cima do anterior:
  faça backup de `files/` (dados e saves) e desinstale limpo antes.

## Instalar e importar dados

Instale o APK, abra o launcher e use Importar dados com a pasta (ou
zip) do seu próprio dump contendo `RomFS/`, `shaders.dmsv` e `env.txt`.
Sem esses arquivos o botão Jogar fica bloqueado. Escolha o driver em
Drivers de GPU; o padrão recomendado até aqui é o Turnip a7xx no
Adreno 740.
