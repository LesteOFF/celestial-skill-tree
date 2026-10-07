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
