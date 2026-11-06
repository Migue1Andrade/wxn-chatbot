import angular from 'angular';
import { matchesConversation } from './conversation-filters.js';

angular.module('wxn.chats').factory('ChatStateService', function () {
  const state = {
    conversations: [],
    selected: null,
    unread: {},
    loading: false,
    loadingMore: false,
    error: null,
    filter: 'all',
    responsibleId: null,
    search: '',
    readerId: null,
    hasMore: false,
    nextPage: null,
  };
  let changes = 0;
  const revisions = new Map();
  const readRevisions = new Map();
  const readBoundaries = new Map();
  function newer(previous, incoming) {
    return (
      !previous ||
      (incoming.version || 0) > (previous.version || 0) ||
      ((incoming.version || 0) === (previous.version || 0) &&
        (Date.parse(incoming.updatedAt) || 0) >= (Date.parse(previous.updatedAt) || 0))
    );
  }
  function combine(previous, incoming) {
    return newer(previous, incoming) ? { ...previous, ...incoming } : previous;
  }
  function sort() {
    state.conversations.sort(
      (a, b) => (Date.parse(b.updatedAt) || 0) - (Date.parse(a.updatedAt) || 0) || b.id.localeCompare(a.id),
    );
  }
  function applyEvent(conversation) {
    revisions.set(conversation.id, ++changes);
    if (state.selected?.id === conversation.id) {
      state.selected = combine(state.selected, conversation);
    }
    const position = state.conversations.findIndex((row) => row.id === conversation.id);
    const merged = combine(position >= 0 ? state.conversations[position] : null, conversation);
    if (!matchesConversation(merged, state)) {
      if (position >= 0) {
        state.conversations.splice(position, 1);
      }
    } else if (position >= 0) {
      state.conversations[position] = merged;
    } else {
      state.conversations.push(merged);
    }
    sort();
  }
  function replace(rows, requestedAt) {
    const current = new Map(state.conversations.map((row) => [row.id, row]));
    const result = new Map();
    for (const row of rows) {
      const merged = combine(current.get(row.id), row);
      if ((readRevisions.get(row.id) || 0) <= requestedAt && row.unreadCount != null) {
        state.unread[row.id] = row.unreadCount;
      }
      if (state.unread[row.id] != null) {
        merged.unreadCount = state.unread[row.id];
      }
      if (matchesConversation(merged, state)) {
        result.set(row.id, merged);
      }
      if (state.selected?.id === row.id) {
        state.selected = combine(state.selected, merged);
      }
    }
    for (const row of current.values()) {
      if ((revisions.get(row.id) || 0) > requestedAt && matchesConversation(row, state)) {
        result.set(row.id, row);
      }
    }
    state.conversations = [...result.values()];
    sort();
  }
  function read(update) {
    if (update.memberId !== state.readerId) {
      return;
    }
    const frontier = { time: Date.parse(update.lastReadAt) || 0, id: update.throughMessageId || '' };
    const previous = readBoundaries.get(update.conversationId);
    if (
      previous &&
      (previous.time > frontier.time || (previous.time === frontier.time && previous.id > frontier.id))
    ) {
      return;
    }
    readBoundaries.set(update.conversationId, frontier);
    readRevisions.set(update.conversationId, ++changes);
    state.unread[update.conversationId] = update.unreadCount;
    const conversation = state.conversations.find((row) => row.id === update.conversationId);
    if (conversation) {
      conversation.unreadCount = update.unreadCount;
    }
    if (state.selected?.id === update.conversationId) {
      state.selected.unreadCount = update.unreadCount;
    }
  }
  function resetQuery() {
    state.conversations = [];
    state.hasMore = false;
    state.nextPage = null;
    revisions.clear();
    readRevisions.clear();
    readBoundaries.clear();
  }
  return {
    state,
    applyEvent,
    replace,
    read,
    resetQuery,
    revision: () => changes,
    select(conversation) {
      state.selected = conversation;
    },
  };
});
