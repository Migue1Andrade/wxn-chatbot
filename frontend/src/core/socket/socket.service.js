import angular from 'angular';
import { Client, ReconnectionTimeMode } from '@stomp/stompjs';
import { BackendEvents } from '../events/backend-events.js';

angular.module('wxn.core').factory('SocketService', [
  'AccountSessionState',
  'environment',
  'EventBusService',
  'AppEvents',
  'ChatEvents',
  'MessageEvents',
  function (account, environment, bus, app, chats, messages) {
    const state = { connected: false, connecting: false, error: null };
    const seen = new Set();
    let subscription = null;
    let notificationSubscription = null;
    let channelSubscription = null;
    let started = false;
    let client = null;
    let epoch = 0;
    const mapping = {
      'kanban.metrics.updated': 'wxn:kanban:metrics',
      'channel.connection.changed': 'wxn:channel:changed',
      'notification.created': 'wxn:notification:created',
      'conversation.team.updated': 'wxn:team:updated',
      [BackendEvents.ACCESS_REVOKED]: chats.ACCESS_REVOKED,
      [BackendEvents.CONVERSATION_CREATED]: chats.CREATED,
      [BackendEvents.CONVERSATION_UPDATED]: chats.UPDATED,
      [BackendEvents.CONVERSATION_ASSIGNED]: chats.ASSIGNED,
      [BackendEvents.CONVERSATION_TRANSFERRED]: chats.TRANSFERRED,
      [BackendEvents.CONVERSATION_CLOSED]: chats.CLOSED,
      [BackendEvents.CONVERSATION_REOPENED]: chats.REOPENED,
      [BackendEvents.CONVERSATION_READ_UPDATED]: chats.READ_UPDATED,
      [BackendEvents.MESSAGE_RECEIVED]: messages.RECEIVED,
      [BackendEvents.MESSAGE_SENT]: messages.SENT,
      [BackendEvents.MESSAGE_STATUS_UPDATED]: messages.STATUS_UPDATED,
    };
    function receive(frame) {
      try {
        const event = JSON.parse(frame.body);
        if (!mapping[event.type] || !event.eventId || seen.has(event.eventId)) {
          return;
        }
        seen.add(event.eventId);
        if (seen.size > 2000) {
          seen.delete(seen.values().next().value);
        }
        bus.emit(mapping[event.type], event);
      } catch {
        state.error = 'Evento inválido recebido.';
        bus.emit(app.SOCKET_ERROR, state.error);
      }
    }
    function disconnected() {
      state.connected = false;
      state.connecting = started;
      subscription = null;
      bus.emit(app.SOCKET_DISCONNECTED, state);
    }
    function connect() {
      if (started) {
        return;
      }
      if (!environment.devApiKey) {
        state.error = 'Configure o acesso local para conectar.';
        bus.emit(app.SOCKET_ERROR, state.error);
        return;
      }
      const current = ++epoch;
      started = true;
      state.connecting = true;
      state.error = null;
      const connection = new Client({
        brokerURL: environment.wsUrl,
        connectHeaders: { 'X-Dev-Api-Key': environment.devApiKey },
        reconnectDelay: 1000,
        maxReconnectDelay: 30000,
        reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
        connectionTimeout: 10000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        onConnect() {
          if (current !== epoch) {
            return;
          }
          const receipt = 'subscription-' + crypto.randomUUID();
          connection.watchForReceipt(receipt, () => {
            if (current !== epoch) {
              return;
            }
            state.connected = true;
            state.connecting = false;
            state.error = null;
            bus.emit(app.SOCKET_CONNECTED, state);
          });
          if (account.user?.role === 'ADMIN') {
            channelSubscription = connection.subscribe('/topic/admin/channels', receive);
          }
          notificationSubscription = connection.subscribe(
            '/topic/users/' + account.user.id + '/notifications',
            receive,
          );
          subscription = connection.subscribe(
            account.user?.role === 'ADMIN'
              ? '/topic/conversations'
              : '/topic/members/' + account.user?.memberId + '/conversations',
            receive,
            { receipt },
          );
        },
        onWebSocketClose() {
          if (current === epoch) {
            disconnected();
          }
        },
        onStompError() {
          if (current !== epoch) {
            return;
          }
          state.error = 'Conexão em tempo real recusada.';
          started = false;
          connection.deactivate({ force: true });
          bus.emit(app.SOCKET_ERROR, state.error);
        },
        onWebSocketError() {
          if (current === epoch) {
            state.error = 'Conexão em tempo real indisponível.';
          }
        },
        debug() {},
      });
      client = connection;
      connection.activate();
    }
    function disconnect() {
      epoch++;
      started = false;
      if (subscription && client?.connected) {
        subscription.unsubscribe();
      }
      if (notificationSubscription && client?.connected) {
        notificationSubscription.unsubscribe();
      }
      if (channelSubscription && client?.connected) {
        channelSubscription.unsubscribe();
      }
      channelSubscription = null;
      notificationSubscription = null;
      const previous = client;
      client = null;
      disconnected();
      return previous?.deactivate();
    }
    return { state, connect, disconnect };
  },
]);
