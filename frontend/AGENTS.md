# Frontend Guidelines

이 파일은 `frontend/` 이하 작업에 적용하는 Frontend-specific guideline이다.

Root `AGENTS.md`의 공통 규칙을 함께 따른다.

---

# 1. Frontend Applications

기본 구조:

```text
frontend/
├─ apps/
│  ├─ admin-web/
│  └─ user-web/
│
└─ packages/
```

책임:

```text
admin-web
= OTT 운영자 CMS

user-web
= 일반 사용자 OTT 서비스
```

두 Application의 책임을 혼합하지 않는다.

---

# 2. Admin Web

Admin Web의 주요 책임:

```text
Content 관리
Localization
Availability
Genre

Season
Episode

Artwork

Video Upload
Encoding 상태
HLS Preview

Collection
Collection Reorder
Territory Preview
```

Admin Preview는 사용자 Playback이 아니다.

다음 기능을 Admin에 넣지 않는다.

```text
Playback Session
Continue Watching
Watch History
Subscription
Recommendation
```

---

# 3. User Web

User Web은 일반 OTT 사용자를 위한 Application이다.

향후 주요 책임:

```text
Home
Collection
Content Detail
Series Detail
Playback
Continue Watching
```

CMS 편집 기능을 user-web에 추가하지 않는다.

---

# 4. Next.js Convention

현재 설치된 Next.js 버전과 기존 directory convention을 먼저 확인한다.

기존 구조가 합리적이면 그대로 따른다.

모든 Component에 습관적으로:

```text
"use client"
```

를 추가하지 않는다.

State, event handler, browser API 등 Client Component가 실제로 필요한 경우에만 사용한다.

---

# 5. Browser-specific Logic

다음 기능은 Client boundary를 명확하게 한다.

```text
File input
Object URL
HLS Player
Drag & Drop
localStorage
window
document
```

Server rendering 과정에서 browser API에 접근하지 않는다.

---

# 6. Backend Business Rules

Backend Business Rule을 Frontend에서 복제하지 않는다.

예:

```text
Content Availability 판단
Collection displayable 판단
Content status 판단
Territory eligibility
```

등을 Frontend가 자체적으로 다시 계산하지 않는다.

Backend가 판정한 결과를 표현한다.

Frontend validation은 UX를 위한 것이며 Backend validation을 대체하지 않는다.

---

# 7. API Contract

Backend Response를 추측하지 않는다.

기존 API와 DTO를 확인한다.

Backend API 변경이 필요한 경우 관련 Backend 영향을 확인한다.

API response와 UI-specific state를 필요에 따라 분리한다.

---

# 8. API Client

HTTP 호출을 Component 곳곳에 직접 복제하지 않는다.

현재 프로젝트의 API client convention을 따른다.

같은 Endpoint를 호출하는 코드를 여러 Component에 복사하지 않는다.

단, 한 번만 사용하는 작은 요청을 추상화하기 위해 과도한 API framework를 만들지 않는다.

---

# 9. TypeScript

가능하면 명확한 타입을 사용한다.

불필요한:

```text
any
as any
unknown as Something
```

을 피한다.

타입 오류를 type assertion으로 숨기기보다 실제 Contract를 확인한다.

---

# 10. Component Responsibility

Component는 줄 수보다 책임을 기준으로 분리한다.

한 Component가 다음을 모두 담당한다면 분리를 검토한다.

```text
API
Form
Validation
Modal
Upload
Table
Drag & Drop
Business Transformation
```

반대로 JSX 몇 줄을 줄이기 위해 의미 없는 작은 Component를 만들지 않는다.

---

# 11. Hooks

실제 reusable stateful behavior가 있을 때 Custom Hook을 사용한다.

모든 함수/로직을 `useXXX`로 만들지 않는다.

Backend Business Rule을 Hook 안에 숨기지 않는다.

---

# 12. State

Local state로 충분한 경우 새로운 global state library를 추가하지 않는다.

예:

```text
modal
selected tab
form state
upload progress
```

실제 여러 화면에서 공유할 필요가 생긴 경우에만 global state를 검토한다.

---

# 13. Server State

API에서 받은 데이터를 불필요하게 여러 local state에 복사하지 않는다.

Source of Truth를 불필요하게 늘리지 않는다.

현재 프로젝트에 server-state/data-fetching convention이 있다면 따른다.

---

# 14. Loading / Error / Empty

데이터 기반 UI에서는 필요에 따라 다음 상태를 구분한다.

```text
Loading
Success
Empty
Error
```

Mutation/Upload:

```text
Idle
Loading
Success
Failure
```

API 오류를 조용히 무시하지 않는다.

---

# 15. Artwork

Content:

```text
POSTER
LANDSCAPE
HERO
```

Episode:

```text
THUMBNAIL
```

의 의미를 유지한다.

기본 사용:

```text
POSTER
→ 세로형 Browse/Grid

LANDSCAPE
→ Collection/Home Card

HERO
→ Content Detail Hero

THUMBNAIL
→ Episode List
```

이미지가 없으면 placeholder를 사용한다.

서로 다른 비율의 Artwork를 강제로 stretch하지 않는다.

---

# 16. Image URL

Frontend는 MinIO objectKey를 이용하여 Storage URL을 직접 만들지 않는다.

