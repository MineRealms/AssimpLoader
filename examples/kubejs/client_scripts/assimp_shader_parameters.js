// Install the matching assets/ tree in kubejs/assets/.
// The active Oculus program must declare and consume these uniforms.
// This example does not inject GLSL or create shader-pack passes.
var assimpShaderReady = false

ClientEvents.tick(function (event) {
  if (!event.player) return
  if (!assimpShaderReady) {
    Assimp.shaders().define('demo:tint', 'assimploader:shader_parameters/demo.json')
    assimpShaderReady = true
  }
  Assimp.shaders().enabled('demo:tint', event.player.isShiftKeyDown())
})

ClientEvents.loggedOut(function () {
  if (assimpShaderReady) Assimp.shaders().enabled('demo:tint', false)
  assimpShaderReady = false
})
