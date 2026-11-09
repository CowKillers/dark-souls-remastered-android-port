# Dark Souls Remastered — port Android (nx2android)

Launcher + host modificado para rodar Dark Souls Remastered no Android
(provado no Galaxy S23, Adreno 740), a partir do runtime nx2android.
Uso pessoal.

## O que funciona

- Jogo abre, passa por menu e chega ao gameplay, com saves funcionando.
- Launcher próprio: Jogar, Importar dados, Configurações
  (Handheld/Docked, FPS, opacidade, overlay, vibração), Sobre com
  marcador de versão e fim do log. Desde a v7: abinha lateral,
  arte de fundo, som de fogueira em loop (com mudo), botão do
  canal no YouTube e editor de layout com Ocultar por botão.
- Drivers de GPU: sistema, Turnip a7xx e a8xx empacotados, ou driver
  importado (`.so`/`.zip`) via adrenotools.
- Controle físico (ex.: Gamesir-X3 via USB-C) e overlay de toque na tela.
- Import de dados via SAF: pasta com `RomFS/`, `shaders.dmsv` e `env.txt`.

## O que NÃO está aqui (e nunca vai estar)

- Arquivos do jogo: dump, RomFS, `shaders.dmsv`, data pack, APKs prontos.
- Chaves: `prod.keys`, `title.keys` ou qualquer chave de assinatura.
- Arte oficial do jogo: o ícone é um placeholder neutro, troque pelo seu.
  O `launcher_art.png` aqui também é um placeholder neutro (gradiente
  escuro): troque pela sua própria arte de fundo antes de compilar.

Traga seu próprio dump e importe pelo launcher. Assine com sua própria
keystore (veja `BUILD.md`).

## Como compilar

Veja `BUILD.md`. Resumo: compile o host (`host/`), depois o launcher
(`launcher/`). A próxima build precisa de `versionCode` maior que 9:
este repo já marca 9. Trocar a chave de assinatura exige desinstalação
limpa com backup de `files/` antes.

## Problemas conhecidos

- Grama sem alpha test e instabilidade de desempenho: issue #1.

## Histórico curto

- v1: launcher com Turnip + host só com o patch adrenotools.
- diag: build só de log, que mediu o alpha test do jogo.
- fix v4: variante de fragment shader com descarte GREATER (efeito na
  grama ainda não confirmado no aparelho).
- diag v5: repack do host da diag com `versionCode` maior, para o teste A/B.
- v7: launcher novo (abinha na esquerda, arte da fogueira, som de
  fogueira em loop com mudo, botão do YouTube @emuucheck, brasas) +
  Ocultar por botão no editor de layout do jogo (pausa > Gamepad >
  Editar layout; o botão de pausa nunca some). Host mantém o fix
  do alpha test (GREATER) e o patch adrenotools.
- v8: igual à v7, sem o som de fogueira e sem o botão de mudo
  (removidos a pedido do usuário; brasas, YouTube e Ocultar ficam).
- v9: launcher refeito com a arte do cavaleiro e do eclipse (título
  centralizado no topo, pílula do YouTube abaixo dele), paleta
  tirada da arte (preto, laranja brasa, dourado da espada, cinza frio)
  e efeitos de iluminação (halo do eclipse, brilho da chama,
  brasas, brilho do título, borda da abinha pulsando). Host igual
  ao da v7/v8 (fix alpha + Ocultar); sem som de fogo.