Backend가 제공한 URL을 사용한다.

다음과 같은 코드를 만들지 않는다.

```text
"http://localhost:9000/" + objectKey
```

Storage topology를 Frontend에 노출하지 않는다.

---

# 17. File Upload

Frontend에서 UX를 위해:

```text
file type
file size
preview
```

등을 확인할 수 있다.

하지만 이것이 Security boundary는 아니다.

Backend에서도 반드시 검증한다.

Object Key를 Frontend가 생성하지 않는다.

---

# 18. Responsive UI

합리적인 Responsive UI를 구현한다.

Artwork Type을 단순히:

```text
desktop → LANDSCAPE
mobile → POSTER
```

로 고정하지 않는다.

현재 UI Component의 목적에 따라 Artwork를 선택한다.

---

# 19. Accessibility

가능한 경우 semantic HTML을 사용한다.

확인:

```text
button
label
alt
keyboard
focus
```

클릭 이벤트가 있다는 이유만으로 `div`를 button처럼 사용하지 않는다.

Modal, Drag & Drop, File Upload에서 기본 접근성을 고려한다.

---

# 20. Styling

현재 프로젝트 styling convention을 따른다.

Task 하나를 위해 새로운 CSS/UI framework를 추가하지 않는다.

이미 존재하는:

```text
Button
Form
Table
Modal
Spacing
Typography
```

pattern이 있다면 재사용한다.

---

# 21. Shared Packages

`frontend/packages`에는 실제로 둘 이상의 Application에서 공유되는 책임만 넣는다.

다음 이유로 미리 이동하지 않는다.

```text
"나중에 user-web에서도 사용할 것 같아서"
```

Admin-specific Component를 shared package로 이동하지 않는다.

실제 중복이 발생한 후 공통화를 검토한다.

---

# 22. HLS

Admin Preview와 User Playback은 Domain 책임이 다르다.

공유 가능한 것은 필요하다면 low-level player integration이다.

예:

```text
hls.js setup
browser compatibility
cleanup
```

다음은 공유하지 않는다.

```text
Admin Preview Business Logic
Playback Session
Watch Event
Subscription
```

---

# 23. Environment Variables

프로젝트의 `.env.example`을 참고한다.

필요한 Local `.env` 또는 framework-specific local environment file을 생성한다.

실제 환경 파일은 Git에 commit하지 않는다.

Frontend에서 공개 가능한 값과 Secret을 구분한다.

특히:

```text
NEXT_PUBLIC_*
```

값은 Browser bundle에 노출된다고 가정한다.

여기에 secret을 넣지 않는다.

---

# 24. Secret Handling

다음을 Frontend source에 hardcode하지 않는다.

```text
DB credentials
MinIO secret
JWT signing secret
private API key
```

Frontend에서 secret이 필요한 구조라면 설계 자체를 재검토한다.

Browser는 secret 보관 장소가 아니다.

---

# 25. Frontend Security

사용자 입력을 DOM에 삽입할 때 XSS 가능성을 고려한다.

`dangerouslySetInnerHTML`을 특별한 이유 없이 사용하지 않는다.

외부 URL, redirect, file input 등을 다룰 때 신뢰 경계를 고려한다.

Frontend 검증만으로 보안을 보장한다고 가정하지 않는다.

---

# 26. Docker

전체 Frontend를 포함한 Full Stack Docker 실행 책임은 Repository Root의:

```text
../docker-compose.yml
```

에 있다.

Backend의:

```text
../backend/docker-compose.yml
```

은 Backend Infrastructure 개발용이므로 Frontend Application을 추가하지 않는다.

Compose 책임 경계를 유지한다.

---

# 27. Frontend Testing

현재 test infrastructure가 있다면 기존 방식을 따른다.

Task 하나 때문에 새로운 test framework를 임의로 추가하지 않는다.

중요한 behavior를 우선 검증한다.

```text
Loading
Error
Empty
Form Validation
Upload
Reorder
Critical Interaction
```

구현 세부사항보다 사용자 관점 behavior를 테스트한다.

---

# 28. Frontend Verification

Frontend 변경 후 현재 구성에 존재하는 명령을 확인하여 관련:

```text
type check
lint
test
build
```

를 실행한다.

작은 Component 변경마다 monorepo 전체를 무조건 build하지 않는다.

Shared package/API contract/configuration 변경 등 영향 범위가 크면 검증 범위를 확대한다.

---

# 29. Frontend Self Review

완료 전 확인:

```text
[ ] Admin/User 책임이 섞였는가?
[ ] Backend Business Rule을 복제했는가?
[ ] Component가 여러 책임을 가지는가?
[ ] API 호출이 중복되었는가?
[ ] any/type assertion으로 문제를 숨겼는가?
[ ] Loading/Error/Empty 처리가 필요한가?
[ ] undefined data로 crash 가능한가?
[ ] Artwork 비율을 왜곡하는가?
[ ] MinIO URL을 직접 조립하는가?
[ ] Client/Server boundary가 적절한가?
[ ] Secret이 Browser에 노출되는가?
[ ] NEXT_PUBLIC_*에 secret이 있는가?
[ ] 불필요한 library를 추가했는가?
[ ] Shared package를 너무 일찍 만들었는가?
```
