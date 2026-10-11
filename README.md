# Morph Controller (Forge 1.20.1)

Vire qualquer mob, entidade ou bloco com o **Controle de Morph**.

## Compilar pelo GitHub (sem instalar nada)
Suba o projeto para um repositorio no GitHub. A aba **Actions** compila sozinha (`.github/workflows/build.yml`)
e disponibiliza o `.jar` em "Artifacts" (ou na aba Releases, se voce criar uma tag `v1.0.0`).

## Como compilar localmente
1. Instale o JDK 17.
2. Gere o Gradle Wrapper (uma unica vez): rode `./setup-wrapper.sh` (Linux/macOS) ou `setup-wrapper.bat` (Windows)
   com o Gradle instalado, ou copie `gradlew`, `gradlew.bat` e `gradle/wrapper/gradle-wrapper.jar`
   do MDK oficial do Forge 1.20.1. O `gradle-wrapper.properties` ja esta incluido (Gradle 8.1.1).
3. Na pasta do projeto: `./gradlew build` -> o .jar fica em `build/libs/`.
4. Para testar: `./gradlew runClient`.
5. Coloque o .jar na pasta `mods` junto com o Forge 1.20.1 (47.x) e, se quiser, o TaCZ.

## Uso
- Receita: ferro/olho do fim/redstone/diamante (ou aba criativa "Morph Controller").
- Botao direito com o controle (ou tecla **M**) abre o console.
- Abas: Mobs / Entidades / Blocos, busca, duplo clique ou "Transformar".
- Slider de tamanho (vale para o morph e para o personagem original) e botao Resetar.
- Tecla **R**: habilidade ativa do morph (creeper explode, enderman teletransporta,
  blaze/ghast/wither/dragao atiram, esqueleto atira flecha, golem de neve joga bola de neve,
  galinha poe ovo, TNT explode).

## Especialidades preservadas
Vida, dano, armadura, resistencia a knockback e velocidade (proporcional); voo (morcego, abelha, papagaio,
ghast, phantom, allay, vex, blaze, dragao, wither); respirar/nadar na agua e secar fora dela (peixes, lula);
imunidade a fogo (qualquer mob fire-immune), a explosao (creeper/TNT), a wither/veneno (esqueletos, aranhas...),
queda (voadores, galinha, bode reduzido); aranhas escalam paredes; blocos reduzem dano pela resistencia a
explosao, cactus/magma ferem quem ataca, slime/feno amortecem queda.

## TaCZ
Compat opcional por reflexao (nao precisa do TaCZ para compilar): bloqueia tiro durante a transformacao e
enquanto for bloco/entidade nao-viva (configuravel). Itens em maos e armaduras sao copiados para mobs
humanoides, entao a arma aparece na mao do morph. A hitbox do morph e a que as balas do TaCZ acertam.

## Config
`config/morphmod-common.toml`: duracao (40-60 ticks), tamanho min/max, exigir controle, heranca de atributos,
habilidades, blacklist de IDs, etc.

## Novidades desta revisao
- Blocos com entidade de bloco (bau, cama, shulker box, banner, placa, cabeca) agora aparecem (renderizados pelo modelo do item).
- Blocos com direcao (fornalha, etc.) olham para onde o jogador olha.
- Mortos-vivos (zumbi, esqueleto, afogado, phantom, zumbi aldeao) queimam ao sol sem capacete; enderman toma dano da agua.
- Golpes corpo a corpo aplicam efeitos: aranha-da-caverna/abelha (veneno), husk (fome), esqueleto wither (wither), stray (lentidao).
- Hitbox base limitada a 4x4 antes do tamanho (dragao nao prende mais em paredes).
- Estado de morph em cache no servidor; pacote de tamanho com limite de frequencia.

## Ainda nao implementado
Variantes (cor da ovelha, gato, cavalo, bebe, tamanho do slime), sons do mob, gravidade de areia/cascalho,
luz de blocos luminosos, comando `/morph`, GeckoLib (nao necessario para o que o mod faz hoje).
