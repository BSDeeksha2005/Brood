export type CreatureKind = 'moss' | 'coral' | 'lilac'
export type RuleCondition = 'near-food' | 'energy-low'
export type RuleAction = 'follow-scent' | 'rest'
export interface CreatureRule { id: string; condition: RuleCondition; action: RuleAction }
export interface Creature {
  id: string; name: string; kind: CreatureKind; x: number; y: number; phase: number
  energy: number; generation: number; goal: string; lastRule: string
  rules: CreatureRule[]; savedRules: CreatureRule[]
}
export interface Food { id: string; x: number; y: number; size: number; phase: number }
export interface WorldSnapshot { creatures: Creature[]; food: Food[]; tick: number }
