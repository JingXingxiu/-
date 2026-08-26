import { isValidIsbn13 } from '@/utils/isbn'

export function scanBookIsbn(): Promise<string> {
  return new Promise((resolve, reject) => {
    // #ifdef MP-WEIXIN
    uni.scanCode({
      scanType: ['barCode'],
      success: (result) => {
        const isbn = String(result.result ?? '').replace(/[\s-]/g, '')
        if (!isValidIsbn13(isbn)) {
          reject(new Error('扫描结果不是有效的 ISBN-13'))
          return
        }
        resolve(isbn)
      },
      fail: () => reject(new Error('未完成扫码，请重试')),
    })
    // #endif

    // #ifndef MP-WEIXIN
    reject(new Error('H5 暂不支持扫码，请手动输入或粘贴 ISBN'))
    // #endif
  })
}
