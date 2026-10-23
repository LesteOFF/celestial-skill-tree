const record = (server, name) => server.runCommandSilent(`scoreboard players add ${name} celestialskilltree_kubejs 1`)

CelestialSkillTreeEvents.pointsBought(event => record(event.player.server, 'pointsBought'))
CelestialSkillTreeEvents.nodesLearned(event => record(event.player.server, 'nodesLearned'))
CelestialSkillTreeEvents.nodesRefunded(event => record(event.player.server, 'nodesRefunded'))
CelestialSkillTreeEvents.buildReset(event => record(event.player.server, 'buildReset'))
CelestialSkillTreeEvents.skyEventChanged(event => record(event.server, 'skyEventChanged'))
CelestialSkillTreeEvents.starLanded(event => record(event.level.server, 'starLanded'))
CelestialSkillTreeEvents.eliteSpawned(event => record(event.monster.server, 'eliteSpawned'))

CelestialSkillTreeEvents.catalog(event => {
  event.addTheme('celestialtest:duelist/kubejs_probe', {
    class: 'celestialskilltree:duelist',
    name: 'KubeJS probe',
    icon: 'celestialskilltree:textures/skill_icons/mobility.png',
    minor: { effects: [{ type: 'attribute', attribute: 'minecraft:generic.movement_speed', operation: 'add_multiplied_base', amount: 0.01 }] },
    notables: [{ id: 'kubejs_probe_notable', name: 'KubeJS probe notable', effects: [{ type: 'attribute', attribute: 'celestialskilltree:dodge_chance', operation: 'add_value', amount: 0.01 }] }]
  })
})
