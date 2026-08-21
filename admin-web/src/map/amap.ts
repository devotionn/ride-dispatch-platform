import type { MapPlace, MapPoint, MapSession } from './types'

type AMapSdk = any

declare global {
  interface Window {
    AMap?: AMapSdk
    _AMapSecurityConfig?: { serviceHost?: string }
    __rideDispatchAdminAmapReady?: () => void
  }
}

let loaderPromise: Promise<AMapSdk> | null = null

export function isAmapConfigured(): boolean {
  return Boolean((import.meta.env.VITE_AMAP_KEY ?? '').trim())
}

export async function createAmapSession(
  container: HTMLElement,
  initialPoint: MapPoint | null,
  onPointChanged: (point: MapPoint) => void,
): Promise<MapSession> {
  const AMap = await loadAmap()
  const map = new AMap.Map(container, {
    zoom: initialPoint ? 16 : 12,
    viewMode: '2D',
    center: initialPoint ? [initialPoint.longitude, initialPoint.latitude] : undefined,
  })
  const geocoder = new AMap.Geocoder({ extensions: 'all' })
  const placeSearch = new AMap.PlaceSearch({ pageSize: 12, pageIndex: 1, extensions: 'base' })
  const geolocation = new AMap.Geolocation({ enableHighAccuracy: true, timeout: 10_000, zoomToAccuracy: true })
  map.addControl(geolocation)

  let marker: any = null

  function setMarker(point: MapPoint): void {
    const position = [point.longitude, point.latitude]
    if (!marker) {
      marker = new AMap.Marker({ position, anchor: 'bottom-center' })
      map.add(marker)
    } else {
      marker.setPosition(position)
    }
    map.setCenter(position)
  }

  async function reverse(longitude: number, latitude: number): Promise<MapPoint> {
    const address = await new Promise<string>((resolve) => {
      geocoder.getAddress([longitude, latitude], (status: string, result: any) => {
        resolve(status === 'complete' && result?.regeocode?.formattedAddress
          ? String(result.regeocode.formattedAddress)
          : `${longitude.toFixed(6)}, ${latitude.toFixed(6)}`)
      })
    })
    return { address, latitude, longitude }
  }

  map.on('click', async (event: any) => {
    const longitude = Number(event?.lnglat?.getLng?.())
    const latitude = Number(event?.lnglat?.getLat?.())
    if (!Number.isFinite(longitude) || !Number.isFinite(latitude)) return
    const point = await reverse(longitude, latitude)
    setMarker(point)
    onPointChanged(point)
  })

  if (initialPoint) setMarker(initialPoint)

  return {
    async search(keyword: string): Promise<MapPlace[]> {
      return new Promise((resolve, reject) => {
        placeSearch.search(keyword, (status: string, result: any) => {
          if (status !== 'complete') {
            reject(new Error(result?.info || '地点搜索失败'))
            return
          }
          const pois = Array.isArray(result?.poiList?.pois) ? result.poiList.pois : []
          resolve(pois.map((poi: any): MapPlace | null => {
            const longitude = Number(poi?.location?.getLng?.())
            const latitude = Number(poi?.location?.getLat?.())
            if (!Number.isFinite(longitude) || !Number.isFinite(latitude)) return null
            const district = [poi?.pname, poi?.cityname, poi?.adname]
              .filter((value) => typeof value === 'string' && value.trim()).join('')
            const name = String(poi?.name || '未命名地点')
            const detail = typeof poi?.address === 'string' ? poi.address : ''
            return { name, address: `${district}${detail || name}` || name, latitude, longitude }
          }).filter((item: MapPlace | null): item is MapPlace => item !== null))
        })
      })
    },
    async locate(): Promise<MapPoint> {
      return new Promise((resolve, reject) => {
        geolocation.getCurrentPosition(async (status: string, result: any) => {
          if (status !== 'complete') {
            reject(new Error(result?.message || result?.info || '定位失败'))
            return
          }
          const longitude = Number(result?.position?.getLng?.())
          const latitude = Number(result?.position?.getLat?.())
          if (!Number.isFinite(longitude) || !Number.isFinite(latitude)) {
            reject(new Error('定位结果无效'))
            return
          }
          const point = await reverse(longitude, latitude)
          setMarker(point)
          resolve(point)
        })
      })
    },
    setPoint(point: MapPoint): void { setMarker(point) },
    destroy(): void { map.destroy() },
  }
}

async function loadAmap(): Promise<AMapSdk> {
  if (window.AMap) return window.AMap
  if (loaderPromise) return loaderPromise

  const key = (import.meta.env.VITE_AMAP_KEY ?? '').trim()
  if (!key) throw new Error('地图服务暂未配置')
  const serviceHost = (import.meta.env.VITE_AMAP_SERVICE_HOST ?? '').trim().replace(/\/$/, '')
  if (serviceHost) window._AMapSecurityConfig = { serviceHost }

  loaderPromise = new Promise((resolve, reject) => {
    const callbackName = '__rideDispatchAdminAmapReady'
    const script = document.createElement('script')
    const plugins = ['AMap.PlaceSearch', 'AMap.Geocoder', 'AMap.Geolocation'].join(',')
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${encodeURIComponent(key)}&plugin=${encodeURIComponent(plugins)}&callback=${callbackName}`
    script.async = true
    script.charset = 'utf-8'

    const cleanup = () => {
      window.__rideDispatchAdminAmapReady = undefined
      script.onerror = null
    }
    window.__rideDispatchAdminAmapReady = () => {
      const sdk = window.AMap
      cleanup()
      if (!sdk) {
        loaderPromise = null
        reject(new Error('高德地图 SDK 不可用'))
        return
      }
      resolve(sdk)
    }
    script.onerror = () => {
      cleanup()
      loaderPromise = null
      reject(new Error('高德地图加载失败'))
    }
    document.head.appendChild(script)
  })
  return loaderPromise
}
