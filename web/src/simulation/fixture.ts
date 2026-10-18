import type { Creature, CreatureRule, Food, WorldSnapshot } from './types'

// A fixed, deterministic stand-in keeps the first UI milestone swappable for Java snapshots.
const starterRules: CreatureRule[] = [
  { id: 'r1', condition: 'near-food', action: 'follow-scent' },
  { id: 'r2', condition: 'energy-low', action: 'rest' },
]
const initialCreatures: Creature[] = [
  { id: 'c1', name: 'Mallow', kind: 'moss', x: .18, y: .30, phase: .2, energy: 82, generation: 1, goal: '', lastRule: 'Following a nearby scent', rules: starterRules.map((r) => ({ ...r })), savedRules: starterRules.map((r) => ({ ...r })) },
  { id: 'c2', name: 'Pip', kind: 'coral', x: .42, y: .62, phase: 2.1, energy: 67, generation: 1, goal: '', lastRule: 'Following a nearby scent', rules: starterRules.map((r) => ({ ...r })), savedRules: starterRules.map((r) => ({ ...r })) },
  { id: 'c3', name: 'Lumen', kind: 'lilac', x: .69, y: .31, phase: 4.3, energy: 91, generation: 1, goal: '', lastRule: 'Following a nearby scent', rules: starterRules.map((r) => ({ ...r })), savedRules: starterRules.map((r) => ({ ...r })) },
  { id: 'c4', name: 'Nori', kind: 'moss', x: .82, y: .70, phase: 3.1, energy: 74, generation: 1, goal: '', lastRule: 'Following a nearby scent', rules: starterRules.map((r) => ({ ...r })), savedRules: starterRules.map((r) => ({ ...r })) },
  { id: 'c5', name: 'Peach', kind: 'coral', x: .31, y: .82, phase: 5.0, energy: 58, generation: 1, goal: '', lastRule: 'Following a nearby scent', rules: starterRules.map((r) => ({ ...r })), savedRules: starterRules.map((r) => ({ ...r })) },
  { id: 'c6', name: 'Thimble', kind: 'lilac', x: .57, y: .46, phase: 1.2, energy: 88, generation: 1, goal: '', lastRule: 'Following a nearby scent', rules: starterRules.map((r) => ({ ...r })), savedRules: starterRules.map((r) => ({ ...r })) },
]
const initialFood: Food[] = Array.from({ length: 32 }, (_, i) => ({
  id: `f${i + 1}`, x: ((i * 67 + 19) % 97 + 1) / 100,
  y: ((i * 43 + 11) % 91 + 4) / 100, size: 1.4 + (i % 4) * .55, phase: i * .73,
}))
export function createFixture(): WorldSnapshot {
  return { creatures: initialCreatures.map((c) => ({ ...c, rules: c.rules.map((r) => ({ ...r })), savedRules: c.savedRules.map((r) => ({ ...r })) })), food: initialFood.map((f) => ({ ...f })), tick: 0 }
}
export function updateSavedRules(world: WorldSnapshot, creatureId: string, rules: CreatureRule[]): WorldSnapshot {
  return { ...world, creatures: world.creatures.map((c) => c.id === creatureId ? { ...c, savedRules: rules.map((r) => ({ ...r })) } : c) }
}
export function applyRules(world: WorldSnapshot, creatureId: string, rules: CreatureRule[]): WorldSnapshot {
  return { ...world, creatures: world.creatures.map((c) => c.id === creatureId ? { ...c, rules: rules.map((r) => ({ ...r })), savedRules: rules.map((r) => ({ ...r })) } : c) }
}
export function advanceFixture(world: WorldSnapshot, speed: number): WorldSnapshot {
  const creatures = world.creatures.map((c) => {
    const nearest = world.food.reduce<{ food: Food; distance: number } | null>((best, food) => {
      const distance = Math.hypot(food.x - c.x, food.y - c.y)
      return !best || distance < best.distance ? { food, distance } : best
    }, null)
    const triggeredRule = c.rules.find((rule) => rule.condition === 'energy-low'
      ? c.energy < 24
      : Boolean(nearest && nearest.distance < .32))
    const resting = triggeredRule?.action === 'rest'
    const target = nearest && triggeredRule?.action === 'follow-scent' ? nearest : null
    let x = c.x, y = c.y, energy = Math.max(0, c.energy - .015 * speed), lastRule = c.lastRule
    if (resting) {
      energy = Math.min(100, energy + .06 * speed)
      lastRule = triggeredRule?.condition === 'energy-low' ? 'Resting to recover energy' : 'Resting near a food scent'
    } else if (target) {
      const dx = target.food.x - x, dy = target.food.y - y
      const distance = Math.max(target.distance, .0001), step = Math.min(.0018 * speed, distance)
      x += dx / distance * step + Math.sin(world.tick * .05 + c.phase) * .00015
      y += dy / distance * step + Math.cos(world.tick * .04 + c.phase) * .00015
      lastRule = triggeredRule?.condition === 'energy-low' ? 'Seeking food to restore energy' : 'Following a nearby scent'
      if (distance < .018) { x = target.food.x; y = target.food.y; energy = Math.min(100, energy + 16) }
    } else {
      x += Math.sin(world.tick * .035 + c.phase) * .0003 * speed
      y += Math.cos(world.tick * .03 + c.phase) * .0003 * speed
      lastRule = 'Drifting and exploring'
    }
    return { ...c, x: Math.max(.04, Math.min(.96, x)), y: Math.max(.06, Math.min(.94, y)), energy, goal: resting ? 'Resting in a quiet patch' : target ? `Finding snack ${target.food.id.slice(1)}` : 'Drifting through Fernlight', lastRule }
  })
  const eaten = new Set(creatures.filter((c) => c.lastRule === 'Following a nearby scent').map((c) => {
    const food = world.food.find((f) => Math.hypot(f.x - c.x, f.y - c.y) < .018)
    return food?.id
  }).filter((id): id is string => Boolean(id)))
  return { creatures, food: world.food.filter((f) => !eaten.has(f.id)), tick: world.tick + speed }
}
