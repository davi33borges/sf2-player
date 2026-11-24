# SF2 Player

App Android que transforma o celular ou tablet num módulo de som para teclado MIDI
(feito pensando no Novation Launchkey 61 MK4). Lê arquivos **.sf2** (SoundFont).

- Até **8 instrumentos** ao mesmo tempo, em camadas ou divididos pelo teclado (split)
- Cada instrumento: preset, volume, pan, faixa de teclas, transposição e canal MIDI
- **MIDI learn**: toque em "MIDI" ao lado de um controle e mexa o fader/knob/botão/pad do teclado
- Faixa de teclas pelo próprio teclado: "Tocar p/ definir" → nota mais grave → nota mais aguda
- Botão vermelho no topo para parar todos os sons
- Motor de áudio em C++ ([TinySoundFont](https://github.com/schellingb/TinySoundFont) + [Oboe](https://github.com/google/oboe)) para pouca latência

## Como instalar
Cada envio para a branch `main` gera o APK pelo GitHub Actions.
Baixe o `SF2Player.apk` na página **Releases** do repositório e instale no Android
(permita "instalar apps de fontes desconhecidas").

## Como ligar o teclado
Cabo USB do teclado → adaptador **USB OTG** → celular/tablet. O app conecta sozinho.
