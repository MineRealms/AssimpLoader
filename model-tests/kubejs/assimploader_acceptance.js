// Generated into client_scripts only by -PclientKubeJsSmoke.
Assimp.character().register('acceptance_top_level', function (frame) { frame.defaultMotion() })
AssimpAcceptance.onClient(function () {
  function check(value, message) { if (!value) throw new Error(message) }
  var probe = null
  var deer = null
  try {
    check(Assimp.apiVersion() === 1, 'Assimp client binding missing')
    check(Assimp.character().controllers().contains('acceptance_top_level'), 'top-level client script registration failed')
    Assimp.character().unregister('acceptance_top_level')
    probe = AssimpAcceptance.create('highbone_probe')
    var model = probe.handle()
    check(Assimp.character().controllers().contains(AssimpPlayerActions.config.id), 'canonical player demo did not load in Rhino')
    check(model.isValid(), 'new handle invalid')
    check(model.state('wave'), 'JSON state unavailable')
    check(!model.state('missing'), 'unknown state accepted')
    check(model.play('Wave', 0), 'clip unavailable')
    probe.step(0.65)
    var seconds = probe.seconds()
    model.performance({updateIntervalTicks: 4, renderInterpolation: true, maxTrailSamples: 16})
    check(Number(model.performance().get('updateIntervalTicks')) === 4, 'JS object performance conversion failed')
    model.layer('script', 'Wave', 0.25, false, {B255: 1})
    model.layerWeight('script', 0.75)
    model.layerPhase('script', 0.375)
    var clock = model.layerPlayback('script')
    check(Math.abs(Number(clock.get('time')) / Number(clock.get('duration')) - 0.375) < 0.000001, 'layer phase clock conversion failed')
    model.layerReleasePhase('script').layerPlayback('script', 2, false)
    model.removeLayer('script')
    model.pose('wave', {weight: 0.8, additive: true, bones: {B255: {rotation: [12, 3, -4], weight: 0.5}}})
    check(model.bones().contains('B255'), 'bone enumeration missing')
    model.removePose('wave')
    model.phase(0.75)
    var playback = model.playback()
    check(Math.abs(Number(playback.get('time')) / Number(playback.get('duration')) - 0.75) < 0.000001, 'base phase clock conversion failed')
    model.releasePhase().playback(1, true)
    // Exercise the actual template in Rhino against a real native model handle.
    var mapped = function (values) { return {get: function (key) { return values[key] }} }
    var sword = mapped({id: 'minecraft:diamond_sword', attackType: 'sword', tags: [], useAnimation: 'none'})
    var empty = mapped({id: 'minecraft:air', attackType: 'fist', tags: [], useAnimation: 'none'})
    var facts = {tick: 1, time: 0.05, swingGeneration: 1, swingActive: true, swingProgress: 0.6,
      swingArm: 'right', primaryArm: 'right', swingHand: 'main', swingItem: sword, mainItem: sword, offItem: empty, onGround: true}
    var policyState = {}, stateMap = {get: function (key) { return policyState[key] }, put: function (key, value) { policyState[key] = value }}
    var frame = {input: function () { return mapped(facts) }, state: function () { return stateMap }, model: function () { return model },
      bone: function (role) { return role === 'cape' ? null : 'B255' }, defaultMotion: function () {}}
    AssimpPlayerActions.controller(frame)
    check(policyState.action === 'slash_up' && policyState.actionPhase === 0.6, 'real Rhino demo action dispatch or native phase failed')
    facts.tick = 2; facts.swingGeneration = 2; facts.swingProgress = 0
    AssimpPlayerActions.controller(frame)
    check(policyState.action === 'slash_down' && policyState.actionPhase === 0, 'real Rhino rapid combo retrigger failed')
    model.clearScriptControls()
    Assimp.character().unregister(AssimpPlayerActions.config.id)
    var characters = Assimp.character()
    characters.register('acceptance', function (frame) { frame.defaultMotion() })
    check(characters.controllers().contains('acceptance'), 'Rhino Consumer controller registration missing')
    check(String(characters.active()) === 'acceptance', 'registered controller was not selected')
    characters.useDefault().unregister('acceptance')
    model.morph(0, [1])
    var active = model.bounds()
    model.morph(0, [0])
    var zero = model.bounds()
    check(active.get('maxX') > zero.get('maxX'), 'JS array Morph did not affect bounds')
    check(model.node('B255').get('x') !== undefined, 'node query missing')
    check(Number(model.meshes().get(0).get('bones')) === 256, 'mesh metadata missing')
    model.resetMorphs().pause().resume().resetDynamics()
    check(probe.seconds() === seconds, 'script commands advanced simulation')
    var owner = Item.of('minecraft:stick')
    var managed = Assimp.instance(owner, 'assimploader:models/validation/highbone_probe.glb')
    check(managed !== null, 'ItemStack owner did not unwrap')
    check(Assimp.models().size() > 0, 'cached model list empty')
    managed.morph(0, [0.5])
    Assimp.release(owner)
    check(!managed.isValid(), 'released managed owner retained state')
    deer = AssimpAcceptance.create('deer_showcase')
    var rig = deer.handle()
    rig.effects(false).groundPlacement(false)
    check(rig.attachment('antlerL').get('x') !== undefined, 'named attachment missing')
    var bounds = rig.bounds()
    var centerY = (Number(bounds.get('minY')) + Number(bounds.get('maxY'))) / 2
    check(rig.raycast('BOUNDS', 0, centerY, -10, 0, 0, 1, 20) !== null, 'bounds raycast missed')
    probe.close()
    check(!model.isValid(), 'closed handle remained valid')
    var expired = false
    try { model.bounds() } catch (error) { expired = true }
    check(expired, 'expired handle mutation/query accepted')
    AssimpAcceptance.success()
  } catch (error) {
    AssimpAcceptance.failure(String(error))
    throw error
  } finally {
    if (probe) probe.close()
    if (deer) deer.close()
  }
})
