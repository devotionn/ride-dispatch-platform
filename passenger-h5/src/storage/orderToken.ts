const prefix = 'ride-dispatch:passenger-token:'

export function saveOrderToken(orderNo: string, token: string): void {
  localStorage.setItem(`${prefix}${orderNo}`, token)
}

export function loadOrderToken(orderNo: string): string {
  return localStorage.getItem(`${prefix}${orderNo}`) ?? ''
}
