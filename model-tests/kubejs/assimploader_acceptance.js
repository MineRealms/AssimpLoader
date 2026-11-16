// Generated into client_scripts only by -PclientKubeJsSmoke.
AssimpAcceptance.onClient(function () {
  function check(value, message) { if (!value) throw new Error(message) }
  var probe = null
  var deer = null
  try {
    check(Assimp.apiVersion() === 1, 'Assimp client binding missing')
    probe = AssimpAcceptance.create('highbone_probe')
    var model = probe.handle()
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
    model.removeLayer('script')
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
