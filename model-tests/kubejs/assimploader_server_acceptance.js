// Only the explicitly tagged smoke-test entity is affected.
EntityEvents.spawned('assimploader:deer_showcase', function (event) {
  var deer = event.entity
  if (!deer.tags.contains('assimp_kubejs_acceptance') || deer.tags.contains('assimp_kubejs_verified')) return
  if (AssimpServer.apiVersion() !== 1) throw new Error('Server binding version')
  AssimpServer.state(deer, 'gallop')
  AssimpServer.groundPlacement(deer, true)
  AssimpServer.effects(deer, true)
  if (String(AssimpServer.state(deer)) !== 'gallop' || !deer.groundIkEnabled() || !deer.effectsEnabled()) {
    throw new Error('Server commands did not update synchronized entity data')
  }
  AssimpServer.state(deer, 'idle')
  AssimpServer.groundPlacement(deer, false)
  AssimpServer.effects(deer, false)
  deer.addTag('assimp_kubejs_verified')
  console.info('[KubeJSAssimpServer] SUCCESS real EntityEvents.spawned state/ground/effects')
})
