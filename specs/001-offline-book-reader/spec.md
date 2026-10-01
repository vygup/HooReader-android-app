# Feature Specification: Offline Book Reader

**Feature Branch**: `001-offline-book-reader`

**Created**: 2026-10-01

**Status**: Draft

**Input**: User description: "Offline reading of imported EPUB and FB2 books with reliable position restoration."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Import and Resume a Book (Priority: P1)

A reader imports a valid EPUB or FB2 file from the device, opens it, reads part of it, closes the
app, and later resumes at practically the same place without an internet connection.

**Why this priority**: Reliable offline reading and position restoration are the defining MVP value.

**Independent Test**: Import one valid book, read into a later chapter, terminate and relaunch the
app in airplane mode, then reopen that book and verify restoration within one paragraph.

**Acceptance Scenarios**:

1. **Given** a valid EPUB or FB2 selected through the device file picker, **When** import finishes,
   **Then** the book appears in the local library with available metadata and can be opened offline.
2. **Given** a reader has reached a paragraph in a book, **When** the app is closed or its process
   is ended and the book is reopened, **Then** reading resumes at that paragraph or an adjacent one.
3. **Given** an imported book, **When** its original source file is moved or deleted, **Then** the
   imported copy remains readable offline.

---

### User Story 2 - Browse and Manage Local Library (Priority: P1)

A reader sees imported books, identifies each by its cover, title, author, and approximate
progress, and can remove a book from the app library without deleting the source file.

**Why this priority**: A usable local library is required to find and continue reading books.

**Independent Test**: Import books with complete and missing metadata, review a library of 100
books, remove one, and verify both the displayed information and source-file behavior.

**Acceptance Scenarios**:

1. **Given** a book with absent metadata or cover, **When** it appears in the library, **Then** the
   filename is used as the title, "Неизвестный автор" as the author, and a standard placeholder as
   the cover.
2. **Given** an already imported book, **When** the same book is imported again, **Then** the
   library does not silently create an additional identical entry.
3. **Given** a library entry, **When** the reader deletes it from the app, **Then** its local app
   data is removed and the original selected file is not deleted.

---

### User Story 3 - Navigate and Read Structured Content (Priority: P2)

A reader scrolls a book vertically, reads supported text and media, uses its table of contents
when present, and changes text size without losing the current logical reading location.

**Why this priority**: Comfortable navigation makes imported books usable beyond the opening page.

**Independent Test**: Open a structured EPUB and FB2 with headings, text styling, lists, images,
and chapters; jump through the table of contents; change text size; and verify the nearby position.

**Acceptance Scenarios**:

1. **Given** a book with a table of contents, **When** the reader selects a chapter, **Then** the
   reader opens at the start of that chapter.
2. **Given** a reader is at a saved location, **When** text size, orientation, or theme changes,
   **Then** the reader remains at that logical location or its nearest available equivalent.
3. **Given** unsupported markup or an unreadable embedded image, **When** the book is opened,
   **Then** unaffected content remains readable and the app does not terminate unexpectedly.

---

### User Story 4 - Personalize Appearance (Priority: P3)

A reader chooses a light or dark reading theme and a comfortable text size; the theme choice is
retained on later launches.

**Why this priority**: Personalization improves reading comfort but does not replace core reading.

**Independent Test**: Change theme and font size, relaunch the app, and verify that the selected
theme persists and the book can still be resumed near the prior location.

**Acceptance Scenarios**:

1. **Given** the reader changes between light and dark themes, **When** the app is relaunched,
   **Then** the chosen theme remains selected.

### Edge Cases

- A selected unsupported file, empty file, or malformed EPUB/FB2 is rejected with a clear message,
  is not added to the library, and does not terminate the app.
- A malformed container, missing book package information, malformed XML, or declared valid FB2
  character encoding is handled safely; valid declared encodings preserve readable Unicode text.
- A book with unavailable title, author, cover, chapter information, or a damaged embedded image
  remains available with the applicable fallback.
- Progress survives foreground/background transitions, screen rotation, and system process
  termination; a never-opened book starts at the beginning.
