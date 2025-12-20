# Phase 4.13: Passive Authentication UI Implementation

**Status**: 📋 PLANNED
**Start Date**: TBD
**Target Completion**: TBD

---

## 🎯 Overview

Phase 4.13 implements the **web UI for Passive Authentication**, allowing users to:
1. Upload ePassport SOD and Data Groups
2. Perform PA verification
3. View verification results
4. Browse verification history
5. View PA statistics dashboard

---

## 📋 Task Breakdown

### Task 1: PA Verification Screen (전자여권 판독 & PA 수행)

**Goal**: Create UI for uploading passport data and performing PA verification

**Sub-tasks**:
1. ✅ **Backend API** (Already implemented in Phase 4.6)
   - POST `/api/pa/verify` - Perform PA verification
   - Returns: PassiveAuthenticationResponse with detailed results

2. ⏳ **Frontend Page**: `/pa/verify`
   - **SOD Input Section**
     - File upload (binary .bin file)
     - OR Base64 text input (textarea)
     - File size limit: 50KB
     - Format validation (ICAO 9303 Tag 0x77)

   - **Data Group Input Section**
     - Dynamic form with add/remove buttons
     - Each entry:
       - Data Group Number (dropdown: DG1-DG16)
       - Data Group Content (Base64 textarea)
     - Minimum: DG1 (MRZ) required
     - Validation: Base64 format check

   - **Verification Button**
     - "Perform Passive Authentication" button
     - Loading spinner during API call
     - Disable button while processing

   - **Result Display Section**
     - Success/Failure badge (green/red)
     - Overall Status: VALID / INVALID / ERROR
     - Verification Details:
       - ✅/❌ Trust Chain Validation
       - ✅/❌ SOD Signature Verification
       - ✅/❌ Data Group Hash Verification
       - ✅/❌ CRL Check (with revocation details if applicable)
     - Error Messages (if any)
     - Passport Metadata:
       - Country Code
       - DSC Serial Number
       - CSCA Issuer
       - Verification Timestamp

   - **Export Options**
     - Download as JSON
     - Download as PDF report

3. ⏳ **UI Components** (DaisyUI + Alpine.js)
   - Form validation
   - File upload handling
   - Base64 encoding/decoding
   - API integration (fetch)
   - Result visualization
   - Error handling

**Files to Create**:
- `src/main/resources/templates/pa/verify.html`
- `src/main/resources/static/js/pa-verify.js` (optional, if needed)

**Estimated LOC**: ~500 lines (HTML + Alpine.js)

---

### Task 2: PA History Screen (PA 수행 이력)

**Goal**: Display list of past PA verification attempts

**Sub-tasks**:
1. ⏳ **Backend API Enhancement**
   - GET `/api/pa/history?page=0&size=20` - List with pagination
   - GET `/api/pa/history/{id}` - Detailed view (already implemented)
   - Query params:
     - `page`, `size` - Pagination
     - `status` - Filter by VALID/INVALID/ERROR
     - `countryCode` - Filter by country
     - `startDate`, `endDate` - Filter by date range

2. ⏳ **History List Page**: `/pa/history`
   - **Filter Section**
     - Status dropdown (All / Valid / Invalid / Error)
     - Country code input (2-letter ISO code)
     - Date range picker (from, to)
     - Search button

   - **History Table**
     - Columns:
       - Verification ID (clickable to detail)
       - Country Code
       - Status Badge (✅ VALID / ❌ INVALID / ⚠️ ERROR)
       - Verification Date
       - Client IP
       - Actions (View Details button)
     - Pagination controls (page numbers, prev/next)
     - Rows per page selector (10, 20, 50)

   - **Detail Modal/Page**
     - Full verification results
     - All verification steps with status
     - Error messages (if any)
     - Passport metadata
     - Client information (IP, User-Agent)

3. ⏳ **UI Components**
   - Table with sorting
   - Pagination component
   - Filter form
   - Modal/detail view
   - Date picker (optional, can use HTML5 date input)

**Files to Create**:
- `src/main/resources/templates/pa/history.html`
- Backend: Enhance `PassiveAuthenticationController` for history endpoint

**Estimated LOC**:
- Frontend: ~400 lines (HTML + Alpine.js)
- Backend: ~150 lines (Controller + Query enhancements)

---

### Task 3: PA Statistics Dashboard (PA 통계)

**Goal**: Visualize PA verification statistics

**Sub-tasks**:
1. ⏳ **Backend API**
   - GET `/api/pa/statistics/summary` - Overall stats
     - Total verifications
     - Success rate
     - Country breakdown (top 10)

   - GET `/api/pa/statistics/daily?days=30` - Daily stats
     - Date, total, success, failure counts

   - GET `/api/pa/statistics/by-country?top=10` - Country stats
     - Country code, total, success, failure counts

