const record = (server, name) => server.runCommandSilent(`scoreboard players add ${name} nostalgiaskills_kubejs 1`)

NostalgiaSkillsEvents.pointsBought(event => record(event.player.server, 'pointsBought'))
NostalgiaSkillsEvents.nodesLearned(event => record(event.player.server, 'nodesLearned'))
NostalgiaSkillsEvents.nodesRefunded(event => record(event.player.server, 'nodesRefunded'))
NostalgiaSkillsEvents.buildReset(event => record(event.player.server, 'buildReset'))
NostalgiaSkillsEvents.skyEventChanged(event => record(event.server, 'skyEventChanged'))
NostalgiaSkillsEvents.starLanded(event => record(event.level.server, 'starLanded'))
NostalgiaSkillsEvents.eliteSpawned(event => record(event.monster.server, 'eliteSpawned'))

NostalgiaSkillsEvents.catalog(event => {
  event.addTheme('nostalgiatest:duelist/kubejs_probe', {
    class: 'nostalgiaskills:duelist',
    name: 'KubeJS probe',
    icon: 'nostalgiaskills:textures/skill_icons/mobility.png',
    minor: { effects: [{ type: 'attribute', attribute: 'minecraft:generic.movement_speed', operation: 'add_multiplied_base', amount: 0.01 }] },
    notables: [{ id: 'kubejs_probe_notable', name: 'KubeJS probe notable', effects: [{ type: 'attribute', attribute: 'nostalgiaskills:dodge_chance', operation: 'add_value', amount: 0.01 }] }]
  })
})
