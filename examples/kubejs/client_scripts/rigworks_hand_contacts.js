// Optional policy example. Equipment JSON owns sockets, item/tag filters and limits.
RigWorks.register('example:hand_contacts', function (frame) {
  frame.defaultAnimation()
  var input = frame.input()
  if (input.get('swimming') || input.get('elytra')) {
    frame.noContact()
    return
  }
  // Automatic JSON selection remains active. This value resets next tick.
  if (!frame.contacts().isEmpty()) frame.contactWeight(1)
  // Explicitly select a pack-defined rule when implementing a custom action:
  // frame.contact('my_pack:rifle_support', true)
})
