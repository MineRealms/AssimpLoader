// Generated into client_scripts only by -PclientKubeJsSmoke.
RigWorks.register('acceptance_top_level', function (frame) { frame.defaultMotion() })
AssimpAcceptance.onClient(function () {
  function check(value, message) { if (!value) throw new Error(message) }
  function stage(schedule, id) {
    for (var index = 0; index < schedule.size(); index++) {
      var entry = schedule.get(index)
      if (String(entry.get('id')) === id) return entry
    }
    throw new Error('missing evaluation stage: ' + id)
  }
  function near(actual, expected, message) { check(Math.abs(Number(actual) - expected) < 0.00001, message + ': ' + actual) }
  var probe = null
  var deer = null
  var block = null
  var morph = null
  try {
    check(Assimp.apiVersion() === 1, 'Assimp client binding missing')
    check(RigWorks.controllers().contains('acceptance_top_level'), 'top-level client script registration failed')
    RigWorks.unregister('acceptance_top_level')
    probe = AssimpAcceptance.create('highbone_probe')
    var model = probe.handle()
    check(RigWorks.controllers().contains(AssimpPlayerActions.config.id), 'canonical player demo did not load in Rhino')
    check(model.isValid(), 'new handle invalid')
    var capabilities = model.capabilities()
    check(Number(capabilities.get('apiVersion')) === 1, 'capability inventory unavailable in Rhino')
    check(String(capabilities.get('source').get('importMode')) === 'DYNAMIC', 'import mode missing from capabilities')
    check(Number(capabilities.get('mesh').get('count')) > 0, 'imported mesh inventory missing')
    check(Number(capabilities.get('skin').get('boneCount')) === 256, 'imported skin inventory missing')
    check(Number(capabilities.get('morph').get('targetCount')) > 0, 'imported Morph inventory missing')
    check(String(capabilities.get('authoring').get('fbxConstraints').get('sourcePresence')) === 'unknown', 'uninspected source constraints were claimed absent')
    check(String(capabilities.get('pbr').get('activeShader')) === 'not_evaluated', 'asset inventory claimed a live shader state')
    AssimpAcceptance.record('capabilities', JSON.stringify({success: true, importMode: 'DYNAMIC', bones: 256,
      meshes: Number(capabilities.get('mesh').get('count')), morphTargets: Number(capabilities.get('morph').get('targetCount')),
      unknownAuthoringPresence: true, shaderNotInferred: true}))
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
    model.processorDefinition('foundation', {version: 1})
    model.processorDefinition('hybrid', {version: 1, after: ['kubejs:processor:foundation'],
      clips: {wave: {clip: 'Wave', weight: 0.5, mask: {B255: 1}}},
      poses: {offset: {additive: true, bones: {B255: {rotation: [5, 0, 0]}}}}
    })
    check(model.processorControls('hybrid').contains('wave'), 'processor declaration did not unwrap in Rhino')
    var schedule = model.schedule()
    var mainStage = stage(schedule, 'kubejs:processor:hybrid')
    var ikStage = stage(schedule, 'kubejs:processor:hybrid:ik')
    check(String(mainStage.get('category')) === 'GENERAL', 'processor main stage has wrong category')
    check(mainStage.get('after').contains('kubejs:processor:foundation'), 'JSON after array did not convert to modifier dependency')
    check(ikStage.get('after').contains('kubejs:processor:hybrid'), 'processor IK stage did not depend on its main stage')
    var immutableSchedule = false
    try { mainStage.put('id', 'incorrect') } catch (error) { immutableSchedule = true }
    check(immutableSchedule, 'schedule diagnostics accepted mutation')
    probe.checkpointProcessor('hybrid')
    model.processorWeight('hybrid', 'wave', 0.75).processorPhase('hybrid', 'wave', 0.5)
    model.processorEnabled('hybrid', 'offset', false).processorEnabled('hybrid', true)
    model.processorReleasePhase('hybrid', 'wave')
    var rejectedProcessor = false
    try { model.processorDefinition('hybrid', {version: 1, clips: {bad: {clip: 'missing'}}}) }
    catch (error) { rejectedProcessor = true }
    check(rejectedProcessor && model.processorControls('hybrid').contains('wave') && probe.processorPreserved('hybrid'), 'processor replacement did not preserve previous state')
    var rejectedMissingAfter = false
    try { model.processorDefinition('hybrid', {version: 1, after: ['kubejs:processor:missing']}) }
    catch (error) { rejectedMissingAfter = true }
    check(rejectedMissingAfter && probe.processorPreserved('hybrid'), 'missing after dependency changed the installed processor')
    var rejectedLateAfter = false
    try { model.processorDefinition('hybrid', {version: 1, after: ['kubejs:processor:hybrid:ik']}) }
    catch (error) { rejectedLateAfter = true }
    check(rejectedLateAfter && probe.processorPreserved('hybrid'), 'later-category after dependency changed the installed processor')
    var rejectedCycle = false
    try { model.processorDefinition('foundation', {version: 1, after: ['kubejs:processor:hybrid']}) }
    catch (error) { rejectedCycle = true }
    check(rejectedCycle && probe.processorPreserved('hybrid'), 'cyclic after dependency corrupted the installed processor')
    AssimpAcceptance.record('evaluationPlan', JSON.stringify({success: true, stageCount: schedule.size(), immutable: true,
      explicitAfter: true, companionAfter: true, rejectedMissing: true, rejectedLateCategory: true, rejectedCycle: true,
      retainedMainAndIkIdentity: true}))
    model.removeProcessor('hybrid')
    model.removeProcessor('foundation')
    morph = AssimpAcceptance.create('animation_probe_morph')
    var morphModel = morph.handle()
    check(morphModel.play('Idle', 0), 'Morph fixture idle clip unavailable')
    var morphMesh = -1
    var morphMeshes = morphModel.meshes()
    for (var meshIndex = 0; meshIndex < morphMeshes.size(); meshIndex++) {
      if (morphMeshes.get(meshIndex).get('morphTargets').size() > 0) { morphMesh = meshIndex; break }
    }
    check(morphMesh >= 0, 'Morph fixture imported no target')
    var morphMask = {}
    morphMask[String(morphMeshes.get(morphMesh).get('name'))] = 1
    morphModel.morph(morphMesh, [0.2])
    morphModel.processorDefinition('shape', {version: 1,
      clips: {pulse: {clip: 'MorphPulse', loop: false, weight: 0.25,
        mask: morphMask}}
    })
    morphModel.processorPhase('shape', 'pulse', 0.5)
    morph.step(0.1)
    near(morph.morphWeight(morphMesh, 0), 0.4, 'Rhino phase/weight/mesh-mask conversion failed at the imported peak')
    morph.step(0.2)
    near(morph.morphWeight(morphMesh, 0), 0.4, 'held processor Morph phase advanced with simulation')
    morphModel.processorPhase('shape', 'pulse', 1)
    morph.step(0.1)
    near(morph.morphWeight(morphMesh, 0), 0.15, 'imported Morph end phase did not compose onto the explicit instance weight')
    morphModel.processorWeight('shape', 'pulse', 1).processorPhase('shape', 'pulse', 0.5)
    morph.step(0.1)
    near(morph.morphWeight(morphMesh, 0), 1, 'Rhino processor weight update did not reach the imported Morph peak')
    morphModel.processorEnabled('shape', 'pulse', false)
    morph.step(0.1)
    near(morph.morphWeight(morphMesh, 0), 0.2, 'disabled processor Morph retained its previous output')
    AssimpAcceptance.record('processorMorph', JSON.stringify({success: true, clip: 'MorphPulse', mesh: morphMesh,
      weightedPeak: 0.4, heldPeak: 0.4, weightedEnd: 0.15, fullPeak: 1, disabledBaseline: 0.2}))
    morphModel.clearScriptControls()
    Assimp.shaders().define('acceptance:rhino', 'assimploader:shader_parameters/acceptance.json')
    Assimp.shaders().uniform('acceptance:rhino', 'assimpAcceptanceVec3', [0.2, 0.4, 0.6])
    Assimp.shaders().enabled('acceptance:rhino', false)
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
    block = AssimpAcceptance.create('block_player')
    var blockModel = block.handle()
    var names = {hips:'Hips',chest:'Chest',spine:'Spine',head:'Head',right_upper_arm:'UpperArm.R',left_upper_arm:'UpperArm.L',
      right_forearm:'Forearm.R',left_forearm:'Forearm.L',right_hand:'Hand.R',left_hand:'Hand.L',
      right_thigh:'Thigh.R',left_thigh:'Thigh.L',right_shin:'Shin.R',left_shin:'Shin.L',right_foot:'Foot.R',left_foot:'Foot.L'}
    policyState = {}; facts.sprinting = true
    var realFrame = {input:function(){return mapped(facts)},state:function(){return stateMap},model:function(){return blockModel},
      bone:function(role){return names[role] || null},defaultMotion:function(){}}
    var expected = ['slash_up','slash_down','slash_inward','slash_outward','slash_spin']
    for (var hit=0;hit<5;hit++) {
      facts.tick=hit+1;facts.swingGeneration=hit+1;facts.swingProgress=0
      AssimpPlayerActions.controller(realFrame)
      check(policyState.action===expected[hit], 'real FBX combo sequence '+hit)
      var start=blockModel.node('Hand.R')
      facts.swingProgress=.45
      AssimpPlayerActions.controller(realFrame)
      var end=blockModel.node('Hand.R')
      var distance=Math.abs(Number(start.get('x'))-Number(end.get('x')))+Math.abs(Number(start.get('y'))-Number(end.get('y')))+Math.abs(Number(start.get('z'))-Number(end.get('z')))
      check(distance>.1, 'real FBX hand did not move through '+expected[hit]+': '+distance)
    }
    blockModel.clearScriptControls()
    RigWorks.unregister(AssimpPlayerActions.config.id)
    var characters = RigWorks
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
    if (block) block.close()
    if (morph) morph.close()
  }
})
