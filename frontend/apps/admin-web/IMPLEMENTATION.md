# Admin Web

## 실행

`frontend`에서 `pnpm install` 후 `pnpm dev:admin`을 실행한다. 기본 주소는 `http://localhost:3001`이다. `apps/admin-web/.env.local.example`을 `.env.local`로 복사해 브라우저에서 접근 가능한 Admin API 주소를 지정한다. 루트 `docker-compose.yml`은 기존 backend 서비스를 포함하고 admin-web을 3001 포트로 실행한다.

첫 접속은 `/login`에서 시작한다. 서버가 계정을 검증하고 HttpOnly `OTT_ADMIN_SESSION` 쿠키로 세션을 유지하므로 새로고침해도 로그인이 유지된다. 비밀번호는 브라우저 저장소에 저장하지 않는다. 세션은 기본 8시간 비활성 시 만료되며 백엔드 재시작 시 다시 로그인해야 한다. `/admin/**`는 서버에서 세션을 확인하고, 모든 관리자 화면 상단에 계정과 로그아웃 버튼을 표시한다. 로그아웃은 서버 세션을 무효화한다.

API 요청에는 세션 쿠키를 포함하며, 변경 요청과 로그인/로그아웃 전에 `/api/admin/auth/csrf`에서 받은 토큰을 헤더로 전송한다. CSRF 보호는 유지한다. 서버 측 세션 확인에 사용하는 `ADMIN_API_INTERNAL_URL`은 Docker 실행 시 `http://admin-api:8080`, IDE 개발 시 기본 `http://localhost:8080`이다.

## 실제 API 계약

- `POST /api/admin/videos`: `{title,file:{fileName,fileSize,contentType,fingerprint}}`, `Idempotency-Key` 헤더
- `POST /api/admin/video-files/{id}/parts/presign`: `{partNumbers}`
- `POST /api/admin/video-files/{id}/parts`: `{parts:[{partNumber,etag,size}]}`
- `GET /api/admin/video-files/{id}/upload-status`
- `POST /api/admin/video-files/{id}/complete`
- `DELETE /api/admin/video-files/{id}`
- `GET /api/admin/videos/{id}`
- `GET /api/admin/videos?status=&page=&size=`: 페이지 단위 영상 목록
- `GET /api/admin/media-processing-jobs?status=&videoId=&page=&size=`: 페이지 단위 Job 목록
- `POST /api/admin/media-processing-jobs/{id}/retry`

업로드 데이터는 XHR PUT으로 presigned URL에 직접 보낸다. Next.js 서버에 원본 영상을 보내지 않는다. 브라우저가 `ETag`를 읽을 수 있도록 MinIO/S3 bucket CORS에서 `http://localhost:3001`의 `PUT`과 `ETag` 응답 헤더 노출을 설정해야 한다. 서명 URL은 브라우저에서 접근 가능한 `APP_STORAGE_PUBLIC_ENDPOINT`로 생성한다.

## Backend 추가 API 필요

- 전체 영상 제목 검색과 수정일 정렬을 위한 목록 query parameter
- 전체 원본 Upload 건수 및 최근 Upload 목록 조회 endpoint
- 로컬 메타데이터가 삭제된 브라우저에서도 미완료 업로드를 찾을 수 있는 조회 endpoint
- 상세 화면에 표시할 파일명·크기·생성자·생성/수정 시각·Worker ID·작업 시각·오류 상세 필드

영상/Job 목록은 새 페이지 조회 API를 사용한다. Dashboard의 Queued, Processing, Failed, Ready 건수는 필터별 `totalElements`로 집계한다. 전체 Upload 건수는 조회 계약이 없어 표시하지 않는다. 영상 제목 검색은 현재 페이지에만 적용한다. 썸네일은 placeholder이며, HLS preview, 시점 선택, frame extraction, 후보 생성, 대표 이미지 선택, 이미지 업로드는 후속 작업이다.
