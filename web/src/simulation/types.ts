export type CreatureKind = 'moss' | 'coral' | 'lilac'

export interface CreatureRuleView {
  id: string
  source: string
  condition: string
  action: string
}

export interface Creature {
  id: string
  name: string
  kind: CreatureKind
  x: number
  y: number
  phase: number
  energy: number
  generation: number
  goal: string
  lastRule: string
  observation: string
  programSource: string
  rules: CreatureRuleView[]
}

export interface Food {
  id: string
  x: number
  y: number
  size: number
  phase: number
}

export interface WorldSnapshot {
  creatures: Creature[]
  food: Food[]
  tick: number
}

export interface ForkBranch {
  id: string
  stateHash: string
  world: WorldSnapshot
}

export interface ForkExperiment {
  id: string
  snapshotTick: number
  snapshotHash: string
  original: ForkBranch
  alternate: ForkBranch
}
