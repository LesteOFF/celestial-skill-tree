# PROGRESSO — Nostalgia Skills

Documentação para quem joga: `README.md`. Para quem programa: `ARCHITECTURE.md`. Este arquivo guarda só o estado atual e o que falta.

## Estado
- Versão: **1.4.1** (`gradle.properties`). O jar `nostalgiaskills-1.4.1.jar` é o que está em `D:\curseforge\minecraft\Instances\NostagiaCraft\mods\`.
- Testes registrados no README: 30 testes de unidade (`./gradlew test`) e 34 gametests (`./gradlew runGameTestServer`).
- 7 classes (a Mago só com Iron's Spells ou Ars Nouveau), 100 keystones (22 sempre visíveis, as outras no Céu Oculto), 12 signos, estrelas cadentes, chuvas de estrelas, eclipses e a constelação comunitária.

## Não testado
- Só a integração com o KubeJS rodou com o mod instalado. As outras (Iron's Spells, Ars Nouveau, Apotheosis, Curios, Better Combat e o resto da lista do README) compilam contra o jar do parceiro, mas nunca rodaram com ele.
- 4 dos 7 eventos do KubeJS nunca foram disparados com o KubeJS instalado.
- Sem medição de FPS em máquina real, sem playtest com pessoas e sem revisão visual feita por uma pessoa.

## Não feito
- Desaprender um nó remove tudo o que depende dele; não dá para escolher o que manter.
- Todos os nós têm nível máximo 1. O formato aceita `maxRank`, mas nenhum nó usa.
- Buracos negros, grupos exclusivos e conflitos funcionam por datapack, mas a árvore padrão não usa nenhum.
- As tags de armadura aredrite, abyssal e infinity existem, mas nenhum nó as usa.
- A tela da árvore não tem sons de interface.

## Pendências de documentação e licença
- O README aponta para `docs/integracoes/compatibilidade.md` e `docs/integracoes/M-medio.md`, que não existem. O `.gitignore` ignora `docs/`, então a pasta precisa sair dessa lista se for criada.
- Os sons vêm do Hyxcate 1.2.4 (fork do Nyx, MIT). Confirmar a licença do fork antes de distribuir.
