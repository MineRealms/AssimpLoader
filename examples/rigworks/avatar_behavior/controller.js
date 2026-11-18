RigWorks.register('example:avatar', frame => {
  const input = frame.input();
  const state = frame.state();
  state.lastTick = input.tick;
  frame.defaultAnimation();

  const arm = frame.bone('right_upper_arm');
  if (arm && input.swingActive && input.swingArm === 'right') {
    const bones = {};
    bones[arm] = {rotation: [-15 * Math.sin(input.swingProgress * Math.PI), 0, 0]};
    frame.model().pose('arm_accent', {space: 'model', additive: true, bones: bones});
  } else {
    frame.model().removePose('arm_accent');
  }
});
