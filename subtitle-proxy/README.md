# Bstation Subtitle Proxy

Cloudflare Worker untuk mengkonversi subtitle JSON Bstation ke format WebVTT.

## Cara Deploy

### 1. Login ke Cloudflare Dashboard
- Buka https://dash.cloudflare.com/
- Login dengan akun Cloudflare Anda

### 2. Buat Worker Baru
- Klik **Workers & Pages** di sidebar kiri
- Klik **Create Application**
- Pilih **Create Worker**
- Beri nama worker, contoh: `bstation-subtitle`
- Klik **Deploy**

### 3. Edit Kode Worker
- Setelah deploy, klik **Edit code**
- Hapus semua kode default
- Copy-paste seluruh isi file `worker.js` dari folder ini
- Klik **Save and Deploy**

### 4. Catat URL Worker
Format: `https://bstation-subtitle.<your-subdomain>.workers.dev`

Contoh: `https://bstation-subtitle.makairamei.workers.dev`

## Penggunaan

Tambahkan URL subtitle JSON Bstation sebagai parameter `url`:

```
https://bstation-subtitle.your-subdomain.workers.dev/?url=https://subtitle.bstation.tv/xxx.json
```

Worker akan:
1. Fetch JSON subtitle dari Bstation
2. Konversi ke format WebVTT
3. Return dengan Content-Type `text/vtt`

## Update Extension

Setelah deploy, beritahu URL worker Anda agar bisa dimasukkan ke extension Bstation.
