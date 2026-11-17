// Opt-in client_scripts demo. Authored for the AssimpLoader Blender block-player rig.
// Existing locomotion, IK and physics stay in Java; the action policy and poses live here.
var AssimpPlayerActions = (function () {
  var config = {
    id: 'assimploader:mobends_style_player',
    autoEnable: false,
    profile: 'assimploader:models/character/block_player/profile.json',
    comboResetTicks: 20,
    guardStartTicks: 10,
    guardEndTicks: 60,
    spinAttack: true,
    spinRequiresSprint: true,
    alternateFists: true,
    bowDrawTicks: 15,
    movementThreshold: 0.04,
    cape: { enabled: true, role: 'cape', amplitude: 12, speedInfluence: 4 },
    // Override semantic roles for other skeletons. Unknown optional roles are skipped.
    bones: {},
    // Weights form an upper-body mask: action poses never contain legs.
    mask: { spine: 0.45, chest: 0.8, left_upper_arm: 1, right_upper_arm: 1,
      left_forearm: 1, right_forearm: 1, left_hand: 1, right_hand: 1 },
    blockedStates: ['dead', 'sleeping', 'elytra'],
    // First match wins; id and tag filters are optional and combine with AND.
    // {id:'yourmod:katana', attack:'sword'}, {tag:'forge:tools/axes', attack:'tool'},
    // {id:'yourmod:wand', use:'bow'}, {id:'yourmod:lantern', hold:'torch'}
    items: [{ id: 'minecraft:torch', hold: 'torch' }, { id: 'minecraft:soul_torch', hold: 'torch' }],
    combo: ['slash_up', 'slash_down', 'slash_inward', 'slash_outward', 'slash_spin'],
    // [native normalized phase, semantic bone Euler XYZ degrees].
    // arm/forearm/hand = attacking arm, support_* = the opposite arm.
    // Left-handed playback mirrors Y/Z and swaps both arms; X stays unchanged.
    attacks: {
      slash_up: { easing: 'smooth', keys: [
        [0, { arm: [-25, -45, -35], forearm: [-55, 0, 0], chest: [8, -25, 0] }],
        [0.45, { arm: [-145, 20, 35], forearm: [-15, 0, 0], chest: [-8, 30, 0] }],
        [1, { arm: [-20, 0, -8], forearm: [-25, 0, 0], chest: [0, 0, 0] }]] },
      slash_down: { easing: 'smooth', keys: [
        [0, { arm: [-165, -20, 25], forearm: [-65, 0, 0], chest: [-10, -20, 0] }],
        [0.45, { arm: [-25, 20, -25], forearm: [-5, 0, 0], chest: [15, 25, 0] }],
        [1, { arm: [-20, 0, -8], forearm: [-25, 0, 0], chest: [0, 0, 0] }]] },
      slash_inward: { easing: 'smooth', keys: [
        [0, { arm: [-90, -65, -55], forearm: [-35, 0, 0], chest: [0, -30, 0] }],
        [0.45, { arm: [-85, 65, 35], forearm: [-15, 0, 0], chest: [5, 35, 0] }],
        [1, { arm: [-20, 0, -8], forearm: [-25, 0, 0], chest: [0, 0, 0] }]] },
      slash_outward: { easing: 'smooth', keys: [
        [0, { arm: [-85, 55, 45], forearm: [-55, 0, 0], chest: [0, 25, 0] }],
        [0.45, { arm: [-95, -60, -55], forearm: [-15, 0, 0], chest: [5, -35, 0] }],
        [1, { arm: [-20, 0, -8], forearm: [-25, 0, 0], chest: [0, 0, 0] }]] },
      slash_spin: { easing: 'linear', spin: true, keys: [
        [0, { arm: [-80, -55, -60], forearm: [-20, 0, 0], support_arm: [-30, 0, 40] }],
        [0.6, { arm: [-100, 40, -55], forearm: [-10, 0, 0], support_arm: [-70, 0, 55] }],
        [1, { arm: [-20, 0, -8], forearm: [-25, 0, 0], support_arm: [0, 0, 0] }]] },
      tool: { easing: 'smooth', keys: [
        [0, { arm: [-145, -8, -10], forearm: [-45, 0, 0], chest: [-5, -15, 0] }],
        [0.5, { arm: [-25, 10, -10], forearm: [-5, 0, 0], chest: [10, 20, 0] }],
        [1, { arm: [0, 0, 0], forearm: [-10, 0, 0], chest: [0, 0, 0] }]] },
      fist: { easing: 'smooth', keys: [
        [0, { arm: [-60, -10, -15], forearm: [-90, 0, 0], chest: [0, -10, 0] }],
        [0.35, { arm: [-95, 10, -5], forearm: [-5, 0, 0], chest: [5, 15, 0] }],
        [1, { arm: [-35, 0, -15], forearm: [-75, 0, 0], chest: [0, 0, 0] }]] }
    },
    poses: {
      sword_guard: { arm: [-45, -12, -15], forearm: [-60, 0, 0], support_arm: [-25, 5, 12], support_forearm: [-30, 0, 0] },
      sword_guard_sprint: { arm: [15, -15, -25], forearm: [-80, 0, 0], chest: [12, -10, 0] },
      fist_guard: { arm: [-40, -10, -20], forearm: [-90, 0, 0], support_arm: [-40, 10, 20], support_forearm: [-90, 0, 0] },
      shield: { arm: [-65, 35, -25], forearm: [-65, 0, 0] },
      torch: { arm: [-105, -5, -8], forearm: [-10, 0, 0] },
      bow: { arm: [-90, -70, -10], forearm: [-15, 0, 0], support_arm: [-90, 30, 10], support_forearm: [-105, 0, 0], chest: [0, -20, 0] },
      eat: { arm: [-45, 20, -15], forearm: [-110, 0, 0] },
      crossbow: { arm: [-90, 25, -5], forearm: [-20, 0, 0], support_arm: [-80, -30, 10], support_forearm: [-70, 0, 0] },
      spear: { arm: [-165, -15, -15], forearm: [-35, 0, 0], chest: [0, -20, 0] },
      spyglass: { arm: [-80, 25, -10], forearm: [-120, 0, 0] },
      horn: { arm: [-65, 25, -15], forearm: [-115, 0, 0] }
    },
    // Add pack-specific policy here. Return true to finish this tick yourself.
    before: null,
    // Called after the template so a pack can add Morph, IK, clips or physics controls.
    after: null
  }

  function value(map, key, fallback) { var v = map.get(key); return v === null || v === undefined ? fallback : v }
  function bool(map, key) { return String(value(map, key, false)) === 'true' }
  function num(map, key, fallback) { return Number(value(map, key, fallback === undefined ? 0 : fallback)) }
  function clamp(v, low, high) { return Math.max(low, Math.min(high, v)) }
  function other(arm) { return arm === 'left' ? 'right' : 'left' }
  function hasTag(item, tag) {
    var tags = value(item, 'tags', null)
    if (!tags) return false
    for (var n = 0; n < tags.size(); n++) if (String(tags.get(n)) === tag) return true
    return false
  }
  function classification(item, key, fallback) {
    for (var n = 0; n < config.items.length; n++) {
      var rule = config.items[n]
      if (rule.id && rule.id !== String(value(item, 'id', 'minecraft:air'))) continue
      if (rule.tag && !hasTag(item, rule.tag)) continue
      if (rule[key]) return rule[key]
    }
    return String(value(item, key === 'attack' ? 'attackType' : 'useAnimation', fallback))
  }
  function easing(name, t) {
    t = clamp(t, 0, 1)
    if (typeof name === 'function') return clamp(name(t), 0, 1)
    if (name === 'smooth') return t * t * (3 - 2 * t)
    if (name === 'in') return t * t
    if (name === 'out') return 1 - (1 - t) * (1 - t)
    return t
  }
  function sample(action, phase) {
    if (!action || !action.keys || action.keys.length < 1) throw new Error('Action needs keyframes')
    var keys = action.keys, first = keys[0], last = keys[keys.length - 1]
    if (phase <= first[0]) return first[1]
    if (phase >= last[0]) return last[1]
    for (var k = 1; k < keys.length; k++) {
      if (phase > keys[k][0]) continue
      var a = keys[k - 1], b = keys[k], t = easing(action.easing, (phase - a[0]) / (b[0] - a[0]))
      var names = {}, out = {}, role, axis
      for (role in a[1]) names[role] = true
      for (role in b[1]) names[role] = true
      for (role in names) {
        var av = a[1][role] || [0, 0, 0], bv = b[1][role] || [0, 0, 0]
        out[role] = []
        for (axis = 0; axis < 3; axis++) out[role][axis] = av[axis] + (bv[axis] - av[axis]) * t
      }
      return out
    }
    return last[1]
  }
  function node(frame, role) { return config.bones[role] || frame.bone(role) }
  function semanticRole(role, arm) {
    var names = { arm: 'upper_arm', forearm: 'forearm', hand: 'hand' }
    if (names[role]) return arm + '_' + names[role]
    if (role.indexOf('support_') === 0) return other(arm) + '_' + names[role.substring(8)]
    return role
  }
  function apply(frame, id, roles, arm, additive, masked) {
    var bones = {}, any = false, role
    for (role in roles) {
      var mapped = semanticRole(role, arm), bone = node(frame, mapped)
      if (!bone) continue
      var rotation = roles[role], sign = arm === 'left' ? -1 : 1
      var weight = masked ? (config.mask[mapped] === undefined ? 0 : config.mask[mapped]) : 1
      if (weight <= 0) continue
      bones[String(bone)] = { rotation: [rotation[0], rotation[1] * sign, rotation[2] * sign], weight: weight }
      any = true
    }
    if (any) frame.model().pose(id, { bones: bones, additive: !!additive, space: 'model' })
    else frame.model().removePose(id)
  }
  function clear(frame) {
    var model = frame.model()
    model.removePose('player:action').removePose('player:posture').removePose('player:spin').removePose('player:torch_main').removePose('player:torch_off').removePose('player:cape')
  }
  function posture(frame, input) {
    var roles = {}, time = num(input, 'time'), phase = time * 6, arm = 'right', name = ''
    if (bool(input, 'climbing') && !bool(input, 'onGround')) {
      var move = Math.abs(num(input, 'verticalVelocity')) > 0.02 ? Math.sin(phase) : 0
      roles = { left_upper_arm: [-145 + 25 * move, 0, 8], right_upper_arm: [-145 - 25 * move, 0, -8],
        left_forearm: [-35, 0, 0], right_forearm: [-35, 0, 0],
        left_thigh: [-35 - 25 * move, 0, 0], right_thigh: [-35 + 25 * move, 0, 0],
        left_shin: [60 + 25 * move, 0, 0], right_shin: [60 - 25 * move, 0, 0] }
      name = 'climb'
    } else if (bool(input, 'riding') && !bool(input, 'passengerLiving')) {
      roles = { left_thigh: [-90, 0, 8], right_thigh: [-90, 0, -8], left_shin: [90, 0, 0], right_shin: [90, 0, 0] }
      name = 'sit'
    } else if (!bool(input, 'onGround') && bool(input, 'sprinting') && !bool(input, 'flying') && !bool(input, 'swimming') && !bool(input, 'elytra')) {
      roles = { left_upper_arm: [20, 0, 10], right_upper_arm: [-90, 0, -10],
        left_forearm: [-85, 0, 0], right_forearm: [-70, 0, 0],
        left_thigh: [-55, 0, 0], right_thigh: [15, 0, 0], left_shin: [85, 0, 0], right_shin: [95, 0, 0] }
      name = 'sprint_jump'
    }
    apply(frame, 'player:posture', roles, arm, false, false)
    frame.state().put('posture', name)
  }
  function controller(frame) {
    if (config.before && config.before(frame, config)) return
    var input = frame.input(), state = frame.state(), model = frame.model()
    frame.defaultMotion()
    var blocked = false
    for (var b = 0; b < config.blockedStates.length; b++) if (bool(input, config.blockedStates[b])) blocked = true
    if (blocked) { clear(frame); state.put('action', ''); if (config.after) config.after(frame, config); return }
    posture(frame, input)
    var roles = {}, arm = String(value(input, 'swingArm', 'right')), actionName = '', attack = null
    var tick = num(input, 'tick'), generation = num(input, 'swingGeneration'), progress = clamp(num(input, 'swingProgress'), 0, 1)
    var swingItem = input.get('swingItem'), type = classification(swingItem, 'attack', 'fist')
    var active = bool(input, 'swingActive'), newSwing = active && generation !== num(state, 'generation', -1)
    if (newSwing) {
      var lastType = String(value(state, 'attackType', '')), lastArm = String(value(state, 'attackArm', ''))
      var reset = tick - num(state, 'attackTick', -1000000) > config.comboResetTicks || type !== lastType || arm !== lastArm
      var next = reset ? 0 : num(state, 'combo', 0)
      var allowed = []
      for (var m = 0; m < config.combo.length; m++) {
        var candidate = config.attacks[config.combo[m]]
        if (!candidate) throw new Error('Unknown combo action: ' + config.combo[m])
        if (candidate.spin && (!config.spinAttack || config.spinRequiresSprint && !bool(input, 'sprinting'))) continue
        allowed.push(config.combo[m])
      }
      if (type === 'sword' && allowed.length === 0) throw new Error('Sword combo is empty')
      state.put('move', type === 'sword' ? allowed[next % allowed.length] : config.attacks[type] ? type : 'tool')
      state.put('combo', type === 'sword' ? (next + 1) % allowed.length : 0)
      var punchCount = num(state, 'punchCount', 0)
      state.put('punchArm', config.alternateFists && type === 'fist' && String(value(input, 'swingHand', 'main')) === 'main' && punchCount % 2 === 1 ? other(arm) : arm)
      if (type === 'fist') state.put('punchCount', punchCount + 1)
      state.put('attackType', type); state.put('attackArm', arm); state.put('attackTick', tick); state.put('generation', generation)
    }
    var age = tick - num(state, 'attackTick', -1000000)
    model.removePose('player:spin')
    if (bool(input, 'usingItem')) {
      arm = String(value(input, 'useArm', 'right'))
      var use = classification(input.get('useItem'), 'use', 'none'), draw = clamp(num(input, 'useTicks') / config.bowDrawTicks, 0, 1)
      if (use === 'block' || use === 'shield') { roles = config.poses.shield; actionName = 'shield' }
      else if (use === 'bow') {
        roles = sample({ easing: 'smooth', keys: [[0, config.poses.sword_guard], [1, config.poses.bow]] }, draw)
        // Add aim to a copy: configuration keyframes remain reusable after this tick.
        var aimed = {}, aimRole
        for (aimRole in roles) aimed[aimRole] = roles[aimRole].slice()
        var pitch = clamp(num(input, 'headPitch'), -60, 60)
        if (aimed.arm) aimed.arm[0] += pitch
        if (aimed.support_arm) aimed.support_arm[0] += pitch
        if (aimed.chest) aimed.chest[1] += clamp(num(input, 'headYawRelative'), -60, 60) * (arm === 'left' ? -1 : 1)
        roles = aimed
        actionName = 'bow'
      } else if (use === 'eat' || use === 'drink') {
        roles = { arm: config.poses.eat.arm.slice(), forearm: config.poses.eat.forearm.slice() }
        roles.arm[0] += Math.sin(num(input, 'useTicks') * 1.2) * 6
        actionName = use
      } else if (use === 'crossbow') { roles = config.poses.crossbow; actionName = 'crossbow' }
      else if (use === 'spear') { roles = config.poses.spear; actionName = 'spear' }
      else if (use === 'spyglass') { roles = config.poses.spyglass; actionName = 'spyglass' }
      else if (use === 'toot_horn' || use === 'horn') { roles = config.poses.horn; actionName = 'horn' }
      else if (config.poses[use]) { roles = config.poses[use]; actionName = use }
    } else if (active && String(value(state, 'attackType', '')) === type) {
      actionName = String(value(state, 'move', type === 'sword' ? config.combo[0] : type))
      attack = config.attacks[actionName]
      if (!attack) throw new Error('Unknown attack: ' + actionName)
      if (type === 'fist') arm = String(value(state, 'punchArm', arm))
      roles = sample(attack, progress)
      if (attack.spin) apply(frame, 'player:spin', { hips: [0, 360 * progress, 0] }, arm, true, false)
    } else if (age >= config.guardStartTicks && age < config.guardEndTicks && bool(input, 'onGround')) {
      arm = String(value(state, 'attackArm', arm))
      if (type === 'sword' && (bool(input, 'sprinting') || num(input, 'speed') <= config.movementThreshold)) {
        actionName = bool(input, 'sprinting') ? 'sword_guard_sprint' : 'sword_guard'; roles = config.poses[actionName]
      } else if (type === 'fist') { actionName = 'fist_guard'; roles = config.poses.fist_guard }
    }
    apply(frame, 'player:action', roles, arm, false, true)
    var holding = ['main', 'off'], primary = String(value(input, 'primaryArm', 'right'))
    for (var h = 0; h < holding.length; h++) {
      var hand = holding[h], held = input.get(hand + 'Item'), physical = hand === 'main' ? primary : other(primary)
      var torch = classification(held, 'hold', '') === 'torch'
      var occupied = !!roles.support_arm || !!roles.support_forearm ||
        bool(input, 'usingItem') && String(value(input, 'useArm', 'right')) === physical || active && arm === physical
      apply(frame, 'player:torch_' + hand, torch && !occupied ? config.poses.torch : {}, physical, false, true)
    }
    var capeBone = config.cape.enabled && node(frame, config.cape.role)
    var capeRoles = {}
    if (capeBone) capeRoles[config.cape.role] = [-(8 + Math.min(45, num(input, 'speed') * config.cape.speedInfluence) + Math.sin(num(input, 'time') * 4) * config.cape.amplitude), 0, 0]
    apply(frame, 'player:cape', capeRoles, 'right', true, false)
    state.put('action', actionName); state.put('actionPhase', active ? progress : 0); state.put('actionArm', arm)
    if (config.after) config.after(frame, config)
  }
  return { config: config, controller: controller, sample: sample, classify: classification }
})()

Assimp.character().register(AssimpPlayerActions.config.id, AssimpPlayerActions.controller)
if (AssimpPlayerActions.config.autoEnable) Assimp.character().profile(AssimpPlayerActions.config.profile).enable(true)
