// Applies synchronized settings to newly spawned showcase deer.
EntityEvents.spawned('assimploader:deer_showcase', function (event) {
  AssimpServer.state(event.entity, 'auto')
  AssimpServer.groundPlacement(event.entity, true)
  AssimpServer.effects(event.entity, true)
})
