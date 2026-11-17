// Optional executable policy for the RigWorks Blender block-player fixture.
// Enable the resource pack in examples/animation_processors before loading this script.
var AssimpProcessorDemo = {
  resource: 'assimploader:animation_processors/block_player_demo.json',
  id: 'example:hybrid_player',
  contactEnabled: false
}

if (typeof RigWorks !== 'undefined') {
  RigWorks.register('example:hybrid_player', function (frame) {
    frame.defaultAnimation()
    var input = frame.input()
    var model = frame.model()
    // An unchanged resource/id is resolved from this instance, with no file read or JSON parsing.
    model.processor(AssimpProcessorDemo.id, AssimpProcessorDemo.resource)
    var nativeAction = input.get('usingItem') || input.get('swingActive') || input.get('swimming') ||
      input.get('elytra') || input.get('flying') || input.get('riding') || input.get('sleeping') || input.get('dead')
    model.processorEnabled(AssimpProcessorDemo.id, !nativeAction)
    var walking = input.get('onGround') && !input.get('crouching') && input.get('speed') > 0.04
    model.processorWeight(AssimpProcessorDemo.id, 'baked_walk', walking ? 1 : 0)
    var sword = String(input.get('mainItem').get('attackType')) === 'sword'
    model.processorWeight(AssimpProcessorDemo.id, 'guard', sword && !walking ? 0.6 : 0)
    model.processorEnabled(AssimpProcessorDemo.id, 'left_contact', AssimpProcessorDemo.contactEnabled)
    if (AssimpProcessorDemo.contactEnabled) {
      model.processorTarget(AssimpProcessorDemo.id, 'left_contact', 0.45, 1.25, 0.35)
    }
  })
}
