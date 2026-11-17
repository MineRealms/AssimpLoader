// Optional policy: enable the examples/animation_processors resource pack for the clip layer.
if (typeof RigWorks !== 'undefined') {
  RigWorks.register('example:native_channels', function (frame) {
    var input = frame.input()
    var nativeItem = input.get('usingItem') || input.get('swingActive') ||
      input.get('mainItem').get('chargedCrossbow') || input.get('offItem').get('chargedCrossbow')
    var special = input.get('swimming') || input.get('elytra') || input.get('flying') ||
      input.get('riding') || input.get('sleeping') || input.get('dead')
    // Keep native weapon interaction and whole-body special poses intact.
    if (nativeItem || special) {
      frame.nativeAnimation('rigworks:animation/native/default.json')
    } else {
      // Native locomotion/root/head remain; omit the right arm so an authored overlay can replace it.
      frame.nativeAnimationDefinition({ attack: false, items: false, rightArm: 0 })
    }
    var model = frame.model()
    model.processor('example:native_channels', 'assimploader:animation_processors/block_player_demo.json')
    model.processorEnabled('example:native_channels', !nativeItem && !special)
    model.processorWeight('example:native_channels', 'baked_walk', input.get('speed') > 0.04 ? 1 : 0)
    model.processorWeight('example:native_channels', 'guard', input.get('speed') > 0.04 ? 0 : 0.5)
    model.processorEnabled('example:native_channels', 'left_contact', false)
  })
}
