export interface MapPoint {
  address: string
  latitude: number
  longitude: number
}

export interface MapPlace extends MapPoint {
  name: string
}

export interface MapSession {
  search(keyword: string): Promise<MapPlace[]>
  locate(): Promise<MapPoint>
  setPoint(point: MapPoint): void
  destroy(): void
}
