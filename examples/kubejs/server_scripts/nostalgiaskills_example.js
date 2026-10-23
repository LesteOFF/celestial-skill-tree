NostalgiaSkillsEvents.pointsBought(event => {
  event.player.tell(`Bought ${event.points} point(s) for ${event.xpSpent} XP`)
})

NostalgiaSkillsEvents.nodesLearned(event => {
  event.player.tell(`Learned ${event.nodes.length} skill(s), ${NostalgiaSkills.availablePoints(event.player)} point(s) left`)
  if (NostalgiaSkills.hasNode(event.player, 'mypack:duelist/stick_dancer')) {
    event.player.tell('Right-click a stick for a burst of speed')
  }
})

NostalgiaSkillsEvents.nodesRefunded(event => {
  event.player.tell(`Refunded ${event.nodes.length} skill(s) for ${event.pointsReturned} point(s)`)
})

NostalgiaSkillsEvents.buildReset(event => {
  event.player.tell(`Build reset: ${event.pointsReturned} point(s) back, ${NostalgiaSkills.learnedNodes(event.player).length} skill(s) left`)
})

NostalgiaSkillsEvents.skyEventChanged(event => {
  console.info(`Sky event changed from ${event.before} to ${event.now} (binding reports ${NostalgiaSkills.skyEvent()})`)
})

NostalgiaSkillsEvents.starLanded(event => {
  console.info(`A falling star landed at ${event.fragment.blockPosition()}`)
})

NostalgiaSkillsEvents.eliteSpawned(event => {
  console.info(`${event.monster.type} became Constellated: ${NostalgiaSkills.isConstellated(event.monster)}`)
})

PlayerEvents.loggedIn(event => {
  let sign = NostalgiaSkills.sign(event.player)
  event.player.tell(sign == null ? 'Your birth sign is not assigned yet' : `Your birth sign is ${sign}`)
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
    notables: [
      { id: 'side_step', name: 'Side Step', effects: [{ type: 'attribute', attribute: 'nostalgiaskills:dodge_chance', operation: 'add_value', amount: 0.03 }] },
      {
        id: 'stick_dancer',
        name: 'Stick Dancer',
        text: 'Right-clicking a stick gives you Speed for 5 seconds.',
        effects: [{
          type: 'trigger',
          trigger: 'mypack:on_stick_used',
          cooldown_ticks: 200,
          action: { type: 'self_effect', effect: 'minecraft:speed', duration_ticks: 100, amplifier: 0 }
        }]
      }
    ]
  })
})
