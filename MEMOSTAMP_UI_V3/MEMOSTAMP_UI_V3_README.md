# MemoStamp UI V3 — Quiet Social / Camera-first

Bản này tập trung nâng cấp giao diện theo hướng consumer social app: sạch, tối giản, camera-first, nhấn mạnh ảnh/tem thay vì card và hiệu ứng trang trí.

## Hướng thiết kế

- Warm neutral background + high-contrast black/white.
- Một màu brand coral duy nhất (`#FF5B52`) và một màu utility blue.
- UI gần như không dùng gradient; giảm shadow, border và emoji trong navigation.
- Bottom navigation dạng floating capsule tối giản, camera là visual anchor.
- Camera controls dạng glass nhẹ, shutter trắng cổ điển có dot coral.
- Filter bar dạng text carousel với active indicator nhỏ.
- Home dùng large typography + "On this day" hero + recent stamps + collection rows.
- Vault dùng grid đều, không random rotation/card wrapping.
- Memory Note chuyển thành editorial layout: tem là hero, title/note borderless, metadata gom vào một surface.
- Collection chuyển từ phong cách "stamp book dashboard" sang clean collection browser.
- Stamp Detail chuyển thành memory story + 3 action chính.
- Advanced filter tuning chuyển sang white bottom sheet tối giản.

## Các file đã nâng cấp mạnh

- `core/theme/Color.kt`
- `core/theme/Type.kt`
- `core/theme/Theme.kt`
- `navigation/MemoStampNavGraph.kt`
- `feature/home/HomeScreen.kt`
- `feature/camera/CameraScreen.kt`
- `feature/camera/components/CameraControls.kt`
- `feature/camera/components/CameraFilterBar.kt`
- `feature/camera/components/AdvancedTuneBottomSheet.kt`
- `feature/memorynote/MemoryNoteScreen.kt`
- `feature/vault/StampVaultScreen.kt`
- `feature/vault/StampDetailScreen.kt`
- `feature/collection/CollectionScreen.kt`
- `feature/friends/FriendsAndTradeScreen.kt` (visual cleanup)
- `feature/profile/PassportScreen.kt` (visual cleanup)

## Nguyên tắc tiếp tục phát triển

1. Ảnh/tem/khuôn là visual hero; UI chrome phải lùi lại.
2. Mỗi màn hình chỉ có một primary action.
3. Tránh card-inside-card, gradient và shadow dày.
4. Motion nên dùng spring/fade ngắn, không animation dài.
5. Nếu thêm feature mới, dùng `SurfaceSoft`, `SurfaceWhite`, `SurfaceDark`, `AccentRed` trước khi thêm màu mới.
6. Emoji chỉ dùng cho mood/collection content, không dùng làm system navigation icon.

## Lưu ý tích hợp

ZIP đầu vào chỉ chứa các file UI/source dưới `app/src/...`, không có Gradle wrapper/build files nên bản này không thể được compile độc lập trong sandbox. Hãy chép đè các file vào project MEMOSTAMP đầy đủ rồi chạy:

```bash
./gradlew test
./gradlew assembleDebug
```
