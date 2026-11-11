# Multifolder Wallpapers — Android + GitHub Actions

이 프로젝트는 Android Studio를 설치하지 않고 GitHub Actions에서 APK를 빌드하도록 구성되어 있습니다. 기존 설정 UI는 Android WebView로 실행하고, 실제 홈 화면 배경화면은 Android `WallpaperService`가 렌더링합니다.

## 1) GitHub에 올리기

1. GitHub에서 새 저장소를 만듭니다(예: `MultifolderWallpapers`).
2. 이 ZIP 안의 `WallpaperNative` 폴더 **안에 있는 모든 파일과 폴더**를 저장소 최상위에 업로드합니다. 저장소 루트에 `app/`, `build.gradle`, `settings.gradle`, `.github/`, `docs/`가 보여야 합니다.
3. 기본 브랜치 이름은 `main`으로 두는 것을 권장합니다.

## 2) APK 자동 빌드 및 다운로드

- `main` 또는 `master`에 코드를 push하면 `.github/workflows/android-apk.yml`이 실행됩니다.
- GitHub 저장소의 **Actions** 탭 → **Build Android APK** → 가장 최근 실행 → **Artifacts**에서 `MultifolderWallpapers-debug-apk`를 다운로드합니다.
- 내려받은 ZIP을 풀면 `app-debug.apk`가 있습니다. Android 휴대전화에서 설치를 허용하고 APK를 설치합니다.
- 첫 빌드는 몇 분 걸릴 수 있습니다. 빌드가 실패하면 Actions 실행을 열어 `Build APK` 로그를 확인하세요.

수동 실행도 가능합니다: **Actions → Build Android APK → Run workflow**.

## 3) GitHub Pages / PWA 배포

1. 저장소 **Settings → Pages**로 이동합니다.
2. Build and deployment의 Source를 **GitHub Actions**로 선택합니다.
3. `docs/`가 바뀌어 `main` 또는 `master`에 push되면 `Deploy PWA to GitHub Pages`가 배포합니다.
4. Pages 주소에서 설치 가능한 안내용 PWA를 열 수 있습니다.

**중요:** GitHub Pages PWA는 앱 소개/안내용 웹 설치본입니다. 브라우저 PWA에는 Android `WallpaperService` 권한이 없으므로 실제 시스템 라이브 배경화면을 설정할 수 없습니다. 실제 배경화면 기능은 Actions가 빌드한 APK에서 사용합니다.

## 4) 앱 사용

1. 앱을 실행하고 `폴더 추가`를 눌러 이미지가 든 폴더를 선택합니다.
2. 폴더 권한을 허용합니다. 하위 폴더의 이미지도 검색합니다.
3. 전환 주기, 순서, 채우기 모드, 파일명 표시를 설정합니다.
4. `설정 저장`을 누릅니다.
5. `실제 배경화면 적용`을 누르고 Android 배경화면 선택 화면에서 이 앱의 라이브 배경화면을 선택합니다.

## 5) 빌드 구성

- Application ID: `kr.jh.wallpaper`
- Min SDK: 23 (Android 6.0)
- Compile SDK: 35
- Target SDK: 34
- JDK: 17
- Android Gradle Plugin: 8.7.3
- Gradle: 8.9 (GitHub Actions에서 설치)

GitHub Actions는 debug APK를 생성합니다. CI에서 사용하는 디버그 서명은 테스트/개인 설치용이며, Play Store 배포용 서명과 동일하지 않습니다. 공개 배포용 서명 키는 별도로 안전하게 관리해야 합니다. keystore나 비밀번호를 저장소에 커밋하지 마세요.

## 6) 알려진 제한 사항

- 이 환경에서는 Android SDK로 실제 빌드를 실행할 수 없어 APK 생성 및 실기기 검증은 아직 확인하지 못했습니다. GitHub Actions 첫 실행 결과를 확인해야 합니다.
- WebView의 Tailwind CSS 및 Pretendard는 기존 CDN을 사용하므로 설정 화면 스타일/폰트에 인터넷이 필요합니다. 네이티브 배경화면 엔진은 저장된 이미지 URI를 사용합니다.
- 폴더가 이동/삭제되거나 접근 권한이 철회되면 해당 이미지에 접근하지 못할 수 있습니다.
- 대용량 이미지 및 제조사별 절전 정책은 실제 기기에서 추가 테스트가 필요합니다.
- `assetlinks.json`은 TWA용이며 이 WebView 기반 앱에는 필요하지 않습니다. 기존의 `YOUR_SHA256_FINGERPRINT_HERE` 값은 사용하지 마세요.
