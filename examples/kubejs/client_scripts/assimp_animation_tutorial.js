// AssimpShowcase + KubeJS: hold assimploader:showcase_highbone_probe.
// Crouching changes a cached pose, a masked clip layer and a Morph weight.
var assimpTutorial = { handle: null, crouching: null }

function assimpTutorialClear() {
  var old = assimpTutorial.handle
  if (old && old.isValid()) {
    old.removeProcessor('tutorial:pose')
    old.removeLayer('tutorial:wave')
    old.resetMorphs()
  }
  assimpTutorial.handle = null
  assimpTutorial.crouching = null
}

ClientEvents.tick(function (event) {
  var player = event.player
  if (!player || String(player.mainHandItem.id) !== 'assimploader:showcase_highbone_probe') {
    assimpTutorialClear()
    return
  }
  var model = Assimp.instance(player.mainHandItem, 'highbone_probe')
  if (!model) return
  if (!assimpTutorial.handle || !assimpTutorial.handle.sameInstance(model)) {
    assimpTutorialClear()
    assimpTutorial.handle = model
    if (!model.play('Wave', 0.15)) throw new Error('The highbone fixture must contain Wave')
    model.playback(1, true)
    model.layer('tutorial:wave', 'Wave', 0, true, {B255: 1})
    model.processorDefinition('tutorial:pose', {
      version: 1,
      poses: {tilt: {weight: 0, additive: true, space: 'local', bones: {B255: {rotation: [0, 0, 25]}}}}
    })
    model.performance({updateIntervalTicks: 1, renderInterpolation: true})
    console.info('[Assimp tutorial] model=' + model.model() + ', nodes=' + model.bones().size())
  }
  var crouching = player.isShiftKeyDown()
  if (crouching !== assimpTutorial.crouching) {
    model.processorWeight('tutorial:pose', 'tilt', crouching ? 1 : 0)
    model.layerWeight('tutorial:wave', crouching ? 0.3 : 0)
    model.morph(0, [crouching ? 1 : 0])
    assimpTutorial.crouching = crouching
  }
})

ClientEvents.loggedOut(function () { assimpTutorialClear() })
