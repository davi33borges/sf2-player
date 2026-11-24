# SF2 Player

App Android que transforma o celular ou tablet num módulo de som para teclado MIDI
(feito pensando no Novation Launchkey 61 MK4). Lê arquivos **.sf2** (SoundFont).

- Interface de **mesa de som** (celular deitado): 8 canais com fader, pan, mute e medidor + master
- Até **8 instrumentos** ao mesmo tempo, em camadas ou divididos pelo teclado (split)
- Toque no nome do canal para configurar o timbre: preset, **release, velocity, cutoff, compressor,
  grave, médio, agudo, reverb**, pan, faixa de teclas, transposição e canal MIDI
- **Configurações do MASTER** (toque em MASTER):
  - escolha da **placa de áudio** (USB, P2, alto-falante…) ou automático
  - **saídas L e R** com volume, mute e medidor próprios; modo **2 saídas independentes**
    (ex.: L numa caixa, R na mesa de som) e roteamento de cada canal para L, R ou as duas
  - **reverb geral**: tamanho, tom e volume
- **Presets gerais**: salve a mesa inteira (todos os .sf2, timbres, master, reverb e mapeamentos) com um nome
  e carregue em um toque, por exemplo antes de um evento
- **Timbres salvos**: guarde o som de um canal (.sf2 + preset + ajustes) e use em qualquer canal
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
