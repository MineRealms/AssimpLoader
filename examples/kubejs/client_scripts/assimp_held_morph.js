// Hold assimploader:showcase_highbone_probe, then crouch to change its Morph.
var assimpHeld = null
var assimpCrouching = null

ClientEvents.tick(function (event) {
  var player = event.player
  if (!player || String(player.mainHandItem.id) !== 'assimploader:showcase_highbone_probe') {
    assimpHeld = null
    assimpCrouching = null
    return
  }
  var next = Assimp.instance(player.mainHandItem, 'highbone_probe')
  if (!next) return
  if (!assimpHeld || !assimpHeld.sameInstance(next)) {
    assimpHeld = next
    assimpHeld.performance({renderInterpolation: true, updateIntervalTicks: 1})
    assimpCrouching = null
  }
  var crouching = player.isShiftKeyDown()
  if (assimpCrouching !== crouching) {
    assimpHeld.morph(0, [crouching ? 1 : 0])
    assimpCrouching = crouching
  }
})

ClientEvents.loggedOut(function () {
  assimpHeld = null
  assimpCrouching = null
})
