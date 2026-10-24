import angular from 'angular';
import { AsyncState } from '../../core/state/async-state.js';
import { mergeMessages } from './message-state.js';

class MessageHistorySession {
  constructor(messages, activities) {
    this.messages = messages;
    this.activities = activities;
    this.conversationId = null;
    this.selection = 0;
    this.request = 0;
    this.eventRequest = 0;
    this.disposed = false;
    this.initialized = false;
    this.query = new AsyncState();
    this.eventsQuery = new AsyncState();
    this.olderQuery = new AsyncState();
    this.state = { items: [], events: [], hasMore: false };
    this.nextCursor = null;
  }
  select(conversation) {
    if (conversation.id !== this.conversationId) {
      this.selection++;
      this.conversationId = conversation.id;
      this.initialized = false;
      this.nextCursor = null;
      this.state.items = [];
      this.state.events = [];
      this.state.hasMore = false;
      this.query.reset();
      this.eventsQuery.reset();
      this.olderQuery.reset();
    }
    this.reconcile();
    this.loadEvents();
  }
  current(selection, request) {
    return !this.disposed && selection === this.selection && (request == null || request === this.request);
  }
  upsert(message) {
    if (message.conversationId === this.conversationId)
      this.state.items = mergeMessages(this.state.items, [message]);
  }
  reconcile() {
    if (!this.conversationId || this.disposed) return;
    const selection = this.selection;
    const request = ++this.request;
    const id = this.conversationId;
    const previousLatest = this.state.items.at(-1)?.id;
    this.query.begin();
    return this.messages
      .listMessages(id)
      .then(async (page) => {
        if (!this.current(selection, request)) return;
        this.state.items = mergeMessages(this.state.items, page.items);
        if (!this.initialized) {
          this.nextCursor = page.nextCursor;
          this.state.hasMore = page.hasMore;
          this.initialized = true;
        }
        while (
          previousLatest &&
          !page.items.some((row) => row.id === previousLatest) &&
          page.nextCursor &&
          this.current(selection, request)
        ) {
          page = await this.messages.listMessages(id, page.nextCursor);
          if (!this.current(selection, request)) return;
          this.state.items = mergeMessages(this.state.items, page.items);
        }
        if (this.current(selection, request)) this.query.succeed();
      })
      .catch((error) => {
        if (this.current(selection, request)) this.query.fail(error);
      });
  }
  loadOlder() {
    if (this.olderQuery.loading || !this.state.hasMore || !this.nextCursor) return;
    const selection = this.selection;
    const id = this.conversationId;
    this.olderQuery.begin();
    return this.messages
      .listMessages(id, this.nextCursor)
      .then((page) => {
        if (!this.current(selection)) return;
        this.state.items = mergeMessages(this.state.items, page.items);
        this.nextCursor = page.nextCursor;
        this.state.hasMore = page.hasMore;
        this.olderQuery.succeed();
      })
      .catch((error) => {
        if (this.current(selection)) this.olderQuery.fail(error);
      });
  }
  loadEvents() {
    if (!this.conversationId || this.disposed) return;
    const selection = this.selection;
    const request = ++this.eventRequest;
    this.eventsQuery.begin();
    return this.activities
      .listEvents(this.conversationId)
      .then((rows) => {
        if (this.current(selection) && request === this.eventRequest) {
          this.state.events = rows;
          this.eventsQuery.succeed();
        }
      })
      .catch((error) => {
        if (this.current(selection) && request === this.eventRequest) this.eventsQuery.fail(error);
      });
  }
  get loading() {
    return this.query.loading && this.state.items.length === 0;
  }
  get error() {
    return this.query.error || this.eventsQuery.error || this.olderQuery.error;
  }
  dispose() {
    this.disposed = true;
    this.selection++;
  }
}

angular.module('wxn.messages').factory('MessageHistoryService', [
  'MessagesService',
  'ConversationActivitiesService',
  function (messages, activities) {
    return {
      create() {
        return new MessageHistorySession(messages, activities);
      },
    };
  },
]);
