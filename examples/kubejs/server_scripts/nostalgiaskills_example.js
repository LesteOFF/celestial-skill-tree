NostalgiaSkillsEvents.nodesLearned(event => {
  event.player.tell(`Learned ${event.nodes.length} skill(s), ${NostalgiaSkills.availablePoints(event.player)} point(s) left`)
})

NostalgiaSkillsEvents.nodesRefunded(event => {
  event.player.tell(`Refunded ${event.nodes.length} skill(s) for ${event.pointsReturned} point(s)`)
})

NostalgiaSkillsEvents.skyEventChanged(event => {
  console.info(`Sky event changed from ${event.before} to ${event.now} (binding reports ${NostalgiaSkills.skyEvent()})`)
})

ItemEvents.rightClicked('minecraft:stick', event => {
  NostalgiaSkills.fireTrigger(event.player, 'mypack:on_stick_used')
})

NostalgiaSkillsEvents.catalog(event => {
  event.rules({ nodes_per_class: 80, max_classes: 8 })
  event.removeKeystone('nostalgiaskills:guardian/bastion')
  event.addTheme('mypack:duelist/footwork', {
    class: 'nostalgiaskills:duelist',
    name: 'Footwork',
    icon: 'nostalgiaskills:textures/skill_icons/mobility.png',
    minor: { effects: [{ type: 'attribute', attribute: 'minecraft:generic.movement_speed', operation: 'add_multiplied_base', amount: 0.01 }] },
    notables: [{ id: 'side_step', name: 'Side Step', effects: [{ type: 'attribute', attribute: 'nostalgiaskills:dodge_chance', operation: 'add_value', amount: 0.03 }] }]
  })
})