2. ⏳ **Dashboard Page**: `/pa/dashboard`
   - **Summary Cards** (DaisyUI stats component)
     - Total Verifications (count)
     - Success Rate (percentage)
     - Verified Countries (count)
     - Avg Response Time (ms)

   - **Daily Verification Chart**
     - Line chart (Chart.js or similar)
     - X-axis: Date (last 30 days)
     - Y-axis: Count
     - Series: Success (green), Failure (red)

   - **Country Breakdown Chart**
     - Bar chart (horizontal)
     - Top 10 countries by verification count
     - Color-coded by success rate

   - **Status Distribution**
     - Pie/Doughnut chart
     - Segments: Valid, Invalid, Error

   - **Recent Verifications Table**
     - Last 10 verifications
     - Quick summary view
     - Link to full history

3. ⏳ **Charts & Visualization**
   - Choose charting library:
     - Option 1: Chart.js (recommended, 64KB)
     - Option 2: ApexCharts (feature-rich, 400KB)
     - Option 3: Recharts (React-based, skip if using Alpine.js)
   - Responsive design
   - Color scheme matching DaisyUI theme

**Files to Create**:
- `src/main/resources/templates/pa/dashboard.html`
- Backend: `PaStatisticsController.java` (new controller)
- Backend: `PaStatisticsService.java` (new service)
- Backend: `PassiveAuthenticationAuditLogRepository` enhancements (aggregation queries)

**Estimated LOC**:
- Frontend: ~600 lines (HTML + Alpine.js + Chart.js)
- Backend: ~300 lines (Controller + Service + Repository queries)

---

### Task 4: Navigation & Integration

**Goal**: Integrate PA pages into main navigation

**Sub-tasks**:
1. ⏳ **Update Main Navigation**
   - Add "Passive Authentication" menu item
   - Sub-menu:
     - Verify Passport
     - Verification History
     - Statistics Dashboard

2. ⏳ **Update Dashboard (index.html)**
   - Add PA statistics card
   - Quick link to PA verification
   - Recent PA verifications widget

3. ⏳ **Breadcrumbs**
   - Add breadcrumbs to PA pages
   - Home > Passive Authentication > [Current Page]

**Files to Modify**:
- `src/main/resources/templates/layout/_nav.html`
- `src/main/resources/templates/index.html`
- `src/main/resources/templates/fragments/*.html` (if needed)

**Estimated LOC**: ~100 lines (modifications)

---

### Task 5: Testing & Documentation

**Goal**: Ensure quality and document the feature

**Sub-tasks**:
1. ⏳ **E2E Testing**
   - Test PA verification flow
   - Test history filtering/pagination
   - Test dashboard charts
   - Test with various passport data (valid/invalid)

2. ⏳ **Controller Tests** (Optional, API already tested in Phase 4.6)
   - Test new history endpoints
   - Test statistics endpoints

3. ⏳ **Documentation**
   - User guide for PA verification
   - Screenshot examples
   - Update CLAUDE.md

**Files to Create/Update**:
- `docs/PA_UI_USER_GUIDE.md`
- `docs/PA_UI_SCREENSHOTS/` (directory with screenshots)
- Update `CLAUDE.md` with Phase 4.13 completion

**Estimated LOC**: Documentation (~500 lines)

---

## 📊 Summary

| Task | Estimated LOC | Complexity | Priority |
|------|---------------|------------|----------|
| Task 1: Verify Screen | 500 | Medium | HIGH |
| Task 2: History Screen | 550 | Medium | HIGH |
| Task 3: Dashboard | 900 | High | MEDIUM |
| Task 4: Navigation | 100 | Low | HIGH |
| Task 5: Testing & Docs | - | Medium | MEDIUM |

**Total Estimated LOC**: ~2,050 lines (Frontend + Backend)

**Estimated Effort**: 3-4 days (assuming 1 developer)

---

## 🛠️ Technical Stack

### Frontend
- **Templating**: Thymeleaf
- **CSS Framework**: DaisyUI 5.0 (Tailwind CSS)
- **JavaScript**: Alpine.js 3.14.8
- **AJAX**: Native Fetch API
- **Charts**: Chart.js 4.x (recommended)

### Backend
- **Framework**: Spring Boot 3.5.5
- **Web**: Spring MVC
- **Validation**: Bean Validation (already implemented)
- **API**: REST (JSON responses)

---

## 📝 Design Decisions

### 1. SOD Input Method
**Options**:
- A) File upload only
- B) Base64 text input only
- C) Both (recommended)

