import { advanceFixture, applyRules, createFixture, updateSavedRules } from './fixture'
import type { CreatureRule, WorldSnapshot } from './fixtureTypes'

/** Keeps fixture ticks out of React's page render; Canvas observes each world update directly. */
class FixtureStore {
  private world: WorldSnapshot = createFixture()
  private listeners = new Set<() => void>()

  getSnapshot = () => this.world

  subscribe = (listener: () => void) => {
    this.listeners.add(listener)
    return () => this.listeners.delete(listener)
  }

  advance(speed: number) {
    this.world = advanceFixture(this.world, speed)
    this.emit()
  }

  restart() {
    this.world = createFixture()
    this.emit()
  }

  saveRules(creatureId: string, rules: CreatureRule[]) {
    this.world = updateSavedRules(this.world, creatureId, rules)
    this.emit()
  }

  applyRules(creatureId: string, rules: CreatureRule[]) {
    this.world = applyRules(this.world, creatureId, rules)
    this.emit()
  }

  private emit() { this.listeners.forEach((listener) => listener()) }
}

export const fixtureStore = new FixtureStore()
