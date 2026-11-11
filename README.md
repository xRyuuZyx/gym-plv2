# Gym

Plugin Paper untuk latihan gym berbasis mesin Nexo/Oraxen. Sesi latihan memakai kamera spectator sementara, lalu memberikan level Strength atau Defense saat sesi selesai.

## Kebutuhan

- Paper 1.21.11 (atau versi yang kompatibel dengan Paper API 1.21.11)
- Java 21
- Nexo atau Oraxen untuk mendeteksi mesin gym (opsional saat plugin dijalankan, tetapi dibutuhkan untuk interaksi mesin)

## Build JAR

```bash
mvn clean package
```

JAR hasil build ada di `target/Gym-1.0.0.jar`. Salin ke folder `plugins/` server Paper, lalu restart server.

## Pengaturan mesin

Edit `plugins/Gym/config.yml`, bagian `machines`:

```yaml
machines:
  strength:
    ids:
      - benchpress
    duration-seconds: 40
    pose: STANDING
  defense:
    ids:
      - pullup
    duration-seconds: 40
    pose: STANDING
```

Sesuaikan `ids` dengan ID furniture/custom block di Nexo atau Oraxen. Gunakan `/gym debug`, lalu klik mesin untuk melihat ID yang terdeteksi. Setelah selesai, matikan debug dengan `/gym debug` lagi. Jalankan `/gym reload` setelah mengubah konfigurasi.

## Perintah dan izin

| Perintah | Kegunaan | Izin |
|---|---|---|
| `/gym` atau `/gym help` | Bantuan | `gym.use` |
| `/gym stats` | Lihat stat sendiri | `gym.use` |
| `/gym stats <pemain>` | Lihat stat pemain online | `gym.admin` dan `gym.use` |
| `/gym stop` | Batalkan latihan | `gym.use` |
| `/gym set <pemain> <strength|defense> <level>` | Atur stat pemain online | `gym.admin` dan `gym.use` |
| `/gym reload` | Muat ulang konfigurasi | `gym.admin` dan `gym.use` |
| `/gym debug` | Toggle debug ID mesin | `gym.admin` dan `gym.use` |

Izin `gym.use` aktif untuk semua pemain secara default, sedangkan `gym.admin` hanya untuk operator. Data pemain disimpan pada `plugins/Gym/data.yml`.

## Kontribusi

Pull request dan laporan bug dipersilakan. Sebelum mengirim perubahan, pastikan `mvn clean package` berhasil.
