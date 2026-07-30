import assert from 'node:assert/strict'
import { test } from 'node:test'

import { collectAccessContextsFromPageState } from '../content/access-context.js'

test('returns only note access fields from nested page state', () => {
  const state = {
    user: { cookie: 'must-not-be-returned' },
    feeds: [
      {
        noteCard: { noteId: '6a50f3d6000000001003eb28', title: 'must-not-be-returned' },
        xsecToken: 'fake-state-token',
        xsecSource: 'pc_collect',
      },
      {
        id: '6a50f3d6000000001003eb29',
        xsec_token: 'fake-snake-token',
      },
    ],
  }

  assert.deepEqual(collectAccessContextsFromPageState([state]), [
    {
      noteId: '6a50f3d6000000001003eb29',
      xsecToken: 'fake-snake-token',
      xsecSource: 'pc_collect',
    },
    {
      noteId: '6a50f3d6000000001003eb28',
      xsecToken: 'fake-state-token',
      xsecSource: 'pc_collect',
    },
  ])
})
