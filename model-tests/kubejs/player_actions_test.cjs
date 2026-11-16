// Offline policy assertions. Real Rhino/Forge acceptance is a separate gate.
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const source = fs.readFileSync(path.join(__dirname, '../../examples/kubejs/client_scripts/assimp_player_actions.js'), 'utf8')
let callback, registered
const api = { register(id, fn) { registered = id; callback = fn; return this }, profile() { return this }, enable() { return this } }
const context = vm.createContext({ Assimp: { character: () => api } })
vm.runInContext(source, context, { filename: 'assimp_player_actions.js' })
const demo = context.AssimpPlayerActions
assert.equal(registered, demo.config.id)
function item(id = 'minecraft:air', attackType = 'fist', useAnimation = 'none', tags = []) {
  const list = { size: () => tags.length, get: i => tags[i] }
  return new Map(Object.entries({ id, empty: id === 'minecraft:air', attackType, useAnimation, tags: list }))
}
const roles = ['hips', 'chest', 'spine', 'head', 'cape']
for (const side of ['left', 'right']) for (const part of ['upper_arm', 'forearm', 'hand', 'thigh', 'shin', 'foot']) roles.push(side + '_' + part)
function fixture() {
  const state = new Map(), poses = new Map()
  state.put = state.set.bind(state)
  const model = { pose(id, options) { poses.set(id, JSON.parse(JSON.stringify(options))); return this }, removePose(id) { poses.delete(id); return this } }
  let defaultTicks = 0
  return {
    state, poses,
    run(changes = {}) {
      const input = new Map(Object.entries(Object.assign({ tick: 1, time: 0.05, speed: 0, onGround: true,
        swingArm: 'right', swingHand: 'main', primaryArm: 'right', swingGeneration: 0,
        swingProgress: 0, swingActive: false, mainItem: item(), offItem: item(), swingItem: item(),
        useItem: item(), useArm: 'right', useTicks: 0, verticalVelocity: 0 }, changes)))
      callback({ input: () => input, state: () => state, model: () => model,
        defaultMotion() { defaultTicks++ }, bone: role => roles.includes(role) ? role : null })
      for (const pose of poses.values()) for (const bone of Object.values(pose.bones)) {
        assert(bone.rotation.every(Number.isFinite), 'non-finite script rotation')
      }
      return state.get('action')
    },
    get defaultTicks() { return defaultTicks }
  }
}
const sword = item('minecraft:diamond_sword', 'sword')
let f = fixture()
assert.equal(f.run(), '')
assert.equal(f.defaultTicks, 1)
assert(!f.poses.has('player:action'))
assert.equal(f.run({ swingActive: true, swingGeneration: 1, swingProgress: 0.2, swingItem: sword }), 'slash_up')
const initial = f.poses.get('player:action').bones.right_upper_arm.rotation
assert(!Object.keys(f.poses.get('player:action').bones).some(name => /thigh|shin/.test(name)), 'attack overwrote legs')
f.run({ tick: 2, swingActive: true, swingGeneration: 1, swingProgress: 0.8, swingItem: sword })
assert.equal(f.state.get('actionPhase'), 0.8)
assert.notDeepEqual(f.poses.get('player:action').bones.right_upper_arm.rotation, initial)
assert.equal(f.state.get('move'), 'slash_up', 'continuous swing queued a new action')
for (let gen = 2; gen <= 10; gen++) {
  f.run({ tick: gen, swingActive: true, swingGeneration: gen, swingProgress: 0, swingItem: sword })
  assert.equal(f.state.get('actionPhase'), 0, 'rapid attack did not immediately restart')
}
assert.equal(f.run({ tick: 100, swingActive: true, swingGeneration: 11, swingItem: sword }), 'slash_up', 'combo did not reset')
f = fixture()
const sequence = []
for (let gen = 1; gen <= 5; gen++) sequence.push(f.run({ tick: gen, sprinting: true, swingActive: true, swingGeneration: gen, swingProgress: 0.5, swingItem: sword }))
assert.deepEqual(sequence, ['slash_up', 'slash_down', 'slash_inward', 'slash_outward', 'slash_spin'])
assert.equal(f.poses.get('player:spin').bones.hips.rotation[1], 180)
f.run({ tick: 6, swingItem: sword })
assert(!f.poses.has('player:spin'), 'spin transform survived action')
demo.config.spinAttack = false
f = fixture()
for (let gen = 1; gen <= 5; gen++) f.run({ tick: gen, sprinting: true, swingActive: true, swingGeneration: gen, swingItem: sword })
assert.equal(f.state.get('action'), 'slash_up')
demo.config.spinAttack = true
const right = fixture(), left = fixture()
right.run({ swingActive: true, swingGeneration: 1, swingProgress: 0.2, swingItem: sword })
left.run({ swingActive: true, swingGeneration: 1, swingProgress: 0.2, swingItem: sword, swingArm: 'left', swingHand: 'off' })
const rr = right.poses.get('player:action').bones.right_upper_arm.rotation
const lr = left.poses.get('player:action').bones.left_upper_arm.rotation
assert.deepEqual(lr, [rr[0], -rr[1], -rr[2]], 'offhand action not mirrored')
f = fixture()
f.run({ swingActive: true, swingGeneration: 1 })
assert.equal(f.state.get('actionArm'), 'right')
f.run({ tick: 2, swingActive: true, swingGeneration: 2 })
assert.equal(f.state.get('actionArm'), 'left', 'fists did not alternate')
for (const [use, expected] of [['bow', 'bow'], ['block', 'shield'], ['eat', 'eat'], ['drink', 'drink'],
  ['crossbow', 'crossbow'], ['spear', 'spear'], ['spyglass', 'spyglass'], ['toot_horn', 'horn']]) {
  f = fixture()
  assert.equal(f.run({ usingItem: true, useArm: 'left', useTicks: 15, useItem: item('test:' + use, 'tool', use) }), expected)
  assert(f.poses.get('player:action').bones.left_upper_arm, use + ' did not target physical use arm')
}
f = fixture()
assert.equal(f.run({ swingActive: true, swingGeneration: 1, swingItem: item('minecraft:iron_axe', 'tool') }), 'tool')
demo.config.items.unshift({ id: 'test:wand', use: 'bow' }, { tag: 'test:swords', attack: 'sword' })
f = fixture()
assert.equal(f.run({ usingItem: true, useItem: item('test:wand', 'tool') }), 'bow')
f = fixture()
assert.equal(f.run({ swingActive: true, swingGeneration: 1, swingItem: item('test:blade', 'tool', 'none', ['test:swords']) }), 'slash_up')
f = fixture()
f.run({ mainItem: item('minecraft:torch', 'tool') })
assert(f.poses.has('player:torch_main'))
f.run({ tick: 2 })
assert(!f.poses.has('player:torch_main'), 'torch pose survived removal')
for (const state of ['dead', 'sleeping', 'elytra']) {
  f.run({ [state]: true, swingActive: true, swingGeneration: 1, swingItem: sword })
  assert.equal(f.poses.size, 0, state + ' retained an action overlay')
}
f = fixture()
f.run({ climbing: true, onGround: false, verticalVelocity: 0.4 })
assert.equal(f.state.get('posture'), 'climb')
assert(f.poses.get('player:posture').bones.left_shin.rotation[0] >= 0, 'climb knee bent backward')
f.run({ tick: 2, riding: true, passengerLiving: false })
assert.equal(f.state.get('posture'), 'sit')
f.run({ tick: 3, sprinting: true, onGround: false })
assert.equal(f.state.get('posture'), 'sprint_jump')
f.run({ tick: 4 })
assert(!f.poses.has('player:posture'), 'posture survived locomotion change')
f = fixture()
f.run({ swingActive: true, swingGeneration: 1, swingItem: sword })
assert.equal(f.run({ tick: 12, swingItem: sword }), 'sword_guard')
assert.equal(f.run({ tick: 13, sprinting: true, speed: 5, swingItem: sword }), 'sword_guard_sprint')
assert.equal(f.run({ tick: 70, swingItem: sword }), '')
let before = 0, after = 0
demo.config.before = () => { before++; return true }
f = fixture(); f.run(); assert.equal(before, 1); assert.equal(f.defaultTicks, 0)
demo.config.before = null; demo.config.after = () => { after++ }
f.run(); assert.equal(after, 1)
console.log('PASS player actions: native phase/retrigger, 5-hit combo/reset/gates, mirrored offhand, fists, all item uses, rule overrides, locomotion overlays, cleanup and extension hooks')
