import type { ForkExperiment } from './types'

const ACTIVE_FORK_KEY = 'brood.active-fork'

class ForkStore {
  async capture(): Promise<ForkExperiment> {
    const experiment = await this.request('/api/forks', { method: 'POST' })
    localStorage.setItem(ACTIVE_FORK_KEY, experiment.id)
    return experiment
  }

  async load(): Promise<ForkExperiment | null> {
    const id = localStorage.getItem(ACTIVE_FORK_KEY)
    if (!id) return null
    try {
      return await this.request(`/api/forks/${encodeURIComponent(id)}`)
    } catch (error) {
      if (error instanceof ForkRequestError && error.status === 404) {
        localStorage.removeItem(ACTIVE_FORK_KEY)
        return null
      }
      throw error
    }
  }

  applyAlternateProgram(id: string, creatureId: string, source: string) {
    return this.request(`/api/forks/${encodeURIComponent(id)}/alternate/creatures/${encodeURIComponent(creatureId)}/program`, {
      method: 'PUT', headers: { 'Content-Type': 'text/plain; charset=utf-8' }, body: source,
    })
  }

  advance(id: string, ticks: number, branch?: 'original' | 'alternate') {
    const route = branch
      ? `/api/forks/${encodeURIComponent(id)}/branches/${branch}/advance`
      : `/api/forks/${encodeURIComponent(id)}/advance`
    return this.request(route, { method: 'POST', headers: { 'Content-Type': 'text/plain' }, body: String(ticks) })
  }

  async discard(id: string) {
    await this.request<null>(`/api/forks/${encodeURIComponent(id)}`, { method: 'DELETE' })
    if (localStorage.getItem(ACTIVE_FORK_KEY) === id) localStorage.removeItem(ACTIVE_FORK_KEY)
  }

  private async request<T = ForkExperiment>(url: string, init: RequestInit = {}): Promise<T> {
    const response = await fetch(url, init)
    if (!response.ok) {
      const message = await response.text()
      throw new ForkRequestError(message || `Fork request failed (${response.status})`, response.status)
    }
    if (response.status === 204) return null as T
    return await response.json() as T
  }
}

class ForkRequestError extends Error {
  constructor(message: string, readonly status: number) { super(message); this.name = 'ForkRequestError' }
}

export const forkStore = new ForkStore()
