import angular from 'angular';

angular.module('wxn.messages').factory('MessageActionsService', [
  'ApiClientService',
  'ChatsService',
  'ChatStateService',
  'AccountSessionState',
  'EventBusService',
  'MessageEvents',
  function (api, chats, selected, session, bus, events) {
    const state = {
      conversationId: null,
      capabilities: [],
      writable: false,
      reply: null,
      forwarding: null,
      destinations: [],
      destinationId: null,
      destinationPage: 0,
      more: false,
      loading: false,
      loadingDestinations: false,
      notice: '',
      error: '',
      deleting: null,
      deleteIntent: null,
    };
    const key = () => crypto.randomUUID();
    function capabilities() {
      const conversation = selected.state.selected;
      if (!conversation) {
        return;
      }
      if (state.conversationId !== conversation.id) {
        state.mutating = null;
        state.reply = null;
        state.forwarding = null;
        state.deleting = null;
        state.error = '';
        state.notice = '';
        state.capabilities = [];
        state.writable = false;
        state.unavailableReason = null;
        state.connection = null;
        state.conversationId = conversation.id;
      }
      const id = conversation.id;
      return api
        .get('/conversations/' + encodeURIComponent(id) + '/message-capabilities')
        .then((result) => {
          if (state.conversationId === id) {
            state.capabilities = result.provider;
            state.connection = result.connection;
            state.writable = result.writable;
            state.unavailableReason = result.unavailableReason;
          }
        })
        .catch((error) => {
          if (state.conversationId === id) {
            state.error = error.message;
            state.writable = false;
          }
        });
    }
    function writable() {
      const c = selected.state.selected;
      const user = session.user;
      return (
        state.writable &&
        c?.id === state.conversationId &&
        c.status !== 'CLOSED' &&
        user &&
        (user.role === 'ADMIN' || (user.memberId && c.assignedMemberId === user.memberId))
      );
    }
    function supports(capability) {
      return writable() && state.capabilities.includes(capability);
    }
    function usable(message) {
      return (
        message.messageType === 'TEXT' &&
        !message.deletedAt &&
        message.externalMessageId &&
        !['FAILED', 'UNKNOWN', 'PENDING'].includes(message.status)
      );
    }
    function allowed(message, capability) {
      return (
        supports(capability) &&
        (message.capabilities || []).includes(capability) &&
        usable(message) &&
        (!message.deleteState || message.deleteState === 'FAILED') &&
        (capability !== 'DELETE_FOR_EVERYONE' || message.direction === 'OUTBOUND')
      );
    }
    function completed(message) {
      bus.emit(events.STATUS_UPDATED, { conversationId: message.conversationId, payload: message });
    }
    function loadDestinations(reset = false) {
      if (state.loadingDestinations) {
        return;
      }
      if (reset) {
        state.destinations = [];
        state.destinationPage = 0;
      }
      state.loadingDestinations = true;
      const source = selected.state.selected;
      return chats
        .listConversations(
          {
            ...(session.user?.role === 'ADMIN' ? {} : { assignedMemberId: session.user?.memberId }),
          },
          state.destinationPage,
        )
        .then((page) => {
          state.destinations.push(
            ...page.items.filter(
              (c) => c.channelId === source.channelId && !['CLOSED', 'RESOLVED'].includes(c.status),
            ),
          );
          state.more = page.hasMore;
          state.destinationPage++;
        })
        .catch((error) => {
          state.error = error.message;
        })
        .finally(() => {
          state.loadingDestinations = false;
        });
    }
    function forward(message) {
      state.forwarding = message;
      state.destinationId = null;
      state.error = '';
      state.operationId = key();
      return loadDestinations(true);
    }
    function performForward() {
      if (state.loading || !state.destinationId || !state.forwarding) {
        return;
      }
      state.loading = true;
      state.error = '';
      return api
        .post('/messages/' + state.forwarding.id + '/forward', {
          destinationConversationId: state.destinationId,
          operationId: state.operationId,
        })
        .then((message) => {
          completed(message);
          if (['FAILED', 'UNKNOWN'].includes(message.status)) {
            throw new Error('Tentativa registrada. Consulte a conversa de destino antes de repetir.');
          }
          state.forwarding = null;
          state.notice = 'Mensagem encaminhada';
        })
        .catch((error) => {
          state.error = error.message;
        })
        .finally(() => {
          state.loading = false;
        });
    }
    function remove(message, intent) {
      state.deleting = message;
      state.deleteIntent = intent;
      state.operationId = key();
      state.error = '';
    }
    function performDelete() {
      if (state.loading || !state.deleting) {
        return;
      }
      state.loading = true;
      state.error = '';
      return api
        .delete(
          '/messages/' +
            state.deleting.id +
            '?intent=' +
            state.deleteIntent +
            '&operationId=' +
            state.operationId,
        )
        .then((message) => {
          completed(message);
          if (message.deleteState !== 'CONFIRMED') {
            throw new Error(
              'Exclusão ' +
                (message.deleteState === 'UNKNOWN' ? 'sem confirmação remota' : 'não concluída') +
                '. O histórico foi preservado.',
            );
          }
          state.deleting = null;
          state.notice = 'Mensagem apagada';
        })
        .catch((error) => {
          state.error = error.message;
        })
        .finally(() => {
          state.loading = false;
        });
    }
    function mutation(message, operation, value = null) {
      state.mutating = { message, operation, value, operationId: key() };
      state.error = '';
    }
    function performMutation() {
      if (state.loading || !state.mutating) {
        return;
      }
      state.loading = true;
      const input = state.mutating;
      return api
        .post('/messages/' + input.message.id + '/operations', {
          operation: input.operation,
          operationId: input.operationId,
          value: input.value,
        })
        .then((message) => {
          completed(message);
          const result = message.pendingMutation?.state;
          state.notice =
            result === 'CONFIRMED'
              ? 'Operação confirmada.'
              : result === 'ACCEPTED'
                ? 'Aceita pelo provider. Aguardando confirmação por callback.'
                : result === 'UNKNOWN'
                  ? 'Não foi possível confirmar a operação. Não repita: acompanhe o histórico.'
                  : 'Operação não concluída. O conteúdo anterior foi preservado.';
          state.mutating = null;
        })
        .catch((error) => {
          state.error = error.userMessage || 'Não foi possível executar a operação.';
        })
        .finally(() => {
          state.loading = false;
        });
    }
    function copy(message) {
      const text = message.content?.text || '';
      let result;
      if (navigator.clipboard?.writeText) {
        result = navigator.clipboard.writeText(text);
      } else {
        const textarea = document.createElement('textarea');
        textarea.value = text;
        textarea.style.position = 'fixed';
        textarea.style.opacity = '0';
        document.body.append(textarea);
        textarea.select();
        const success = document.execCommand('copy');
        textarea.remove();
        result = success
          ? Promise.resolve()
          : Promise.reject(new Error('Seu navegador não permitiu copiar a mensagem.'));
      }
      return result
        .then(() => {
          state.notice = 'Mensagem copiada';
          state.error = '';
        })
        .catch(() => {
          state.error = 'Não foi possível copiar. Selecione o texto e copie manualmente.';
        })
        .finally(() => bus.emit('message.actions.changed', {}));
    }
    return {
      state,
      actor: () => session.user,
      capabilities,
      writable,
      supports,
      allowed,
      copy,
      edit: (message) => mutation(message, 'EDIT_TEXT', message.content?.text || ''),
      react: (message) => mutation(message, 'SEND_REACTION', '👍'),
      removeReaction: (message) => mutation(message, 'REMOVE_REACTION'),
      markRead: (message) => mutation(message, 'MARK_READ'),
      performMutation,
      forward,
      performForward,
      loadDestinations,
      remove,
      performDelete,
      reply(message) {
        state.reply = message;
      },
      reuse(message) {
        bus.emit('message.reuse', {
          conversationId: message.conversationId,
          text: message.content?.text || '',
        });
      },
    };
  },
]);
