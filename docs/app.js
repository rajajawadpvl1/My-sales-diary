(() => {
  'use strict';

  const DB_NAME = 'MySalesDiaryDB';
  const DB_VERSION = 1;
  const STORE_LEADS = 'leads';
  const STORE_COURSES = 'courses';

  const CALL_STATUSES = [
    { key: 'INTERESTED', label: 'Interested', fg: '#13734A', bg: '#E8F6EF', autoTwoDays: true },
    { key: 'NOT_INTERESTED', label: 'Not Interested', fg: '#C23535', bg: '#FDECEC', closesReminder: true },
    { key: 'NOT_CONNECTED', label: 'Not Connected', fg: '#475569', bg: '#F0F4F8', optionalRetry: true },
    { key: 'CALL_BACK', label: 'Call Back', fg: '#C96214', bg: '#FFF2E6', requiresCustom: true },
    { key: 'BUSY', label: 'Busy', fg: '#9C6615', bg: '#FCF5E6', optionalRetry: true },
    { key: 'SWITCHED_OFF', label: 'Switched Off', fg: '#564B9E', bg: '#F1EFFB' },
    { key: 'WRONG_NUMBER', label: 'Wrong Number', fg: '#AD2E4B', bg: '#FCEBEF', closesReminder: true },
    { key: 'ENROLLED', label: 'Enrolled', fg: '#0C625B', bg: '#E2F4F2', closesReminder: true }
  ];

  const STATUS_MAP = Object.fromEntries(CALL_STATUSES.map((s) => [s.key, s]));

  // ---------------- Timezone-Safe Local Date Utilities ----------------

  const ISO_RE = /^\d{4}-\d{2}-\d{2}$/;
  const TIME_RE = /^\d{2}:\d{2}$/;

  function pad2(n) {
    return String(n).padStart(2, '0');
  }

  function todayIso() {
    const now = new Date();
    return `${now.getFullYear()}-${pad2(now.getMonth() + 1)}-${pad2(now.getDate())}`;
  }

  function isValidIsoDate(iso) {
    if (!iso || !ISO_RE.test(iso)) return false;
    const [y, m, d] = iso.split('-').map(Number);
    const dt = new Date(y, m - 1, d, 12, 0, 0);
    return dt.getFullYear() === y && dt.getMonth() === m - 1 && dt.getDate() === d;
  }

  function isValidTime(hhmm) {
    if (!hhmm || !TIME_RE.test(hhmm)) return false;
    const [h, m] = hhmm.split(':').map(Number);
    return h >= 0 && h <= 23 && m >= 0 && m <= 59;
  }

  function addDays(iso, days) {
    const base = isValidIsoDate(iso) ? iso : todayIso();
    const [y, m, d] = base.split('-').map(Number);
    const dt = new Date(y, m - 1, d, 12, 0, 0);
    dt.setDate(dt.getDate() + days);
    return `${dt.getFullYear()}-${pad2(dt.getMonth() + 1)}-${pad2(dt.getDate())}`;
  }

  function daysBetween(fromIso, toIso) {
    if (!isValidIsoDate(fromIso) || !isValidIsoDate(toIso)) return 0;
    const [y1, m1, d1] = fromIso.split('-').map(Number);
    const [y2, m2, d2] = toIso.split('-').map(Number);
    const t1 = Date.UTC(y1, m1 - 1, d1, 12, 0, 0);
    const t2 = Date.UTC(y2, m2 - 1, d2, 12, 0, 0);
    return Math.round((t2 - t1) / 86400000);
  }

  function formatDisplayDate(iso) {
    if (!isValidIsoDate(iso)) return iso || '';
    const [y, m, d] = iso.split('-').map(Number);
    return new Date(y, m - 1, d, 12, 0, 0).toLocaleDateString(undefined, {
      month: 'short',
      day: 'numeric',
      year: 'numeric'
    });
  }

  function formatShortDate(iso) {
    if (!isValidIsoDate(iso)) return iso || '';
    const [y, m, d] = iso.split('-').map(Number);
    return new Date(y, m - 1, d, 12, 0, 0).toLocaleDateString(undefined, {
      month: 'short',
      day: 'numeric'
    });
  }

  function formatRelativeDate(iso, today) {
    const delta = daysBetween(today, iso);
    if (delta === 0) return 'Today';
    if (delta === 1) return 'Tomorrow';
    if (delta === -1) return '1 day overdue';
    if (delta < -1) return `${-delta} days overdue`;
    if (delta >= 2 && delta <= 6) return `In ${delta} days (${formatShortDate(iso)})`;
    return formatDisplayDate(iso);
  }

  function formatTimeDisplay(hhmm) {
    if (!isValidTime(hhmm)) return '';
    const [h24, m] = hhmm.split(':').map(Number);
    const suffix = h24 >= 12 ? 'PM' : 'AM';
    const h12 = h24 === 0 ? 12 : h24 > 12 ? h24 - 12 : h24;
    return `${h12}:${pad2(m)} ${suffix}`;
  }

  function normalizePhone(raw) {
    const trimmed = String(raw || '').trim();
    const hasPlus = trimmed.startsWith('+');
    const digits = trimmed.replace(/\D/g, '');
    return hasPlus && digits ? `+${digits}` : digits;
  }

  function resolveStatusKey(raw) {
    const clean = String(raw || '').trim().toLowerCase();
    const match = CALL_STATUSES.find(
      (s) => s.key.toLowerCase() === clean || s.label.toLowerCase() === clean
    );
    return match ? match.key : 'INTERESTED';
  }

  function computeFollowUp(statusKey, callDate, customDate, customTime, scheduleRetry) {
    const safeTime = isValidTime(customTime) ? customTime : null;
    if (statusKey === 'INTERESTED') {
      return { followUpDate: addDays(callDate, 2), followUpTime: null };
    }
    if (statusKey === 'CALL_BACK') {
      const date = isValidIsoDate(customDate) ? customDate : addDays(callDate, 1);
      return { followUpDate: date, followUpTime: safeTime };
    }
    if (statusKey === 'NOT_CONNECTED' || statusKey === 'BUSY') {
      if (scheduleRetry && isValidIsoDate(customDate)) {
        return { followUpDate: customDate, followUpTime: safeTime };
      }
      return { followUpDate: null, followUpTime: null };
    }
    return { followUpDate: null, followUpTime: null };
  }

  function hasActiveFollowUp(lead) {
    const meta = STATUS_MAP[lead.status] || STATUS_MAP.INTERESTED;
    return Boolean(lead.followUpDate && isValidIsoDate(lead.followUpDate) && !meta.closesReminder);
  }

  function escapeHtml(str) {
    return String(str ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  // ---------------- IndexedDB Storage Layer ----------------

  function openDb() {
    return new Promise((resolve, reject) => {
      const req = indexedDB.open(DB_NAME, DB_VERSION);
      req.onupgradeneeded = (event) => {
        const db = event.target.result;
        if (!db.objectStoreNames.contains(STORE_LEADS)) {
          const leadStore = db.createObjectStore(STORE_LEADS, { keyPath: 'id', autoIncrement: true });
          leadStore.createIndex('normalizedPhone', 'normalizedPhone', { unique: false });
          leadStore.createIndex('followUpDate', 'followUpDate', { unique: false });
          leadStore.createIndex('status', 'status', { unique: false });
        }
        if (!db.objectStoreNames.contains(STORE_COURSES)) {
          const courseStore = db.createObjectStore(STORE_COURSES, { keyPath: 'id', autoIncrement: true });
          courseStore.createIndex('nameLower', 'nameLower', { unique: true });
        }
      };
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
  }

  function txPromise(db, storeNames, mode, fn) {
    return new Promise((resolve, reject) => {
      const tx = db.transaction(storeNames, mode);
      let result;
      tx.oncomplete = () => resolve(result);
      tx.onerror = () => reject(tx.error);
      tx.onabort = () => reject(tx.error || new Error('Transaction aborted'));
      Promise.resolve(fn(tx)).then((val) => { result = val; }).catch((err) => {
        try { tx.abort(); } catch (_) {}
        reject(err);
      });
    });
  }

  function reqToPromise(req) {
    return new Promise((resolve, reject) => {
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
  }

  const dbApi = {
    db: null,
    async init() {
      this.db = await openDb();
    },
    async getAllLeads() {
      const list = await txPromise(this.db, [STORE_LEADS], 'readonly', (tx) =>
        reqToPromise(tx.objectStore(STORE_LEADS).getAll())
      );
      return list.sort((a, b) => (b.updatedAt - a.updatedAt) || (b.id - a.id));
    },
    async getAllCourses() {
      const list = await txPromise(this.db, [STORE_COURSES], 'readonly', (tx) =>
        reqToPromise(tx.objectStore(STORE_COURSES).getAll())
      );
      return list.sort((a, b) => a.name.localeCompare(b.name, undefined, { sensitivity: 'base' }));
    },
    async addCourse(name, price = '') {
      const cleanName = name.trim();
      const cleanPrice = price.trim();
      if (!cleanName) return false;
      const existing = await this.getAllCourses();
      if (existing.some((c) => c.name.toLowerCase() === cleanName.toLowerCase())) {
        return false;
      }
      await txPromise(this.db, [STORE_COURSES], 'readwrite', (tx) =>
        reqToPromise(tx.objectStore(STORE_COURSES).add({
          name: cleanName,
          nameLower: cleanName.toLowerCase(),
          price: cleanPrice
        }))
      );
      return true;
    },
    async updateCourse(id, newName, newPrice) {
      const cleanName = newName.trim();
      const cleanPrice = newPrice.trim();
      if (!cleanName) return false;
      const allCourses = await this.getAllCourses();
      const current = allCourses.find((c) => c.id === id);
      if (!current) return false;
      if (allCourses.some((c) => c.id !== id && c.name.toLowerCase() === cleanName.toLowerCase())) {
        return false;
      }
      await txPromise(this.db, [STORE_COURSES, STORE_LEADS], 'readwrite', async (tx) => {
        const courseStore = tx.objectStore(STORE_COURSES);
        const leadStore = tx.objectStore(STORE_LEADS);
        await reqToPromise(courseStore.put({
          id,
          name: cleanName,
          nameLower: cleanName.toLowerCase(),
          price: cleanPrice
        }));
        if (current.name !== cleanName) {
          const allLeads = await reqToPromise(leadStore.getAll());
          for (const lead of allLeads) {
            if (lead.course === current.name) {
              await reqToPromise(leadStore.put({ ...lead, course: cleanName }));
            }
          }
        }
      });
      return true;
    },
    async deleteCourse(id) {
      await txPromise(this.db, [STORE_COURSES], 'readwrite', (tx) =>
        reqToPromise(tx.objectStore(STORE_COURSES).delete(id))
      );
    },
    async saveLead(payload) {
      const cleanPhone = payload.phoneNumber.trim();
      const normalized = normalizePhone(cleanPhone);
      const safeCallDate = isValidIsoDate(payload.callDate) ? payload.callDate : todayIso();
      const cleanCourse = (payload.course || '').trim();
      const statusKey = resolveStatusKey(payload.status);
      const now = Date.now();

      if (cleanCourse) {
        await this.addCourse(cleanCourse, '');
      }

      const { followUpDate, followUpTime } = computeFollowUp(
        statusKey,
        safeCallDate,
        payload.customFollowUpDate,
        payload.customFollowUpTime,
        payload.scheduleRetry
      );

      const allLeads = await this.getAllLeads();

      if (payload.editingLeadId != null) {
        const existing = allLeads.find((l) => l.id === payload.editingLeadId);
        const updated = {
          id: payload.editingLeadId,
          studentName: (payload.studentName || '').trim(),
          phoneNumber: cleanPhone,
          normalizedPhone: normalized,
          course: cleanCourse,
          callDate: safeCallDate,
          status: statusKey,
          notes: (payload.notes || '').trim(),
          followUpDate,
          followUpTime,
          createdAt: existing ? existing.createdAt : now,
          updatedAt: now
        };
        await txPromise(this.db, [STORE_LEADS], 'readwrite', (tx) =>
          reqToPromise(tx.objectStore(STORE_LEADS).put(updated))
        );
        return updated;
      }

      // Deduplicate active follow-ups for the same student phone
      if (followUpDate) {
        const existingActive = allLeads.find(
          (l) => l.normalizedPhone === normalized && hasActiveFollowUp(l)
        );
        if (existingActive) {
          const merged = {
            ...existingActive,
            studentName: (payload.studentName || '').trim() || existingActive.studentName,
            phoneNumber: cleanPhone,
            course: cleanCourse || existingActive.course,
            callDate: safeCallDate,
            status: statusKey,
            notes: (payload.notes || '').trim() || existingActive.notes,
            followUpDate,
            followUpTime,
            updatedAt: now
          };
          await txPromise(this.db, [STORE_LEADS], 'readwrite', (tx) =>
            reqToPromise(tx.objectStore(STORE_LEADS).put(merged))
          );
          return merged;
        }
      }

      const record = {
        studentName: (payload.studentName || '').trim(),
        phoneNumber: cleanPhone,
        normalizedPhone: normalized,
        course: cleanCourse,
        callDate: safeCallDate,
        status: statusKey,
        notes: (payload.notes || '').trim(),
        followUpDate,
        followUpTime,
        createdAt: now,
        updatedAt: now
      };
      const newId = await txPromise(this.db, [STORE_LEADS], 'readwrite', (tx) =>
        reqToPromise(tx.objectStore(STORE_LEADS).add(record))
      );
      return { ...record, id: newId };
    },
    async applyAfterCallAction(leadId, newStatusKey, customDate = null, customTime = null, updatedNote = null) {
      const leads = await this.getAllLeads();
      const existing = leads.find((l) => l.id === leadId);
      if (!existing) return null;
      const today = todayIso();
      const now = Date.now();
      const isRetry = newStatusKey === 'NOT_CONNECTED' || newStatusKey === 'BUSY';
      const { followUpDate, followUpTime } = computeFollowUp(
        newStatusKey,
        today,
        customDate,
        customTime,
        isRetry && Boolean(customDate)
      );
      const finalNotes = updatedNote !== null && updatedNote.trim() !== ''
        ? updatedNote.trim()
        : existing.notes;
      const updated = {
        ...existing,
        callDate: today,
        status: newStatusKey,
        notes: finalNotes,
        followUpDate,
        followUpTime,
        updatedAt: now
      };
      await txPromise(this.db, [STORE_LEADS], 'readwrite', (tx) =>
        reqToPromise(tx.objectStore(STORE_LEADS).put(updated))
      );
      return updated;
    },
    async deleteLead(id) {
      await txPromise(this.db, [STORE_LEADS], 'readwrite', (tx) =>
        reqToPromise(tx.objectStore(STORE_LEADS).delete(id))
      );
    },
    async replaceAll(leads, courses) {
      await txPromise(this.db, [STORE_LEADS, STORE_COURSES], 'readwrite', async (tx) => {
        const leadStore = tx.objectStore(STORE_LEADS);
        const courseStore = tx.objectStore(STORE_COURSES);
        await reqToPromise(leadStore.clear());
        await reqToPromise(courseStore.clear());
        for (const lead of leads) {
          const { id, ...withoutId } = lead;
          await reqToPromise(leadStore.add(withoutId));
        }
        for (const course of courses) {
          await reqToPromise(courseStore.add({
            name: course.name,
            nameLower: course.name.toLowerCase(),
            price: course.price || ''
          }));
        }
      });
    }
  };

  // ---------------- Application State ----------------

  const state = {
    activeTab: 'notes',
    today: todayIso(),
    leads: [],
    courses: [],
    searchQuery: '',
    formExpanded: true,
    form: {
      editingLeadId: null,
      studentName: '',
      phoneNumber: '',
      course: '',
      callDate: todayIso(),
      status: 'INTERESTED',
      customFollowUpDate: addDays(todayIso(), 1),
      customFollowUpTime: '10:00',
      scheduleRetry: false,
      notes: ''
    },
    editingCourseId: null,
    rescheduleTarget: null,
    deleteLeadId: null,
    deferredInstallPrompt: null
  };

  // ---------------- DOM References ----------------

  const el = {
    installBtn: document.getElementById('installBtn'),
    backupToolsBtn: document.getElementById('backupToolsBtn'),
    statTotal: document.getElementById('statTotal'),
    statInterested: document.getElementById('statInterested'),
    statDueToday: document.getElementById('statDueToday'),
    statDueTodayCard: document.getElementById('statDueTodayCard'),
    statEnrolled: document.getElementById('statEnrolled'),
    notesView: document.getElementById('notesView'),
    remindersView: document.getElementById('remindersView'),
    leadFormCard: document.getElementById('leadFormCard'),
    formHeading: document.getElementById('formHeading'),
    formCallDateSubtitle: document.getElementById('formCallDateSubtitle'),
    callDateLabel: document.getElementById('callDateLabel'),
    inputCallDate: document.getElementById('inputCallDate'),
    toggleFormBtn: document.getElementById('toggleFormBtn'),
    toggleFormChevron: document.getElementById('toggleFormChevron'),
    leadForm: document.getElementById('leadForm'),
    inputPhone: document.getElementById('inputPhone'),
    phoneError: document.getElementById('phoneError'),
    inputStudentName: document.getElementById('inputStudentName'),
    inputCourse: document.getElementById('inputCourse'),
    manageCoursesInlineBtn: document.getElementById('manageCoursesInlineBtn'),
    selectedCoursePriceChip: document.getElementById('selectedCoursePriceChip'),
    statusChips: document.getElementById('statusChips'),
    followUpPanel: document.getElementById('followUpPanel'),
    inputNotes: document.getElementById('inputNotes'),
    cancelEditBtn: document.getElementById('cancelEditBtn'),
    saveLeadBtn: document.getElementById('saveLeadBtn'),
    leadCountBadge: document.getElementById('leadCountBadge'),
    searchInput: document.getElementById('searchInput'),
    clearSearchBtn: document.getElementById('clearSearchBtn'),
    leadsList: document.getElementById('leadsList'),
    remindersContainer: document.getElementById('remindersContainer'),
    fabAddLead: document.getElementById('fabAddLead'),
    tabNotesBtn: document.getElementById('tabNotesBtn'),
    tabRemindersBtn: document.getElementById('tabRemindersBtn'),
    remindersNavBadge: document.getElementById('remindersNavBadge'),
    backupSheetBackdrop: document.getElementById('backupSheetBackdrop'),
    exportCsvBtn: document.getElementById('exportCsvBtn'),
    exportCsvMeta: document.getElementById('exportCsvMeta'),
    exportJsonBtn: document.getElementById('exportJsonBtn'),
    restoreJsonBtn: document.getElementById('restoreJsonBtn'),
    restoreFileInput: document.getElementById('restoreFileInput'),
    sheetManageCoursesBtn: document.getElementById('sheetManageCoursesBtn'),
    closeBackupSheetBtn: document.getElementById('closeBackupSheetBtn'),
    coursesModalBackdrop: document.getElementById('coursesModalBackdrop'),
    coursesModalTitle: document.getElementById('coursesModalTitle'),
    coursesModalSubtitle: document.getElementById('coursesModalSubtitle'),
    courseForm: document.getElementById('courseForm'),
    courseNameInput: document.getElementById('courseNameInput'),
    coursePriceInput: document.getElementById('coursePriceInput'),
    cancelCourseEditBtn: document.getElementById('cancelCourseEditBtn'),
    saveCourseBtn: document.getElementById('saveCourseBtn'),
    coursesModalList: document.getElementById('coursesModalList'),
    closeCoursesModalBtn: document.getElementById('closeCoursesModalBtn'),
    rescheduleModalBackdrop: document.getElementById('rescheduleModalBackdrop'),
    rescheduleModalTitle: document.getElementById('rescheduleModalTitle'),
    rescheduleLeadMeta: document.getElementById('rescheduleLeadMeta'),
    rescheduleDateInput: document.getElementById('rescheduleDateInput'),
    rescheduleTimeInput: document.getElementById('rescheduleTimeInput'),
    rescheduleNoteInput: document.getElementById('rescheduleNoteInput'),
    cancelRescheduleBtn: document.getElementById('cancelRescheduleBtn'),
    confirmRescheduleBtn: document.getElementById('confirmRescheduleBtn'),
    deleteModalBackdrop: document.getElementById('deleteModalBackdrop'),
    deleteModalBody: document.getElementById('deleteModalBody'),
    cancelDeleteBtn: document.getElementById('cancelDeleteBtn'),
    confirmDeleteBtn: document.getElementById('confirmDeleteBtn'),
    toast: document.getElementById('toast')
  };

  let toastTimer = null;
  function showToast(message) {
    el.toast.textContent = message;
    el.toast.classList.remove('hidden');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.toast.classList.add('hidden'), 3000);
  }

  function coursePriceMap() {
    const map = new Map();
    for (const c of state.courses) {
      map.set(c.name.toLowerCase(), c.price || '');
    }
    return map;
  }

  function getReminderBuckets() {
    const today = state.today;
    const active = state.leads.filter(hasActiveFollowUp);
    const sortFn = (a, b) => {
      if (a.followUpDate !== b.followUpDate) return a.followUpDate.localeCompare(b.followUpDate);
      const tA = a.followUpTime || '99:99';
      const tB = b.followUpTime || '99:99';
      if (tA !== tB) return tA.localeCompare(tB);
      return b.updatedAt - a.updatedAt;
    };
    return {
      overdue: active.filter((l) => l.followUpDate < today).sort(sortFn),
      dueToday: active.filter((l) => l.followUpDate === today).sort(sortFn),
      upcoming: active.filter((l) => l.followUpDate > today).sort(sortFn)
    };
  }

  // ---------------- Rendering ----------------

  function renderAll() {
    renderStatsAndNav();
    renderForm();
    renderLeadsList();
    renderReminders();
  }

  function renderStatsAndNav() {
    const buckets = getReminderBuckets();
    const total = state.leads.length;
    const interested = state.leads.filter((l) => l.status === 'INTERESTED').length;
    const dueToday = buckets.dueToday.length;
    const enrolled = state.leads.filter((l) => l.status === 'ENROLLED').length;
    const urgent = buckets.overdue.length + dueToday;

    el.statTotal.textContent = String(total);
    el.statInterested.textContent = String(interested);
    el.statDueToday.textContent = String(dueToday);
    el.statEnrolled.textContent = String(enrolled);
    el.statDueTodayCard.classList.toggle('highlight', dueToday > 0);
    el.exportCsvMeta.textContent = `Spreadsheet-ready file for Excel or Google Sheets (${total} leads)`;

    if (urgent > 0) {
      el.remindersNavBadge.textContent = urgent > 99 ? '99+' : String(urgent);
      el.remindersNavBadge.classList.remove('hidden');
    } else {
      el.remindersNavBadge.classList.add('hidden');
    }

    const onNotes = state.activeTab === 'notes';
    el.notesView.classList.toggle('hidden', !onNotes);
    el.remindersView.classList.toggle('hidden', onNotes);
    el.fabAddLead.classList.toggle('hidden', !onNotes);
    el.tabNotesBtn.classList.toggle('active', onNotes);
    el.tabRemindersBtn.classList.toggle('active', !onNotes);
  }

  function renderForm() {
    const f = state.form;
    const isEdit = f.editingLeadId != null;
    el.leadFormCard.classList.toggle('editing', isEdit);
    el.formHeading.textContent = isEdit ? 'Edit Student Lead' : 'Log Student Lead';
    el.formCallDateSubtitle.textContent = `Call Date: ${formatDisplayDate(f.callDate)}`;
    el.callDateLabel.textContent = f.callDate === state.today ? 'Today' : formatShortDate(f.callDate);
    el.inputCallDate.value = f.callDate;

    el.leadForm.classList.toggle('hidden', !state.formExpanded);
    el.toggleFormBtn.setAttribute('aria-expanded', String(state.formExpanded));
    el.toggleFormChevron.style.transform = state.formExpanded ? 'rotate(0deg)' : 'rotate(180deg)';

    if (el.inputPhone.value !== f.phoneNumber) el.inputPhone.value = f.phoneNumber;
    if (el.inputStudentName.value !== f.studentName) el.inputStudentName.value = f.studentName;
    if (el.inputNotes.value !== f.notes) el.inputNotes.value = f.notes;

    el.manageCoursesInlineBtn.textContent = state.courses.length === 0 ? '+ Add Course' : 'Edit / Add';

    // Course dropdown options (zero stock courses)
    const opts = [];
    if (state.courses.length === 0) {
      opts.push('<option value="">No courses yet — click + Add Course</option>');
    } else {
      opts.push('<option value="">Select a course</option>');
      for (const c of state.courses) {
        const label = c.price ? `${c.name} (${c.price})` : c.name;
        const selected = c.name.toLowerCase() === f.course.toLowerCase() ? 'selected' : '';
        opts.push(`<option value="${escapeHtml(c.name)}" ${selected}>${escapeHtml(label)}</option>`);
      }
      if (f.course && !state.courses.some((c) => c.name.toLowerCase() === f.course.toLowerCase())) {
        opts.push(`<option value="${escapeHtml(f.course)}" selected>${escapeHtml(f.course)}</option>`);
      }
    }
    el.inputCourse.innerHTML = opts.join('');

    const selectedCourse = state.courses.find((c) => c.name.toLowerCase() === f.course.toLowerCase());
    if (selectedCourse && selectedCourse.price) {
      el.selectedCoursePriceChip.textContent = `Course Price: ${selectedCourse.price} (Tap to edit)`;
      el.selectedCoursePriceChip.classList.remove('hidden');
    } else {
      el.selectedCoursePriceChip.classList.add('hidden');
    }

    // Status chips
    el.statusChips.innerHTML = CALL_STATUSES.map((s) => {
      const selected = s.key === f.status;
      const style = selected
        ? `background:${s.bg};color:${s.fg};border-color:${s.fg};`
        : '';
      return `<button type="button" class="status-chip ${selected ? 'selected' : ''}" data-status="${s.key}" style="${style}" aria-pressed="${selected}">${escapeHtml(s.label)}</button>`;
    }).join('');

    renderFollowUpPanel();

    el.cancelEditBtn.classList.toggle('hidden', !isEdit);
    el.saveLeadBtn.textContent = isEdit ? 'UPDATE LEAD' : 'SAVE LEAD';
  }

  function renderFollowUpPanel() {
    const f = state.form;
    const statusMeta = STATUS_MAP[f.status] || STATUS_MAP.INTERESTED;
    el.followUpPanel.className = 'followup-panel';

    if (statusMeta.autoTwoDays) {
      el.followUpPanel.classList.add('auto-interested');
      const autoDate = addDays(f.callDate, 2);
      el.followUpPanel.innerHTML = `
        <div class="followup-title">Auto Follow-up Scheduled (+2 Days)</div>
        <div class="followup-sub">Due on ${escapeHtml(formatDisplayDate(autoDate))} in Reminders</div>
      `;
      return;
    }

    if (statusMeta.requiresCustom) {
      el.followUpPanel.classList.add('custom-orange');
      el.followUpPanel.innerHTML = `
        <div class="followup-title">Call Back Follow-up Date &amp; Time</div>
        <div class="datetime-row">
          <input id="formFollowUpDate" class="text-input" type="date" value="${escapeHtml(f.customFollowUpDate)}" />
          <input id="formFollowUpTime" class="text-input" type="time" value="${escapeHtml(f.customFollowUpTime || '')}" />
        </div>
      `;
      bindFormFollowUpInputs();
      return;
    }

    if (statusMeta.optionalRetry) {
      if (f.scheduleRetry) el.followUpPanel.classList.add('custom-orange');
      el.followUpPanel.innerHTML = `
        <div class="switch-row">
          <div>
            <div class="followup-title">Schedule another attempt</div>
            <div class="followup-sub">Optional retry reminder for ${escapeHtml(statusMeta.label)}</div>
          </div>
          <input id="formRetryToggle" class="toggle-switch" type="checkbox" ${f.scheduleRetry ? 'checked' : ''} aria-label="Schedule another attempt" />
        </div>
        ${f.scheduleRetry ? `
          <div class="quick-date-row">
            <button type="button" class="chip-btn" data-form-retry-offset="0">Today</button>
            <button type="button" class="chip-btn" data-form-retry-offset="1">Tomorrow</button>
          </div>
          <div class="datetime-row">
            <input id="formFollowUpDate" class="text-input" type="date" value="${escapeHtml(f.customFollowUpDate)}" />
            <input id="formFollowUpTime" class="text-input" type="time" value="${escapeHtml(f.customFollowUpTime || '')}" />
          </div>
        ` : ''}
      `;
      bindFormFollowUpInputs();
      return;
    }

    el.followUpPanel.innerHTML = `
      <div class="followup-sub">${escapeHtml(statusMeta.label)} will not create an automatic follow-up reminder.</div>
    `;
  }

  function bindFormFollowUpInputs() {
    const toggle = document.getElementById('formRetryToggle');
    if (toggle) {
      toggle.addEventListener('change', (e) => {
        state.form.scheduleRetry = e.target.checked;
        if (state.form.scheduleRetry && !isValidIsoDate(state.form.customFollowUpDate)) {
          state.form.customFollowUpDate = addDays(state.today, 1);
        }
        renderFollowUpPanel();
      });
    }
    const dateInput = document.getElementById('formFollowUpDate');
    if (dateInput) {
      dateInput.addEventListener('change', (e) => {
        if (isValidIsoDate(e.target.value)) state.form.customFollowUpDate = e.target.value;
      });
    }
    const timeInput = document.getElementById('formFollowUpTime');
    if (timeInput) {
      timeInput.addEventListener('change', (e) => {
        state.form.customFollowUpTime = e.target.value || null;
      });
    }
    el.followUpPanel.querySelectorAll('[data-form-retry-offset]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const offset = Number(btn.getAttribute('data-form-retry-offset'));
        state.form.customFollowUpDate = addDays(state.today, offset);
        renderFollowUpPanel();
      });
    });
  }

  function renderLeadsList() {
    const q = state.searchQuery.trim().toLowerCase();
    const qDigits = q.replace(/\D/g, '');
    const filtered = !q
      ? state.leads
      : state.leads.filter((l) =>
          (l.studentName || '').toLowerCase().includes(q) ||
          (l.phoneNumber || '').toLowerCase().includes(q) ||
          (qDigits && (l.normalizedPhone || '').includes(qDigits)) ||
          (l.course || '').toLowerCase().includes(q)
        );

    el.leadCountBadge.textContent = q
      ? `${filtered.length} of ${state.leads.length}`
      : `${state.leads.length} total`;
    el.clearSearchBtn.classList.toggle('hidden', !state.searchQuery);

    if (filtered.length === 0) {
      el.leadsList.innerHTML = `
        <div class="empty-state">
          <h3>${q ? 'No matching leads' : 'No student leads yet'}</h3>
          <p>${q ? 'Try a different phone number, student name, or course.' : 'Save your first inquiry above to start tracking calls and follow-ups.'}</p>
        </div>
      `;
      return;
    }

    const prices = coursePriceMap();
    el.leadsList.innerHTML = filtered.map((lead) => {
      const status = STATUS_MAP[lead.status] || STATUS_MAP.INTERESTED;
      const displayName = (lead.studentName || '').trim() || lead.phoneNumber;
      const price = lead.course ? prices.get(lead.course.toLowerCase()) || '' : '';
      const courseLabel = price ? `${lead.course} • ${price}` : lead.course;
      const active = hasActiveFollowUp(lead);
      const timeLabel = formatTimeDisplay(lead.followUpTime);
      const telHref = `tel:${encodeURIComponent(lead.phoneNumber.trim())}`;

      return `
        <article class="lead-card">
          <div class="lead-top-row">
            <div>
              <h3 class="lead-name">${escapeHtml(displayName)}</h3>
              ${lead.studentName ? `<p class="lead-phone">${escapeHtml(lead.phoneNumber)}</p>` : ''}
            </div>
            <span class="status-badge" style="background:${status.bg};color:${status.fg};">${escapeHtml(status.label)}</span>
          </div>

          <div class="meta-row">
            ${lead.course ? `<span class="course-chip">${escapeHtml(courseLabel)}</span>` : ''}
            <span class="called-date">Called ${escapeHtml(formatShortDate(lead.callDate))}</span>
          </div>

          ${active ? `
            <div class="followup-banner">
              Follow-up: ${escapeHtml(formatDisplayDate(lead.followUpDate))}${timeLabel ? ` • ${escapeHtml(timeLabel)}` : ''} (${escapeHtml(formatRelativeDate(lead.followUpDate, state.today))})
            </div>
          ` : ''}

          ${lead.notes ? `<p class="lead-notes">${escapeHtml(lead.notes)}</p>` : ''}

          <hr class="card-divider" />

          <div class="lead-actions-row">
            <a class="call-link call-link-subtle" href="${telHref}" aria-label="Call ${escapeHtml(displayName)}">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.127.96.361 1.903.7 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.907.339 1.85.573 2.81.7A2 2 0 0 1 22 16.92z"/>
              </svg>
              Call
            </a>
            <div class="card-icon-actions">
              <button class="card-icon-btn" type="button" data-edit-lead="${lead.id}" aria-label="Edit lead">
                <svg viewBox="0 0 24 24" width="19" height="19" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/>
                </svg>
              </button>
              <button class="card-icon-btn danger" type="button" data-delete-lead="${lead.id}" aria-label="Delete lead">
                <svg viewBox="0 0 24 24" width="19" height="19" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path d="M3 6h18"/>
                  <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/>
                  <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/>
                </svg>
              </button>
            </div>
          </div>
        </article>
      `;
    }).join('');
  }

  function renderReminders() {
    const buckets = getReminderBuckets();
    if (buckets.overdue.length === 0 && buckets.dueToday.length === 0 && buckets.upcoming.length === 0) {
      el.remindersContainer.innerHTML = `
        <div class="empty-state">
          <h3>All caught up</h3>
          <p>Interested leads and scheduled callbacks will automatically appear here when due.</p>
        </div>
      `;
      return;
    }

    const prices = coursePriceMap();
    const sections = [
      { key: 'overdue', title: 'Overdue', items: buckets.overdue },
      { key: 'today', title: 'Due Today', items: buckets.dueToday },
      { key: 'upcoming', title: 'Upcoming', items: buckets.upcoming }
    ];

    el.remindersContainer.innerHTML = sections
      .filter((s) => s.items.length > 0)
      .map((sec) => {
        const cardsHtml = sec.items.map((lead) => {
          const status = STATUS_MAP[lead.status] || STATUS_MAP.INTERESTED;
          const displayName = (lead.studentName || '').trim() || lead.phoneNumber;
          const price = lead.course ? prices.get(lead.course.toLowerCase()) || '' : '';
          const courseLabel = price ? `${lead.course} • ${price}` : lead.course;
          const timeLabel = formatTimeDisplay(lead.followUpTime);
          const rel = formatRelativeDate(lead.followUpDate, state.today);
          const telHref = `tel:${encodeURIComponent(lead.phoneNumber.trim())}`;

          return `
            <article class="lead-card urgency-${sec.key}">
              <div class="lead-top-row">
                <div>
                  <h3 class="lead-name">${escapeHtml(displayName)}</h3>
                  <p class="lead-phone">${escapeHtml(lead.phoneNumber)}</p>
                </div>
                <a class="call-link call-link-solid" href="${telHref}" aria-label="Call ${escapeHtml(displayName)}">
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.127.96.361 1.903.7 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.907.339 1.85.573 2.81.7A2 2 0 0 1 22 16.92z"/>
                  </svg>
                  Call
                </a>
              </div>

              <div class="meta-row">
                ${lead.course ? `<span class="course-chip">${escapeHtml(courseLabel)}</span>` : ''}
                <span class="status-badge" style="background:${status.bg};color:${status.fg};">${escapeHtml(status.label)}</span>
              </div>

              <div class="followup-banner ${sec.key}">
                ${escapeHtml(formatDisplayDate(lead.followUpDate))}${timeLabel ? ` • ${escapeHtml(timeLabel)}` : ''} — ${escapeHtml(rel)}
              </div>

              ${lead.notes ? `<p class="lead-notes">${escapeHtml(lead.notes)}</p>` : ''}

              <hr class="card-divider" />
              <span class="after-call-label">After Call Action</span>
              <div class="after-call-grid">
                <button type="button" class="after-call-btn" data-after-call="INTERESTED" data-lead-id="${lead.id}" style="background:${STATUS_MAP.INTERESTED.bg};color:${STATUS_MAP.INTERESTED.fg};">Interested (+2d)</button>
                <button type="button" class="after-call-btn" data-after-call="CALL_BACK" data-lead-id="${lead.id}" style="background:${STATUS_MAP.CALL_BACK.bg};color:${STATUS_MAP.CALL_BACK.fg};">Call Back</button>
                <button type="button" class="after-call-btn" data-after-call="NOT_CONNECTED" data-lead-id="${lead.id}" style="background:${STATUS_MAP.NOT_CONNECTED.bg};color:${STATUS_MAP.NOT_CONNECTED.fg};">Not Connected</button>
                <button type="button" class="after-call-btn" data-after-call="NOT_INTERESTED" data-lead-id="${lead.id}" style="background:${STATUS_MAP.NOT_INTERESTED.bg};color:${STATUS_MAP.NOT_INTERESTED.fg};">Not Interested</button>
                <button type="button" class="after-call-btn" data-after-call="ENROLLED" data-lead-id="${lead.id}" style="background:${STATUS_MAP.ENROLLED.bg};color:${STATUS_MAP.ENROLLED.fg};">Enrolled</button>
              </div>
            </article>
          `;
        }).join('');

        return `
          <div class="reminder-section-header ${sec.key}">
            <span>${escapeHtml(sec.title)}</span>
            <span class="count-pill">${sec.items.length}</span>
          </div>
          ${cardsHtml}
        `;
      }).join('');
  }

  function renderCoursesModal() {
    const editing = state.courses.find((c) => c.id === state.editingCourseId) || null;
    el.coursesModalTitle.textContent = editing ? 'Edit Course' : 'Manage Courses';
    el.coursesModalSubtitle.textContent = editing
      ? 'Update course name or price below.'
      : 'Add courses with their fee and edit anytime.';
    el.saveCourseBtn.textContent = editing ? 'Save Changes' : 'Add Course';
    el.cancelCourseEditBtn.classList.toggle('hidden', !editing);

    if (state.courses.length === 0) {
      el.coursesModalList.innerHTML = `
        <div class="empty-state" style="padding:16px;">
          <p>No courses added yet. Add your first course and price above.</p>
        </div>
      `;
      return;
    }

    el.coursesModalList.innerHTML = state.courses.map((c) => `
      <div class="course-row ${state.editingCourseId === c.id ? 'editing' : ''}">
        <div class="course-row-info">
          <div class="course-row-name">${escapeHtml(c.name)}</div>
          <div class="course-row-price ${c.price ? '' : 'muted'}">
            ${c.price ? `Price: ${escapeHtml(c.price)}` : 'No price set'}
          </div>
        </div>
        <div class="card-icon-actions">
          <button type="button" class="card-icon-btn" data-edit-course="${c.id}" aria-label="Edit ${escapeHtml(c.name)}">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/>
            </svg>
          </button>
          <button type="button" class="card-icon-btn danger" data-delete-course="${c.id}" aria-label="Delete ${escapeHtml(c.name)}">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M3 6h18"/>
              <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/>
              <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/>
            </svg>
          </button>
        </div>
      </div>
    `).join('');
  }

  // ---------------- CSV & JSON Export / Restore ----------------

  function escapeCsv(val) {
    const s = String(val ?? '');
    return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  }

  function downloadFile(filename, content, mimeType) {
    const blob = new Blob([content], { type: mimeType });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  function exportCsv() {
    const header = 'Student Name,Phone Number,Interested Course,Call Status,Call Date,Follow-up Date,Follow-up Time,Notes';
    const rows = state.leads.map((l) => {
      const statusLabel = (STATUS_MAP[l.status] || STATUS_MAP.INTERESTED).label;
      return [
        l.studentName || '',
        l.phoneNumber || '',
        l.course || '',
        statusLabel,
        l.callDate || '',
        l.followUpDate || '',
        l.followUpTime || '',
        l.notes || ''
      ].map(escapeCsv).join(',');
    });
    downloadFile(`my_sales_diary_leads_${state.today}.csv`, [header, ...rows].join('\n'), 'text/csv;charset=utf-8');
    showToast('Leads exported to CSV.');
  }

  function exportJsonBackup() {
    const payload = {
      schemaVersion: 2,
      appName: 'My Sales Diary',
      exportedAtMillis: Date.now(),
      courses: state.courses.map((c) => c.name),
      courseItems: state.courses.map((c) => ({ name: c.name, price: c.price || '' })),
      leads: state.leads.map((l) => ({
        studentName: l.studentName || '',
        phoneNumber: l.phoneNumber,
        course: l.course || '',
        callDate: l.callDate,
        status: l.status,
        notes: l.notes || '',
        followUpDate: l.followUpDate || null,
        followUpTime: l.followUpTime || null,
        createdAt: l.createdAt || Date.now(),
        updatedAt: l.updatedAt || Date.now()
      }))
    };
    downloadFile(
      `my_sales_diary_backup_${state.today}.json`,
      JSON.stringify(payload, null, 2),
      'application/json;charset=utf-8'
    );
    showToast('JSON backup saved.');
  }

  async function restoreJsonBackup(rawText) {
    try {
      const parsed = JSON.parse(rawText);
      if (!parsed || typeof parsed !== 'object' || !Array.isArray(parsed.leads)) {
        throw new Error('Invalid backup file format.');
      }

      const now = Date.now();
      const coursesByKey = new Map();
      if (Array.isArray(parsed.courseItems) && parsed.courseItems.length > 0) {
        for (const item of parsed.courseItems) {
          const name = String(item?.name || '').trim();
          if (name && !coursesByKey.has(name.toLowerCase())) {
            coursesByKey.set(name.toLowerCase(), { name, price: String(item?.price || '').trim() });
          }
        }
      } else if (Array.isArray(parsed.courses)) {
        for (const cName of parsed.courses) {
          const name = String(cName || '').trim();
          if (name && !coursesByKey.has(name.toLowerCase())) {
            coursesByKey.set(name.toLowerCase(), { name, price: '' });
          }
        }
      }

      const rawLeads = [];
      for (const dto of parsed.leads) {
        const cleanPhone = String(dto?.phoneNumber || '').trim();
        const normalized = normalizePhone(cleanPhone);
        if (!normalized) continue;
        const statusKey = resolveStatusKey(dto.status);
        const statusMeta = STATUS_MAP[statusKey];
        const callDate = isValidIsoDate(dto.callDate) ? dto.callDate : state.today;
        let followUpDate = null;
        if (!statusMeta.closesReminder) {
          if (isValidIsoDate(dto.followUpDate)) followUpDate = dto.followUpDate;
          else if (statusMeta.autoTwoDays) followUpDate = addDays(callDate, 2);
        }
        const followUpTime = followUpDate && isValidTime(dto.followUpTime) ? dto.followUpTime : null;
        const course = String(dto?.course || '').trim();
        if (course && !coursesByKey.has(course.toLowerCase())) {
          coursesByKey.set(course.toLowerCase(), { name: course, price: '' });
        }
        rawLeads.push({
          studentName: String(dto?.studentName || '').trim(),
          phoneNumber: cleanPhone,
          normalizedPhone: normalized,
          course,
          callDate,
          status: statusKey,
          notes: String(dto?.notes || '').trim(),
          followUpDate,
          followUpTime,
          createdAt: Number(dto?.createdAt) > 0 ? Number(dto.createdAt) : now,
          updatedAt: Number(dto?.updatedAt) > 0 ? Number(dto.updatedAt) : now
        });
      }

      // Deduplicate active follow-ups by normalized phone
      rawLeads.sort((a, b) => b.updatedAt - a.updatedAt);
      const seenActive = new Set();
      const dedupedLeads = [];
      for (const l of rawLeads) {
        if (hasActiveFollowUp(l)) {
          if (seenActive.has(l.normalizedPhone)) continue;
          seenActive.add(l.normalizedPhone);
        }
        dedupedLeads.push(l);
      }

      const finalCourses = Array.from(coursesByKey.values());
      await dbApi.replaceAll(dedupedLeads, finalCourses);
      await reloadData();
      resetLeadForm();
      renderAll();
      showToast(`Restored ${dedupedLeads.length} leads and ${finalCourses.length} courses.`);
    } catch (err) {
      showToast(err?.message || 'Could not restore backup file.');
    }
  }

  // ---------------- Form & Modal Helpers ----------------

  function resetLeadForm() {
    const defaultCourse = state.courses[0]?.name || '';
    state.form = {
      editingLeadId: null,
      studentName: '',
      phoneNumber: '',
      course: defaultCourse,
      callDate: state.today,
      status: 'INTERESTED',
      customFollowUpDate: addDays(state.today, 1),
      customFollowUpTime: '10:00',
      scheduleRetry: false,
      notes: ''
    };
    el.phoneError.classList.add('hidden');
  }

  function openCoursesModal() {
    state.editingCourseId = null;
    el.courseNameInput.value = '';
    el.coursePriceInput.value = '';
    renderCoursesModal();
    el.coursesModalBackdrop.classList.remove('hidden');
  }

  function closeCoursesModal() {
    el.coursesModalBackdrop.classList.add('hidden');
  }

  function openRescheduleModal(lead, targetStatus) {
    state.rescheduleTarget = { lead, targetStatus };
    el.rescheduleModalTitle.textContent =
      targetStatus === 'CALL_BACK' ? 'Schedule Call Back' : 'Retry Attempt (Not Connected)';
    const displayName = (lead.studentName || '').trim() || lead.phoneNumber;
    el.rescheduleLeadMeta.textContent = `${displayName} • ${lead.phoneNumber}`;
    el.rescheduleDateInput.value = addDays(state.today, 1);
    el.rescheduleTimeInput.value = '10:00';
    el.rescheduleNoteInput.value = lead.notes || '';
    el.rescheduleModalBackdrop.classList.remove('hidden');
  }

  async function reloadData() {
    const [leads, courses] = await Promise.all([dbApi.getAllLeads(), dbApi.getAllCourses()]);
    state.leads = leads;
    state.courses = courses;
  }

  function refreshLocalDateIfChanged() {
    const latest = todayIso();
    if (latest !== state.today) {
      const prev = state.today;
      state.today = latest;
      if (state.form.editingLeadId == null && state.form.callDate === prev) {
        state.form.callDate = latest;
        state.form.customFollowUpDate = addDays(latest, 1);
      }
      renderAll();
    }
  }

  // ---------------- Event Wiring ----------------

  function bindEvents() {
    // Bottom nav & stat card shortcuts
    document.querySelectorAll('[data-tab]').forEach((btn) => {
      btn.addEventListener('click', () => {
        state.activeTab = btn.getAttribute('data-tab');
        renderStatsAndNav();
        window.scrollTo({ top: 0, behavior: 'smooth' });
      });
    });

    document.querySelectorAll('[data-stat-action]').forEach((btn) => {
      btn.addEventListener('click', () => {
        state.activeTab = btn.getAttribute('data-stat-action');
        renderStatsAndNav();
        window.scrollTo({ top: 0, behavior: 'smooth' });
      });
    });

    el.fabAddLead.addEventListener('click', () => {
      state.activeTab = 'notes';
      state.formExpanded = true;
      resetLeadForm();
      renderAll();
      window.scrollTo({ top: 0, behavior: 'smooth' });
      el.inputPhone.focus();
    });

    el.toggleFormBtn.addEventListener('click', () => {
      state.formExpanded = !state.formExpanded;
      renderForm();
    });

    el.inputCallDate.addEventListener('change', (e) => {
      if (isValidIsoDate(e.target.value)) {
        state.form.callDate = e.target.value;
        renderForm();
      }
    });

    el.inputPhone.addEventListener('input', (e) => {
      state.form.phoneNumber = e.target.value;
      el.phoneError.classList.add('hidden');
    });

    el.inputStudentName.addEventListener('input', (e) => {
      state.form.studentName = e.target.value;
    });

    el.inputCourse.addEventListener('change', (e) => {
      state.form.course = e.target.value;
      renderForm();
    });

    el.inputNotes.addEventListener('input', (e) => {
      state.form.notes = e.target.value;
    });

    el.manageCoursesInlineBtn.addEventListener('click', openCoursesModal);
    el.selectedCoursePriceChip.addEventListener('click', openCoursesModal);

    el.statusChips.addEventListener('click', (e) => {
      const chip = e.target.closest('[data-status]');
      if (!chip) return;
      state.form.status = chip.getAttribute('data-status');
      renderForm();
    });

    el.cancelEditBtn.addEventListener('click', () => {
      resetLeadForm();
      renderForm();
    });

    el.leadForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const digits = normalizePhone(state.form.phoneNumber);
      if (digits.length < 5) {
        el.phoneError.classList.remove('hidden');
        el.inputPhone.focus();
        return;
      }

      const wasEditing = state.form.editingLeadId != null;
      const saved = await dbApi.saveLead(state.form);
      await reloadData();
      resetLeadForm();
      renderAll();

      if (saved.followUpDate) {
        showToast(`${wasEditing ? 'Lead updated' : 'Lead saved'} • Follow-up set for ${formatShortDate(saved.followUpDate)}`);
      } else {
        showToast(wasEditing ? 'Lead updated • Reminder closed' : 'Lead saved');
      }
    });

    // Search
    el.searchInput.addEventListener('input', (e) => {
      state.searchQuery = e.target.value;
      renderLeadsList();
    });

    el.clearSearchBtn.addEventListener('click', () => {
      state.searchQuery = '';
      el.searchInput.value = '';
      renderLeadsList();
    });

    // Lead card edit / delete delegation
    el.leadsList.addEventListener('click', (e) => {
      const editBtn = e.target.closest('[data-edit-lead]');
      if (editBtn) {
        const id = Number(editBtn.getAttribute('data-edit-lead'));
        const lead = state.leads.find((l) => l.id === id);
        if (!lead) return;
        const meta = STATUS_MAP[lead.status] || STATUS_MAP.INTERESTED;
        state.formExpanded = true;
        state.form = {
          editingLeadId: lead.id,
          studentName: lead.studentName || '',
          phoneNumber: lead.phoneNumber,
          course: lead.course || '',
          callDate: lead.callDate,
          status: lead.status,
          customFollowUpDate: lead.followUpDate || addDays(state.today, 1),
          customFollowUpTime: lead.followUpTime || '10:00',
          scheduleRetry: Boolean(meta.optionalRetry && lead.followUpDate),
          notes: lead.notes || ''
        };
        renderForm();
        window.scrollTo({ top: 0, behavior: 'smooth' });
        return;
      }

      const delBtn = e.target.closest('[data-delete-lead]');
      if (delBtn) {
        const id = Number(delBtn.getAttribute('data-delete-lead'));
        const lead = state.leads.find((l) => l.id === id);
        if (!lead) return;
        state.deleteLeadId = id;
        const name = (lead.studentName || '').trim() || lead.phoneNumber;
        el.deleteModalBody.textContent = `Remove ${name} (${lead.phoneNumber}) and any scheduled follow-up from your diary?`;
        el.deleteModalBackdrop.classList.remove('hidden');
      }
    });

    el.cancelDeleteBtn.addEventListener('click', () => {
      state.deleteLeadId = null;
      el.deleteModalBackdrop.classList.add('hidden');
    });

    el.confirmDeleteBtn.addEventListener('click', async () => {
      if (state.deleteLeadId != null) {
        const target = state.leads.find((l) => l.id === state.deleteLeadId);
        await dbApi.deleteLead(state.deleteLeadId);
        if (state.form.editingLeadId === state.deleteLeadId) resetLeadForm();
        state.deleteLeadId = null;
        el.deleteModalBackdrop.classList.add('hidden');
        await reloadData();
        renderAll();
        if (target) showToast(`Deleted ${(target.studentName || '').trim() || target.phoneNumber}`);
      }
    });

    // Reminders after-call actions
    el.remindersContainer.addEventListener('click', async (e) => {
      const btn = e.target.closest('[data-after-call]');
      if (!btn) return;
      const actionStatus = btn.getAttribute('data-after-call');
      const leadId = Number(btn.getAttribute('data-lead-id'));
      const lead = state.leads.find((l) => l.id === leadId);
      if (!lead) return;

      if (actionStatus === 'CALL_BACK' || actionStatus === 'NOT_CONNECTED') {
        openRescheduleModal(lead, actionStatus);
        return;
      }

      const updated = await dbApi.applyAfterCallAction(leadId, actionStatus);
      await reloadData();
      renderAll();
      if (updated) {
        const name = (updated.studentName || '').trim() || updated.phoneNumber;
        if (updated.followUpDate) {
          showToast(`${name} → ${STATUS_MAP[actionStatus].label} (${formatShortDate(updated.followUpDate)})`);
        } else if (actionStatus === 'ENROLLED') {
          showToast(`${name} marked Enrolled!`);
        } else {
          showToast(`${name} → ${STATUS_MAP[actionStatus].label} (Closed)`);
        }
      }
    });

    // Reschedule modal
    el.rescheduleModalBackdrop.querySelectorAll('[data-quick-offset]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const offset = Number(btn.getAttribute('data-quick-offset'));
        el.rescheduleDateInput.value = addDays(state.today, offset);
      });
    });

    el.cancelRescheduleBtn.addEventListener('click', () => {
      state.rescheduleTarget = null;
      el.rescheduleModalBackdrop.classList.add('hidden');
    });

    el.confirmRescheduleBtn.addEventListener('click', async () => {
      if (!state.rescheduleTarget) return;
      const { lead, targetStatus } = state.rescheduleTarget;
      const pickedDate = isValidIsoDate(el.rescheduleDateInput.value)
        ? el.rescheduleDateInput.value
        : addDays(state.today, 1);
      const pickedTime = isValidTime(el.rescheduleTimeInput.value) ? el.rescheduleTimeInput.value : null;
      const note = el.rescheduleNoteInput.value;

      const updated = await dbApi.applyAfterCallAction(lead.id, targetStatus, pickedDate, pickedTime, note);
      state.rescheduleTarget = null;
      el.rescheduleModalBackdrop.classList.add('hidden');
      await reloadData();
      renderAll();
      if (updated) {
        const name = (updated.studentName || '').trim() || updated.phoneNumber;
        showToast(`${name} scheduled for ${formatShortDate(pickedDate)}`);
      }
    });

    // Backup & Tools bottom sheet
    el.backupToolsBtn.addEventListener('click', () => {
      el.backupSheetBackdrop.classList.remove('hidden');
    });
    el.closeBackupSheetBtn.addEventListener('click', () => {
      el.backupSheetBackdrop.classList.add('hidden');
    });
    el.backupSheetBackdrop.addEventListener('click', (e) => {
      if (e.target === el.backupSheetBackdrop) el.backupSheetBackdrop.classList.add('hidden');
    });

    el.exportCsvBtn.addEventListener('click', () => {
      el.backupSheetBackdrop.classList.add('hidden');
      exportCsv();
    });

    el.exportJsonBtn.addEventListener('click', () => {
      el.backupSheetBackdrop.classList.add('hidden');
      exportJsonBackup();
    });

    el.restoreJsonBtn.addEventListener('click', () => {
      el.backupSheetBackdrop.classList.add('hidden');
      el.restoreFileInput.value = '';
      el.restoreFileInput.click();
    });

    el.restoreFileInput.addEventListener('change', async (e) => {
      const file = e.target.files?.[0];
      if (!file) return;
      const text = await file.text();
      await restoreJsonBackup(text);
    });

    el.sheetManageCoursesBtn.addEventListener('click', () => {
      el.backupSheetBackdrop.classList.add('hidden');
      openCoursesModal();
    });

    // Manage Courses modal
    el.closeCoursesModalBtn.addEventListener('click', closeCoursesModal);
    el.coursesModalBackdrop.addEventListener('click', (e) => {
      if (e.target === el.coursesModalBackdrop) closeCoursesModal();
    });

    el.cancelCourseEditBtn.addEventListener('click', () => {
      state.editingCourseId = null;
      el.courseNameInput.value = '';
      el.coursePriceInput.value = '';
      renderCoursesModal();
    });

    el.courseForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const name = el.courseNameInput.value.trim();
      const price = el.coursePriceInput.value.trim();
      if (!name) return;

      if (state.editingCourseId != null) {
        const oldCourse = state.courses.find((c) => c.id === state.editingCourseId);
        const ok = await dbApi.updateCourse(state.editingCourseId, name, price);
        if (!ok) {
          showToast('Another course already has that name.');
          return;
        }
        if (oldCourse && state.form.course.toLowerCase() === oldCourse.name.toLowerCase()) {
          state.form.course = name;
        }
        state.editingCourseId = null;
        el.courseNameInput.value = '';
        el.coursePriceInput.value = '';
        await reloadData();
        renderCoursesModal();
        renderAll();
        showToast(`Updated course: ${name}${price ? ` (${price})` : ''}`);
      } else {
        const ok = await dbApi.addCourse(name, price);
        if (!ok) {
          showToast('Course already exists.');
          return;
        }
        state.form.course = name;
        el.courseNameInput.value = '';
        el.coursePriceInput.value = '';
        await reloadData();
        renderCoursesModal();
        renderAll();
        showToast(`Added course: ${name}${price ? ` (${price})` : ''}`);
      }
    });

    el.coursesModalList.addEventListener('click', async (e) => {
      const editBtn = e.target.closest('[data-edit-course]');
      if (editBtn) {
        const id = Number(editBtn.getAttribute('data-edit-course'));
        const course = state.courses.find((c) => c.id === id);
        if (!course) return;
        state.editingCourseId = course.id;
        el.courseNameInput.value = course.name;
        el.coursePriceInput.value = course.price || '';
        renderCoursesModal();
        el.courseNameInput.focus();
        return;
      }

      const delBtn = e.target.closest('[data-delete-course]');
      if (delBtn) {
        const id = Number(delBtn.getAttribute('data-delete-course'));
        const course = state.courses.find((c) => c.id === id);
        if (!course) return;
        await dbApi.deleteCourse(id);
        if (state.editingCourseId === id) {
          state.editingCourseId = null;
          el.courseNameInput.value = '';
          el.coursePriceInput.value = '';
        }
        await reloadData();
        if (state.form.course.toLowerCase() === course.name.toLowerCase()) {
          state.form.course = state.courses[0]?.name || '';
        }
        renderCoursesModal();
        renderAll();
        showToast(`Removed course: ${course.name}`);
      }
    });

    // PWA Install Prompt
    window.addEventListener('beforeinstallprompt', (e) => {
      e.preventDefault();
      state.deferredInstallPrompt = e;
      el.installBtn.classList.remove('hidden');
    });

    el.installBtn.addEventListener('click', async () => {
      if (!state.deferredInstallPrompt) return;
      state.deferredInstallPrompt.prompt();
      await state.deferredInstallPrompt.userChoice;
      state.deferredInstallPrompt = null;
      el.installBtn.classList.add('hidden');
    });

    window.addEventListener('appinstalled', () => {
      el.installBtn.classList.add('hidden');
      showToast('My Sales Diary installed to home screen!');
    });

    // Refresh local date automatically when returning to app or crossing midnight
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') refreshLocalDateIfChanged();
    });
    window.addEventListener('focus', refreshLocalDateIfChanged);
    setInterval(refreshLocalDateIfChanged, 60000);
  }

  // ---------------- Boot ----------------

  async function init() {
    await dbApi.init();
    await reloadData();
    resetLeadForm();
    bindEvents();
    renderAll();

    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.register('./sw.js', { scope: './' }).catch(() => {});
    }
  }

  init();
})();