**Decision**: **Option C** - Support both methods for flexibility
- Users can upload `.bin` files from passport readers
- OR paste Base64 from other sources (mobile apps, APIs)

---

### 2. Data Group Input
**Options**:
- A) Individual textareas for each DG (DG1-DG16)
- B) Dynamic add/remove form
- C) JSON editor

**Decision**: **Option B** - Dynamic form with add/remove buttons
- Most flexible (only input required DGs)
- User-friendly (no JSON knowledge required)
- Clear UI (each DG has its own card)

---

### 3. Result Display
**Options**:
- A) Simple success/failure message
- B) Detailed step-by-step results (recommended)
- C) JSON raw output

**Decision**: **Option B** - Detailed results with visual indicators
- Shows which step failed (if any)
- Easy to understand for non-technical users
- Includes all verification details

---

### 4. History Storage
**Current**: Already persists to `passive_authentication_audit_log` table
**Decision**: Reuse existing audit log table
- No schema changes needed
- Full history already available via `/api/pa/history/{id}`

---

### 5. Statistics Data Source
**Options**:
- A) Real-time aggregation queries
- B) Pre-computed statistics table
- C) Caching with scheduled updates

**Decision**: **Option A** - Real-time queries for Phase 4.13
- Simpler implementation
- No additional tables/jobs
- Performance sufficient for initial release
- Consider Option C if performance becomes an issue

---

## 🎨 UI/UX Design Guidelines

### 1. Color Scheme (DaisyUI)
- **Success**: `success` class (green) - Valid verifications
- **Error**: `error` class (red) - Invalid verifications
- **Warning**: `warning` class (yellow) - Errors/Warnings
- **Info**: `info` class (blue) - Informational messages

### 2. Layout
- **Verify Page**: Single column, centered (max-width: 1024px)
- **History Page**: Full width table with filters
- **Dashboard**: Grid layout (2-3 columns on desktop, 1 column on mobile)

### 3. Responsiveness
- Mobile-first design
- Tables scroll horizontally on mobile
- Charts resize for smaller screens

### 4. Accessibility
- Semantic HTML5
- ARIA labels for screen readers
- Keyboard navigation support
- Color contrast compliance (WCAG AA)

---

## 🔄 Integration with Existing Code

### Reuse Existing Components
1. **Layout Template**: `layout/main.html`
2. **Navigation**: `layout/_nav.html`
3. **Footer**: `layout/_footer.html`
4. **Modals**: `layout/_modals.html`

### API Endpoints (Already Implemented)
- ✅ `POST /api/pa/verify` - Verification endpoint
- ✅ `GET /api/pa/history/{id}` - Detail endpoint
- ⏳ `GET /api/pa/history` - List endpoint (needs pagination)
- ⏳ `GET /api/pa/statistics/*` - Statistics endpoints (new)

---

## 📚 References

### Frontend Technologies
- [DaisyUI Components](https://daisyui.com/components/)
- [Alpine.js Documentation](https://alpinejs.dev/)
- [Chart.js Documentation](https://www.chartjs.org/docs/)
- [Thymeleaf Documentation](https://www.thymeleaf.org/documentation.html)

### Backend Standards
- [ICAO Doc 9303 Part 11](https://www.icao.int/publications/Documents/9303_p11_cons_en.pdf) - PA Standards
- [RFC 5280](https://datatracker.ietf.org/doc/html/rfc5280) - X.509 CRL Profile
- Phase 4.6 Controller Implementation

---

## ✅ Acceptance Criteria

### Must Have (Phase 4.13)
- ✅ Users can upload SOD and Data Groups
- ✅ PA verification executes and displays results
- ✅ Results show detailed step-by-step status
- ✅ History page shows past verifications
- ✅ History can be filtered and paginated
- ✅ Dashboard displays basic statistics
- ✅ All pages are responsive (mobile/desktop)
- ✅ Error handling for invalid inputs

### Nice to Have (Future Enhancements)
- 📋 Export results as PDF report
- 📋 Real-time verification status (WebSocket/SSE)
- 📋 Batch verification (multiple passports)
- 📋 Advanced statistics (trends, anomaly detection)
- 📋 User authentication (currently public API)

---

## 🚀 Next Steps

1. **Confirm Design** with stakeholders
2. **Start with Task 1** (Verify Screen) - Most critical
3. **Iterate** based on user feedback
4. **Add Task 2** (History Screen)
5. **Complete with Task 3** (Dashboard)

---

**Phase 4.13 Status**: 📋 PLANNED
**Document Version**: 1.0
**Last Updated**: 2025-12-19
