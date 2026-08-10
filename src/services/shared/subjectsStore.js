// Shared in-memory store for the subjects catalog.
//
// Why this file exists: array methods like .map()/.filter() return a NEW
// array — they don't mutate the original. If professorService.js and
// studentService.js each kept their own local `let catalog = [...]`
// variable, reassigning one would NOT be visible to the other, even
// though both imported the same starting data. This module holds the
// ONE mutable reference that both services read and write through, so
// changes actually stay in sync between Professor and Student.
//
// TODO: this whole file goes away once there's a real backend — both
// services will just call the API directly instead of sharing local state.

import { mockSubjectsCatalog } from '../../data/shared/subjectsCatalog';

let catalog = mockSubjectsCatalog;

export function getCatalog() {
  return catalog;
}

export function setCatalog(newCatalog) {
  catalog = newCatalog;
}