- Large books are handled without avoidable full-book memory loading and remain usable.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST let users select EPUB and FB2 files using the standard device file
  picker.
- **FR-002**: The system MUST validate the selected file, identify its supported format, and reject
  unsupported, empty, or unreadable files with a clear message and no library entry.
- **FR-003**: The system MUST retain an app-local readable copy after successful import so a book
  remains available offline if its original file is moved or deleted.
- **FR-004**: The system MUST extract available title, author, and cover information on import and
  apply the specified title, author, and cover fallbacks when any are unavailable.
- **FR-005**: The system MUST prevent silent duplicate entries when the same book is imported again
  and MUST inform the user whether the existing entry was retained.
- **FR-006**: The library MUST show each imported book's cover or placeholder, title, author, and
  approximate reading progress, and remain convenient to browse with at least 100 books.
- **FR-007**: The reader MUST render EPUB and FB2 content in vertically scrollable form, including
  paragraphs, headings, bold and italic text, basic lists, images, and available chapter structure.
- **FR-008**: The system MUST tolerate unsupported or malformed in-book markup and individual
  unreadable images without terminating the app or blocking unaffected content.
- **FR-009**: The system MUST save a logical reading position during reading, chapter changes,
  reader exit, app backgrounding, and lifecycle interruption. The position MUST identify a chapter,
  paragraph, and offset or the nearest equivalent position.
- **FR-010**: The system MUST open an unread book at its beginning and a previously read book at the
  saved logical position or its nearest available equivalent.
- **FR-011**: The system MUST calculate and display approximate reading progress in the library,
  display "Прочитано" at approximately 98% or more, and update it as reading advances.
- **FR-012**: When chapter information exists, the system MUST provide a table of contents and let
  the reader open the start of a selected chapter.
- **FR-013**: The system MUST let the reader change text size and choose light or dark themes. Theme
  selection MUST persist across relaunches; text-size, theme, and orientation changes MUST preserve
  the logical reading location.
- **FR-014**: The system MUST let the reader remove an imported book and its app-local data from the
  library without automatically deleting the original user-selected file.
- **FR-015**: All imported books, library data, preferences, and recently saved reading positions
  MUST remain usable without an internet connection.
- **FR-016**: The system MUST avoid avoidable full-book memory loading while reading large books.

### Key Entities

- **Book**: An imported local work with format, retained copy, identity for duplicate detection,
  display metadata, cover state, and reading progress.
- **Reading Position**: A durable logical location consisting of chapter, paragraph, and in-paragraph
  offset, with an approximate percentage for library display.
- **Chapter**: A navigable section of a book with title, order, and starting logical location.
- **Reader Preferences**: Persisted appearance choices, including theme and text size.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A reader can import a valid EPUB or FB2, find it in the library, open it, close the
  app, and resume within one paragraph of the previous location in 100% of the defined acceptance
  test runs.
- **SC-002**: Imported books remain readable and retain progress in airplane mode in 100% of the
  defined offline acceptance test runs.
- **SC-003**: At least 99% of valid EPUB and FB2 files in the agreed test corpus import and open
  successfully.
- **SC-004**: A typical book no larger than 20 MB opens within 2 seconds on a contemporary
  mid-range Android device.
- **SC-005**: The library remains usable with 100 imported books, and ordinary reading scrolls
  without user-visible stutter during acceptance testing.
- **SC-006**: The app completes import, reading, theme changes, text-size changes, and relaunch
  scenarios without unexpected termination in the defined acceptance test runs.

## Assumptions

- The MVP targets one local user per device and does not include accounts, cloud synchronization, or
  sharing of reading progress between devices.
- EPUB and FB2 are the only import formats in scope; PDF, DJVU, MOBI, AZW3, online catalogs, and
  book-store integrations are excluded.
- Duplicate detection uses a stable identity derived from imported content; the app may show the
  existing book instead of creating a second entry.
- "Approximately the same place" means the saved paragraph or the nearest available paragraph, with
  a maximum restoration difference of one paragraph under normal content changes caused by layout.
- The test corpus represents supported, legally obtained books and includes files with Cyrillic,
  Latin, diacritics, typographic punctuation, valid declared encodings, and damaged-content cases.
