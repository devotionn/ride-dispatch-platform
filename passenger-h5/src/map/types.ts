export interface MapPoint {
  address: string
  latitude: number
  longitude: number
}

export interface MapPlace extends MapPoint {
  name: string
  district?: string
}

export interface MapPickerSession {
  search(keyword: string): Promise<MapPlace[]>
  locate(): Promise<MapPoint>
  setPoint(point: MapPoint): void
  destroy(): void
}
